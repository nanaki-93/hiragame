package com.github.nanaki_93.practice

import com.github.nanaki_93.content.BundledContentLoader
import com.github.nanaki_93.content.CatalogLoad
import com.github.nanaki_93.content.ContentTextSource
import com.github.nanaki_93.content.ReadingExercise
import com.github.nanaki_93.content.KanaReadingAnswer
import com.github.nanaki_93.progress.BackupCodec
import com.github.nanaki_93.progress.BackupDecodeResult
import com.github.nanaki_93.progress.CheckpointView
import com.github.nanaki_93.progress.ResetScope
import com.github.nanaki_93.progress.SaveCodec
import com.github.nanaki_93.progress.SaveDecodeResult
import com.github.nanaki_93.progress.changePreferences
import com.github.nanaki_93.storage.LocalProgressOwner
import com.github.nanaki_93.storage.MemoryProgressBacking
import com.github.nanaki_93.storage.MemoryProgressStore
import com.github.nanaki_93.storage.PersistenceStatus
import com.github.nanaki_93.storage.ReplacementPreparation
import com.github.nanaki_93.storage.ReplacementResult
import com.github.nanaki_93.storage.StoreFailure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Final-gate scenarios: only in-memory adapters and the checked-in canonical JSON; no HTTP or browser. */
@OptIn(ExperimentalCoroutinesApi::class)
class FoundationalPracticeIntegrationTest {
    private val source = object : ContentTextSource {
        override suspend fun readText(relativePath: String): String {
            val fs: dynamic = js("require('fs')")
            val path: dynamic = js("require('path')")
            var root: String = js("process.cwd()") as String
            val contentRoot = "site/src/jsMain/resources/public/content"
            while (!(fs.existsSync(path.resolve(root, contentRoot, "catalog.json")) as Boolean)) {
                val parent = path.dirname(root) as String
                check(parent != root) { "Canonical content tree not found" }
                root = parent
            }
            // Loader supplies only manifest paths. Never fetch or serve the content over HTTP.
            return fs.readFileSync(path.resolve(root, contentRoot, relativePath), "utf8") as String
        }
    }
    private var sequence = 0
    private fun owner(store: MemoryProgressStore = MemoryProgressStore()) =
        LocalProgressOwner(store, { 1_000L }, { "f05_snapshot_${++sequence}" })
    private fun coordinator(scope: kotlinx.coroutines.CoroutineScope, progress: LocalProgressOwner,
        load: suspend () -> CatalogLoad = BundledContentLoader(source)::load,
    ) = LocalPracticeCoordinator(scope, progress, load, now = { 1_000L })
    private fun LocalPracticeCoordinator.session() = assertIs<LocalPracticeState.Ready>(state.value).session!!
    private fun LocalPracticeCoordinator.send(command: (PracticeSession) -> PracticeCommand) = dispatch(command(session()))
    private fun LocalPracticeCoordinator.leave() = send { PracticeCommand.Leave(it.id, it.revision) }

    @Test fun everyCanonicalMicroSetIsSelectableAndEveryAuthoredExerciseFitsTheCheckpoint() = runTest {
        val canonical = assertIs<CatalogLoad.Ready>(BundledContentLoader(source).load())
        val sets = canonical.content.practiceSets
        assertEquals(4, canonical.content.catalog.contentVersion) // pre-bump seed checkpoints still resolve by stable ID
        assertTrue(sets.size > 3)
        assertTrue(sets.keys.any { it.startsWith("practice-hiragana-") })
        assertTrue(sets.keys.any { it.startsWith("practice-katakana-") })
        assertTrue(sets.keys.any { it.startsWith("practice-vocabulary-") })
        for ((id, set) in sets) {
            // Independent empty saves: accumulating dozens of checkpoints would exceed the save bound.
            val progress = owner()
            val practice = coordinator(this, progress) { canonical }
            try {
                practice.load(); runCurrent()
                assertTrue(id in assertIs<LocalPracticeState.Ready>(practice.state.value).availablePracticeSets)
                practice.start(id)
                val session = practice.session()
                assertEquals(id, session.setId)
                assertTrue(set.exercises.size in 1..10, id)
                assertEquals(set.exercises.map { it.id }, session.plan.map { it.id }, id)
                assertEquals(set.exercises.map { it.id }, progress.state.value.snapshot.practiceProgress.single().exerciseIds, id)
                assertEquals(CheckpointView.PROMPT, progress.state.value.snapshot.practiceProgress.single().view)
                practice.leave()
            } finally {
                practice.dispose(); progress.dispose()
            }
        }
    }

    @Test fun canonicalKanaVariantsFeedbackRetryRevealSkipAndCompletionRequireExplicitActions() = runTest {
        val progress = owner()
        val practice = coordinator(this, progress)
        try {
            practice.load(); runCurrent()
            practice.start("practice-vocabulary-daily-reading")
            val initial = practice.session()
            assertEquals("exercise-vocab-daily-water", initial.plan.first().id)
            val duplicate = PracticeCommand.Submit(initial.id, initial.revision, PracticeAnswer.Text("not a reading"))
            practice.dispatch(duplicate)
            val incorrect = practice.session()
            assertEquals(SessionView.Feedback(0), incorrect.view)
            assertIs<PracticeOutcome.Incorrect>(incorrect.outcomes[0])
            practice.dispatch(duplicate)
            assertSame(incorrect, practice.session())
            practice.send { PracticeCommand.Retry(it.id, it.revision) }
            assertEquals(SessionView.Prompt(0), practice.session().view)
            assertEquals(0, practice.session().counts.completed)
            practice.send { PracticeCommand.Submit(it.id, it.revision, PracticeAnswer.Text(" みず ")) }
            assertIs<PracticeOutcome.Correct>(practice.session().outcomes[0])
            assertEquals(SessionView.Feedback(0), practice.session().view)
            practice.send { PracticeCommand.Continue(it.id, it.revision) }
            val second = practice.session()
            practice.dispatch(PracticeCommand.Reveal(second.id, second.revision))
            assertEquals(SessionView.Feedback(1), practice.session().view)
            assertIs<PracticeOutcome.Revealed>(practice.session().outcomes[1])
            assertEquals(1, practice.session().counts.correct)
            practice.send { PracticeCommand.Continue(it.id, it.revision) }
            val third = practice.session()
            practice.dispatch(PracticeCommand.Skip(third.id, third.revision))
            assertIs<PracticeOutcome.Skipped>(practice.session().outcomes[2])
            assertEquals(1, practice.session().counts.correct)
            for (index in 3..5) {
                practice.send { PracticeCommand.Continue(it.id, it.revision) }
                assertEquals(SessionView.Prompt(index), practice.session().view)
                val exercise = assertIs<ReadingExercise>(practice.session().plan[index].exercise)
                val authored = assertIs<KanaReadingAnswer>(exercise.acceptedAnswers.first()).text.surface
                val response = if (index == 5) " あす " else authored
                practice.send { PracticeCommand.Submit(it.id, it.revision, PracticeAnswer.Text(response)) }
                assertIs<PracticeOutcome.Correct>(practice.session().outcomes[index])
                assertEquals(SessionView.Feedback(index), practice.session().view)
            }
            // 明日 explicitly authors あす as an alternative; arbitrary paraphrases are not inferred.
            val tomorrow = assertIs<ReadingExercise>(practice.session().plan[5].exercise)
            assertEquals(true, assertIs<EvaluationResult.Objective>(evaluate(tomorrow, PracticeAnswer.Text("あした"))).correct)
            assertEquals(false, assertIs<EvaluationResult.Objective>(evaluate(tomorrow, PracticeAnswer.Text("あさって"))).correct)
            val feedback = practice.session()
            val last = PracticeCommand.Continue(feedback.id, feedback.revision)
            practice.dispatch(last)
            practice.dispatch(last)
            assertEquals(SessionView.Complete, practice.session().view)
            assertEquals(6, practice.session().counts.completed)
            assertEquals(4, practice.session().counts.correct)
            assertEquals(CheckpointView.COMPLETE, progress.state.value.snapshot.practiceProgress.single().view)
            practice.leave()
            practice.start("practice-katakana-basic-vowels-k-reading")
            assertEquals("exercise-kb-reading-30a2", practice.session().plan.first().id)
            practice.send { PracticeCommand.Submit(it.id, it.revision, PracticeAnswer.Text(" ア ")) }
            assertIs<PracticeOutcome.Correct>(practice.session().outcomes.first())
            practice.leave()

            // Real canonical voiced kana must also accept canonical-equivalent decomposed marks.
            val voiced = assertIs<CatalogLoad.Ready>(BundledContentLoader(source).load()).content.practiceSets.values
                .flatMap { it.exercises }.filterIsInstance<ReadingExercise>().first { exercise ->
                    exercise.acceptedAnswers.filterIsInstance<KanaReadingAnswer>().any { it.text.surface == "が" }
                }
            assertEquals(true, assertIs<EvaluationResult.Objective>(evaluate(voiced, PracticeAnswer.Text("か\u3099"))).correct)
            assertEquals(false, assertIs<EvaluationResult.Objective>(evaluate(voiced, PracticeAnswer.Text("か"))).correct)
        } finally {
            practice.dispose(); progress.dispose()
        }
    }

    @Test fun preferencesSerializedResumeOldSeedAndBackupResetRestoreRetainValidProgress() = runTest {
        val backing = MemoryProgressBacking()
        val firstOwner = owner(MemoryProgressStore(backing))
        val first = coordinator(this, firstOwner)
        first.load(); runCurrent()
        try {
            first.start("practice-kana-a-i") // pre-F05 stable IDs and romaji answer
            val oldSeed = first.session()
            assertEquals("exercise-kana-a-choice", oldSeed.plan.first().id)
            first.send { PracticeCommand.Submit(it.id, it.revision, PracticeAnswer.Choice("option-kana-a")) }
            val stale = PracticeCommand.Continue(oldSeed.id, oldSeed.revision)
            first.leave()
            first.start("practice-vocabulary-daily-reading")
            val active = first.session()
            val checkpoint = firstOwner.state.value.snapshot.practiceProgress
            firstOwner.mutate { changePreferences(it, it.preferences.copy(
                showReadings = false, showTranslation = false, showRomaji = true)) }
            assertSame(active, first.session())
            assertEquals(checkpoint, firstOwner.state.value.snapshot.practiceProgress)
            assertEquals(SessionView.Prompt(0), first.session().view)
            val wire = backing.raw!!
            assertEquals(firstOwner.state.value.snapshot,
                assertIs<SaveDecodeResult.Valid>(SaveCodec.decodeSave(wire)).snapshot)
            first.dispose(); firstOwner.dispose()

            val reopened = owner(MemoryProgressStore(backing))
            val second = coordinator(this, reopened)
            try {
                second.load(); runCurrent()
                assertEquals(2, reopened.state.value.snapshot.practiceProgress.size)
                assertIs<PracticeCheckpointResolution.Available>(second.savedCheckpoints()["practice-kana-a-i"])
                assertIs<PracticeCheckpointResolution.Available>(second.savedCheckpoints()["practice-vocabulary-daily-reading"])
                assertTrue(reopened.state.value.snapshot.preferences.showRomaji)
                second.resume("practice-kana-a-i")
                assertEquals(SessionView.Feedback(0), second.session().view)
                assertEquals(1, second.session().counts.correct)
                second.dispatch(stale)
                assertEquals(SessionView.Feedback(0), second.session().view)
                assertEquals(wire, backing.raw) // resume only reads
                second.send { PracticeCommand.Continue(it.id, it.revision) }
                second.send { PracticeCommand.Submit(it.id, it.revision, PracticeAnswer.Text(" i ")) }
                assertEquals(2, second.session().counts.correct)
                second.leave()
                second.resume("practice-vocabulary-daily-reading")
                assertEquals(SessionView.Prompt(0), second.session().view)
                second.leave()

                val original = reopened.state.value.snapshot
                val backup = BackupCodec.encodeBackup(original, "1.0", 1_000L)
                val candidate = assertIs<BackupDecodeResult.Valid>(BackupCodec.decodeBackup(backup)).backup
                val reset = assertIs<ReplacementPreparation.Ready>(reopened.beginReset(ResetScope.FULL_LEARNER_STATE)).token
                assertEquals(ReplacementResult.Replaced, reopened.confirmReplacement(reset))
                assertTrue(reopened.state.value.snapshot.practiceProgress.isEmpty())
                assertTrue(!reopened.state.value.snapshot.preferences.showRomaji)
                val restore = assertIs<ReplacementPreparation.Ready>(reopened.beginRestore(candidate)).token
                assertEquals(ReplacementResult.Replaced, reopened.confirmReplacement(restore))
                assertEquals(original.practiceProgress, reopened.state.value.snapshot.practiceProgress)
                assertEquals(original.preferences, reopened.state.value.snapshot.preferences)
                assertNotEquals(original.snapshotId, reopened.state.value.snapshot.snapshotId)
                assertEquals(reopened.state.value.snapshot,
                    assertIs<SaveDecodeResult.Valid>(SaveCodec.decodeSave(backing.raw!!)).snapshot)
                assertIs<PracticeCheckpointResolution.Available>(second.savedCheckpoints()["practice-kana-a-i"])
                assertIs<PracticeCheckpointResolution.Available>(second.savedCheckpoints()["practice-vocabulary-daily-reading"])
                second.resume("practice-vocabulary-daily-reading")
                assertEquals(SessionView.Prompt(0), second.session().view)
            } finally {
                second.dispose(); reopened.dispose()
            }
        } finally {
            first.dispose(); firstOwner.dispose()
        }
    }

    @Test fun failuresConflictsProtectedOriginalUnavailableContentAndGenerationKeepRecoveryExplicit() = runTest {
        val canonical = assertIs<CatalogLoad.Ready>(BundledContentLoader(source).load())
        val backing = MemoryProgressBacking()
        val store = MemoryProgressStore(backing).apply { writeFailure = StoreFailure.QUOTA }
        val progress = owner(store)
        val practice = coordinator(this, progress) { canonical }
        try {
            practice.load(); runCurrent()
            practice.start("practice-vocabulary-daily-reading")
            val old = practice.session()
            assertIs<PersistenceStatus.MemoryOnly>(progress.state.value.status)
            assertNull(backing.raw)
            practice.send { PracticeCommand.Skip(it.id, it.revision) }
            assertEquals(1, practice.session().counts.skipped)
            store.writeFailure = null
            progress.retrySaving()
            assertIs<PersistenceStatus.Saved>(progress.state.value.status)
            assertEquals(CheckpointView.FEEDBACK,
                assertIs<SaveDecodeResult.Valid>(SaveCodec.decodeSave(backing.raw!!)).snapshot.practiceProgress.single().view)
            practice.leave()
            val saved = backing.raw
            val unavailable = coordinator(this, progress) {
                CatalogLoad.Ready(canonical.content.copy(practiceSets = canonical.content.practiceSets - "practice-vocabulary-daily-reading"))
            }
            try {
                unavailable.load(); runCurrent()
                assertTrue(assertIs<PracticeCheckpointResolution.Unavailable>(
                    unavailable.savedCheckpoints()["practice-vocabulary-daily-reading"]).missingSet)
                unavailable.resume("practice-vocabulary-daily-reading")
                assertNull(assertIs<LocalPracticeState.Ready>(unavailable.state.value).session)
            } finally { unavailable.dispose() }
            assertEquals(saved, backing.raw)
            val oldSkip = PracticeCommand.Skip(old.id, old.revision)
            assertTrue(progress.reloadSavedState())
            assertNull(assertIs<LocalPracticeState.Ready>(practice.state.value).session)
            practice.dispatch(oldSkip)
            assertNull(assertIs<LocalPracticeState.Ready>(practice.state.value).session)
            assertIs<PracticeCheckpointResolution.Available>(practice.savedCheckpoints()["practice-vocabulary-daily-reading"])
        } finally { practice.dispose(); progress.dispose() }

        val protectedBacking = MemoryProgressBacking("unreadable original")
        val protected = owner(MemoryProgressStore(protectedBacking))
        val protectedPractice = coordinator(this, protected) { canonical }
        try {
            protectedPractice.load(); runCurrent(); protectedPractice.start("practice-kana-a-i")
            assertIs<PersistenceStatus.Protected>(protected.state.value.status)
            assertEquals("unreadable original", protectedBacking.raw)
            assertEquals(1, protected.state.value.snapshot.practiceProgress.size)
            assertEquals(ReplacementPreparation.Blocked, protected.beginReset(ResetScope.PROGRESS_ONLY))
        } finally { protectedPractice.dispose(); protected.dispose() }

        val shared = MemoryProgressBacking()
        val stale = owner(MemoryProgressStore(shared))
        val stalePractice = coordinator(this, stale) { canonical }
        try {
            stalePractice.load(); runCurrent(); stalePractice.start("practice-kana-a-i")
            val oldRaw = shared.raw
            val other = owner(MemoryProgressStore(shared))
            try {
                other.mutate { changePreferences(it, it.preferences.copy(showRomaji = true)) }
                assertIs<PersistenceStatus.Conflict>(stale.state.value.status)
                stalePractice.send { PracticeCommand.Skip(it.id, it.revision) }
                assertEquals(1, stalePractice.session().counts.skipped) // memory usable, original not overwritten
                assertNotEquals(oldRaw, shared.raw)
                assertEquals(true, assertIs<SaveDecodeResult.Valid>(SaveCodec.decodeSave(shared.raw!!)).snapshot.preferences.showRomaji)
                assertEquals(CheckpointView.PROMPT,
                    assertIs<SaveDecodeResult.Valid>(SaveCodec.decodeSave(shared.raw!!)).snapshot.practiceProgress.single().view)
                assertEquals(ReplacementPreparation.Blocked, stale.beginReset(ResetScope.FULL_LEARNER_STATE))
                assertTrue(stale.reloadSavedState())
                assertNull(assertIs<LocalPracticeState.Ready>(stalePractice.state.value).session)
                assertIs<PracticeCheckpointResolution.Available>(stalePractice.savedCheckpoints()["practice-kana-a-i"])
            } finally { other.dispose() }
        } finally { stalePractice.dispose(); stale.dispose() }
    }
}
