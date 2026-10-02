package com.github.nanaki_93.content

import com.github.nanaki_93.content.CompletionExercise
import com.github.nanaki_93.content.ReadingExercise
import com.github.nanaki_93.progress.BackupCodec
import com.github.nanaki_93.progress.BackupDecodeResult
import com.github.nanaki_93.progress.CheckpointExerciseType
import com.github.nanaki_93.progress.CompactOutcome
import com.github.nanaki_93.progress.CheckpointView
import com.github.nanaki_93.progress.LessonProgress
import com.github.nanaki_93.progress.LessonStage
import com.github.nanaki_93.progress.PracticeCheckpoint
import com.github.nanaki_93.progress.ProgressAvailability
import com.github.nanaki_93.progress.ResetScope
import com.github.nanaki_93.progress.SaveCodec
import com.github.nanaki_93.progress.SaveDecodeResult
import com.github.nanaki_93.progress.SaveEnvelope
import com.github.nanaki_93.progress.SavePreferences
import com.github.nanaki_93.progress.changePreferences
import com.github.nanaki_93.progress.practiceAvailability
import com.github.nanaki_93.storage.LocalProgressOwner
import com.github.nanaki_93.storage.MemoryProgressBacking
import com.github.nanaki_93.storage.MemoryProgressStore
import com.github.nanaki_93.storage.PersistenceStatus
import com.github.nanaki_93.storage.ReplacementPreparation
import com.github.nanaki_93.storage.ReplacementResult
import com.github.nanaki_93.storage.StoreFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Final-gate integration: canonical bytes enter via a fake source; progress stays in memory. */
@OptIn(ExperimentalCoroutinesApi::class)
class TopicCatalogIntegrationTest {
    private val starterIds = listOf(
        "lesson-engineering-introduction", "lesson-clarify-understanding",
        "lesson-daily-update-blocker", "lesson-bug-reproduction", "lesson-code-review-request",
    )
    private val seedId = "lesson-confirm-meeting-time"
    private val contentRoot = "site/src/jsMain/resources/public/content"
    private fun canonical(path: String): String {
        val fs: dynamic = js("require('fs')")
        val paths: dynamic = js("require('path')")
        var root: String = js("process.cwd()") as String
        while (!(fs.existsSync(paths.resolve(root, contentRoot, "catalog.json")) as Boolean)) {
            val parent = paths.dirname(root) as String
            check(parent != root) { "Canonical content tree not found" }
            root = parent
        }
        return fs.readFileSync(paths.resolve(root, contentRoot, path), "utf8") as String
    }
    private val manifest by lazy { Json.parseToJsonElement(canonical("catalog.json")).jsonObject }
    private val entries by lazy { (manifest.getValue("entries") as JsonArray).map { it.jsonObject } }
    private fun manifestFor(selected: List<JsonObject>): String =
        JsonObject(manifest + ("entries" to JsonArray(selected))).toString()
    private fun source(
        manifestText: String = canonical("catalog.json"), overrides: Map<String, String> = emptyMap(),
        onRead: suspend (String) -> Unit = {},
    ) = object : ContentTextSource {
        override suspend fun readText(relativePath: String): String {
            onRead(relativePath)
            return overrides[relativePath] ?: if (relativePath == "catalog.json") manifestText else canonical(relativePath)
        }
    }
    private fun entry(id: String) = entries.single { it.getValue("id").jsonPrimitive.content == id }
    private fun path(id: String) = entry(id).getValue("path").jsonPrimitive.content
    private suspend fun loaded(source: ContentTextSource = source()) =
        assertIs<CatalogLoad.Ready>(BundledContentLoader(source).load()).content
    private var serial = 0
    private fun owner(store: MemoryProgressStore) = LocalProgressOwner(store, { 2_000L }, { "f06_${++serial}" })
    private fun record(id: String, stage: LessonStage = LessonStage.DIALOGUE, item: String? = null,
        updated: Long = 100, completed: Long? = null) =
        LessonProgress(id, 2, updated, stage, item, completed) // pre-version-3 learner data
    private fun save(vararg records: LessonProgress, preferences: SavePreferences = SavePreferences()) =
        SaveEnvelope(savedAtEpochMs = 100, snapshotId = "pre_f06", revision = 0,
            preferences = preferences, lessonProgress = records.toList())
    private fun view(content: BundledContent, owner: LocalProgressOwner,
        options: CatalogViewOptions = CatalogViewOptions()) =
        projectLessonCatalog(content.catalog, content.lessons, owner.state.value.snapshot, options)

    @Test fun canonicalSixObjectivesRemainSelectableAndMetadataComesFromValidatedDocuments() = runTest {
        val content = loaded()
        assertEquals(3, content.catalog.contentVersion)
        assertEquals(starterIds + seedId, content.lessons.keys.toList())
        val progress = owner(MemoryProgressStore())
        try {
            val initial = view(content, progress)
            assertEquals(starterIds + seedId, initial.cards.map { it.lessonId })
            assertEquals(6, initial.topics.sumOf { it.cards.size })
            assertEquals(listOf(
                "Introduce yourself, your engineering role, and relevant experience, then invite a colleague to share their work.",
                "Request clarification and confirm an interpretation of a teammate's request without assuming the task is to make a change.",
                "Give a concise update and explain a blocker, including the help needed and a conditional next step.",
                "Report expected versus actual behavior and reproducible steps for a test-screen bug without assuming its cause.",
                "Request review of a small code change and respond constructively to a teammate's suggestion.",
                "Politely clarify and confirm a meeting time without assuming an unclear detail.",
            ), initial.cards.map { it.communicationGoal })
            assertTrue(initial.beginnerPathCards.isEmpty())
            assertEquals(starterIds, view(content, progress, CatalogViewOptions(beginnerPath = true))
                .beginnerPathCards.map { it.lessonId })
            for (card in initial.cards) {
                val lesson = content.lessons.getValue(card.lessonId)
                assertEquals(lesson.communicationGoal, card.communicationGoal)
                assertEquals(lesson.title, card.title)
                assertEquals(lesson.situation, card.situation)
                assertEquals(lesson.durationMinutes, card.durationMinutes)
                assertEquals(lesson.prerequisiteLessonIds, card.prerequisiteLessonIds)
                assertNull(card.status.record)
                assertFalse(card.status.isCompleted)
            }
            val before = progress.state.value.snapshot
            val filtered = view(content, progress, CatalogViewOptions("workplace-clarification", true))
            assertEquals(listOf(starterIds[1], seedId), filtered.cards.map { it.lessonId })
            assertEquals((starterIds + seedId).toSet(), filtered.topics.flatMap { it.cards }.map { it.lessonId }.toSet())
            val invalid = view(content, progress, CatalogViewOptions("unknown-topic"))
            assertTrue(invalid.invalidTopicFilter)
            assertTrue(invalid.cards.isEmpty())
            assertEquals(initial.recommendation, invalid.recommendation)
            assertEquals(initial.cards, view(content, progress).cards) // clear filter
            assertEquals(before, progress.state.value.snapshot) // no visits/review writes
            assertNull(progress.savedBaseline)
        } finally { progress.dispose() }
    }

    @Test fun manifestKindsAndLaterInvalidDocumentsNeverPublishPartialContent() = runTest {
        val lessonOnly = loaded(source(manifestFor(entries.filter { it.getValue("kind").jsonPrimitive.content == "lesson" })))
        assertEquals(6, lessonOnly.lessons.size)
        assertTrue(lessonOnly.practiceSets.isEmpty())
        val practiceOnly = assertIs<CatalogLoad.Ready>(BundledContentLoader(source(
            manifestFor(entries.filter { it.getValue("kind").jsonPrimitive.content == "practice" }))).load()).content
        assertTrue(practiceOnly.lessons.isEmpty())
        assertTrue(practiceOnly.practiceSets.isNotEmpty())
        val emptyManifest = manifestFor(emptyList())
        assertEquals(EmptyContentReason.EMPTY_CATALOG,
            assertIs<CatalogLoad.Empty>(BundledContentLoader(source(emptyManifest)).load()).reason)
        for ((text, reason) in listOf(
            emptyManifest to CatalogEmptyReason.EMPTY_CATALOG,
            manifestFor(entries.filter { it.getValue("kind").jsonPrimitive.content == "practice" }) to
                CatalogEmptyReason.NO_WORKPLACE_LESSONS,
        )) {
            val noLessons = LocalCatalogCoordinator(this, BundledContentLoader(source(text)))
            try {
                noLessons.load(); runCurrent()
                assertEquals(LocalCatalogState.Empty(reason), noLessons.state.value)
            } finally { noLessons.dispose() }
        }
        val mixed = loaded()
        assertTrue(mixed.practiceSets.isNotEmpty() && mixed.lessons.size == 6)
        val last = entries.last().getValue("path").jsonPrimitive.content
        val broken = source(overrides = mapOf(last to "<html>not a document</html>"))
        val coordinator = LocalCatalogCoordinator(this, BundledContentLoader(broken))
        try {
            coordinator.load(); runCurrent()
            assertIs<LocalCatalogState.Error>(coordinator.state.value)
        } finally { coordinator.dispose() }
        val malformedManifest = LocalCatalogCoordinator(this, BundledContentLoader(source("{broken")))
        try {
            malformedManifest.load(); runCurrent()
            assertIs<LocalCatalogState.Error>(malformedManifest.state.value)
        } finally { malformedManifest.dispose() }
        val wrongVersion = canonical(path(seedId)).replace("\"contentVersion\": 3", "\"contentVersion\": 2")
        val invalid = source(overrides = mapOf(path(seedId) to wrongVersion))
        assertEquals(path(seedId), assertIs<BundledContentException>(runCatching {
            BundledContentLoader(invalid).load()
        }.exceptionOrNull()).affectedPath)
    }

    @Test fun savedStagesAndRecommendationsUseStableIdsNotVersionOrPracticeScores() = runTest {
        val content = loaded()
        val intro = content.lessons.getValue(starterIds[0])
        val seed = content.lessons.getValue(seedId)
        val turn = intro.dialogue.turns.first().id
        val exercise = intro.exercises.first().id
        val guided = intro.exercises.first { it is ReadingExercise || it is CompletionExercise }.id
        val production = intro.exercises.filterIsInstance<ProductionExercise>().first().id
        val graphNode = seed.conversationGraph!!.nodes.first().id
        val backing = MemoryProgressBacking()
        val progress = owner(MemoryProgressStore(backing))
        try {
            val cases = listOf(
                Triple(LessonStage.DIALOGUE, turn, true), Triple(LessonStage.DIALOGUE, exercise, false),
                Triple(LessonStage.UNDERSTANDING, exercise, true), Triple(LessonStage.GUIDED_PRACTICE, exercise, false),
                Triple(LessonStage.GUIDED_PRACTICE, guided, true), Triple(LessonStage.UNDERSTANDING, guided, false),
                Triple(LessonStage.UNDERSTANDING, turn, false), Triple(LessonStage.ROLE_PLAY, production, true),
                Triple(LessonStage.ROLE_PLAY, exercise, false), Triple(LessonStage.SITUATION, turn, false),
                Triple(LessonStage.SUMMARY, turn, false),
            )
            for ((stage, item, expected) in cases) {
                backing.externalChange(SaveCodec.encodeSave(save(record(starterIds[0], stage, item))))
                assertTrue(progress.reloadSavedState())
                val status = view(content, progress).cards.first().status
                assertEquals(expected, status.checkpointAvailable, "$stage / $item")
                assertEquals(stage, status.savedStage)
                assertFalse(status.isCompleted)
                assertEquals(2, status.record!!.contentVersion)
            }
            for (stage in LessonStage.entries) {
                backing.externalChange(SaveCodec.encodeSave(save(record(starterIds[0], stage))))
                progress.reloadSavedState()
                assertEquals(true, view(content, progress).cards.first().status.checkpointAvailable, "$stage boundary")
            }
            val saved = save(
                record(starterIds[0], LessonStage.DIALOGUE, turn, updated = 500),
                record(starterIds[1], LessonStage.DIALOGUE, "removed-turn", updated = 900),
                record(seedId, LessonStage.ROLE_PLAY, graphNode, updated = 500),
                record("lesson-retired", item = "gone", updated = 1000, completed = 800),
            )
            backing.externalChange(SaveCodec.encodeSave(saved)); progress.reloadSavedState()
            val projected = view(content, progress)
            assertEquals(starterIds[0], projected.recommendation?.lessonId) // tie: canonical order
            assertTrue(projected.recommendation!!.reason.contains("browsing does not change progress"))
            assertEquals(false, projected.cards[1].status.checkpointAvailable)
            assertEquals(listOf("lesson-retired"), projected.unavailableSavedLessons.map { it.record?.lessonId })
            assertFalse(projected.unavailableSavedLessons.single().documentAvailable)
            assertTrue(projected.unavailableSavedLessons.single().isCompleted)
            assertEquals(saved.lessonProgress, progress.state.value.snapshot.lessonProgress)
            assertEquals(SaveCodec.encodeSave(saved), backing.raw)
            val unavailableContent = content.copy(lessons = content.lessons - seedId)
            assertEquals(seedId, view(unavailableContent, progress).unavailableSavedLessons
                .first { it.record?.lessonId == seedId }.record?.lessonId)
            assertEquals(saved.lessonProgress, progress.state.value.snapshot.lessonProgress)
            assertEquals(starterIds[0], view(content, progress, CatalogViewOptions("unknown-topic"))
                .recommendation?.lessonId)
            val unaided = SavePreferences(showReadings = false, showTranslation = false, showRomaji = false)
            backing.externalChange(SaveCodec.encodeSave(save(record(starterIds[0], item = "gone"),
                preferences = unaided))); progress.reloadSavedState()
            assertEquals(starterIds[0], view(content, progress).recommendation?.lessonId)
            assertTrue(view(content, progress).recommendation!!.reason.contains("catalog order"))
            for (aids in listOf(
                SavePreferences(showReadings = true, showTranslation = false, showRomaji = false),
                SavePreferences(showReadings = false, showTranslation = true, showRomaji = false),
                SavePreferences(showReadings = false, showTranslation = false, showRomaji = true),
            )) {
                backing.externalChange(SaveCodec.encodeSave(save(record(starterIds[0], item = "gone"),
                    preferences = aids))); progress.reloadSavedState()
                assertEquals(starterIds[0], view(content, progress).recommendation?.lessonId)
                assertTrue(view(content, progress).recommendation!!.reason.contains("support is enabled"))
            }
            val practice = PracticeCheckpoint("practice-kana-a-i", 2, 100, "run", "transition",
                listOf("exercise-kana-a-choice"), listOf(CompactOutcome.CORRECT), 1,
                CheckpointView.COMPLETE, lastCompletedAtEpochMs = 100,
                exerciseTypes = listOf(CheckpointExerciseType.CHOICE))
            val unscored = view(content, progress).recommendation
            backing.externalChange(SaveCodec.encodeSave(progress.state.value.snapshot.copy(
                practiceProgress = listOf(practice))))
            progress.reloadSavedState()
            assertEquals(unscored, view(content, progress).recommendation)
            backing.externalChange(SaveCodec.encodeSave(save(*(starterIds + seedId).map {
                record(it, LessonStage.SUMMARY, completed = 700)
            }.toTypedArray()))); progress.reloadSavedState()
            assertTrue(view(content, progress).recommendation!!.revisit)
            assertEquals(starterIds[0], view(content, progress).recommendation?.lessonId)
        } finally { progress.dispose() }
    }

    @Test fun backupResetRestoreReloadAndOldPracticeCheckpointPreserveProgress() = runTest {
        val content = loaded()
        val set = content.practiceSets.getValue("practice-kana-a-i")
        val oldPractice = PracticeCheckpoint(set.id, 2, 100, "old-run", "old-transition",
            listOf(set.exercises.first().id), emptyList(), 0, CheckpointView.PROMPT,
            exerciseTypes = listOf(CheckpointExerciseType.CHOICE))
        val original = save(record(seedId, LessonStage.DIALOGUE, "turn-time"),
            record("lesson-retired", item = "lost"),
            preferences = SavePreferences(showReadings = false, showRomaji = true))
            .copy(practiceProgress = listOf(oldPractice))
        val backing = MemoryProgressBacking(SaveCodec.encodeSave(original))
        val progress = owner(MemoryProgressStore(backing))
        try {
            assertEquals(ProgressAvailability.AVAILABLE, practiceAvailability(oldPractice,
                mapOf(set.id to set.exercises.map { it.id }.toSet())))
            assertEquals(true, view(content, progress).cards.last().status.checkpointAvailable)
            val backup = BackupCodec.encodeBackup(progress.state.value.snapshot, "1.0", 200)
            val candidate = assertIs<BackupDecodeResult.Valid>(BackupCodec.decodeBackup(backup)).backup
            assertEquals(original, candidate.snapshot)
            val reset = assertIs<ReplacementPreparation.Ready>(progress.beginReset(ResetScope.FULL_LEARNER_STATE)).token
            assertEquals(ReplacementResult.Replaced, progress.confirmReplacement(reset))
            assertTrue(view(content, progress).cards.none { it.status.record != null })
            assertTrue(progress.state.value.snapshot.practiceProgress.isEmpty())
            val restore = assertIs<ReplacementPreparation.Ready>(progress.beginRestore(candidate)).token
            assertEquals(ReplacementResult.Replaced, progress.confirmReplacement(restore))
            assertEquals(original.lessonProgress, progress.state.value.snapshot.lessonProgress)
            assertEquals(listOf(oldPractice), progress.state.value.snapshot.practiceProgress)
            assertEquals(original.preferences, progress.state.value.snapshot.preferences)
            assertNotEquals(original.snapshotId, progress.state.value.snapshot.snapshotId)
            assertEquals(listOf("lesson-retired"), view(content, progress).unavailableSavedLessons.map { it.record?.lessonId })
            val saved = backing.raw
            assertTrue(progress.reloadSavedState())
            assertEquals(true, view(content, progress).cards.last().status.checkpointAvailable)
            assertEquals(ProgressAvailability.AVAILABLE, practiceAvailability(
                progress.state.value.snapshot.practiceProgress.single(), mapOf(set.id to set.exercises.map { it.id }.toSet())))
            assertEquals(saved, backing.raw)
            assertEquals(progress.state.value.snapshot,
                assertIs<SaveDecodeResult.Valid>(SaveCodec.decodeSave(saved!!)).snapshot)
        } finally { progress.dispose() }
    }

    @Test fun browsingDoesNotWriteAndExplicitPreferenceFailuresKeepContentAvailable() = runTest {
        val content = loaded()
        val backing = MemoryProgressBacking()
        val store = MemoryProgressStore(backing).apply { writeFailure = StoreFailure.QUOTA }
        val progress = owner(store)
        try {
            repeat(2) { view(content, progress, CatalogViewOptions("workplace-reviews", it == 0)) }
            assertNull(backing.raw)
            progress.mutate { changePreferences(it, it.preferences.copy(showTranslation = false)) }
            assertIs<PersistenceStatus.MemoryOnly>(progress.state.value.status)
            assertNull(backing.raw)
            assertFalse(progress.state.value.snapshot.preferences.showTranslation)
            assertEquals(6, view(content, progress).cards.size)
        } finally { progress.dispose() }

        val protectedBacking = MemoryProgressBacking("invalid original")
        val protected = owner(MemoryProgressStore(protectedBacking))
        try {
            protected.mutate { changePreferences(it, it.preferences.copy(showRomaji = true)) }
            assertIs<PersistenceStatus.Protected>(protected.state.value.status)
            assertEquals("invalid original", protectedBacking.raw)
            assertEquals(6, view(content, protected).cards.size)
            assertEquals(ReplacementPreparation.Blocked, protected.beginReset(ResetScope.PROGRESS_ONLY))
        } finally { protected.dispose() }

        val shared = MemoryProgressBacking()
        val stale = owner(MemoryProgressStore(shared))
        val other = owner(MemoryProgressStore(shared))
        try {
            other.mutate { changePreferences(it, it.preferences.copy(showRomaji = true)) }
            assertIs<PersistenceStatus.Conflict>(stale.state.value.status)
            val stored = shared.raw
            stale.mutate { changePreferences(it, it.preferences.copy(showReadings = false)) }
            assertEquals(stored, shared.raw)
            assertEquals(6, view(content, stale).cards.size)
            assertEquals(ReplacementPreparation.Blocked, stale.beginReset(ResetScope.FULL_LEARNER_STATE))
            assertTrue(stale.reloadSavedState())
            assertTrue(stale.state.value.snapshot.preferences.showRomaji)
            assertEquals(stored, shared.raw)
        } finally { stale.dispose(); other.dispose() }
    }

    @Test fun canonicalFakeCancellationTimeoutRetryAndDisposalNeverPublishStaleContent() = runTest {
        val hanging = source(onRead = { if (it == "catalog.json") awaitCancellation() })
        val pending = async { BundledContentLoader(hanging).load() }
        runCurrent()
        pending.cancel()
        assertIs<CancellationException>(runCatching { pending.await() }.exceptionOrNull())

        val slow = source(onRead = { if (it == "catalog.json") delay(10_001) })
        val timed = async { runCatching { BundledContentLoader(slow).load() }.exceptionOrNull() }
        advanceTimeBy(10_000); runCurrent()
        assertEquals("catalog.json", assertIs<BundledContentException>(timed.await()).affectedPath)

        val ready = CatalogLoad.Ready(loaded())
        val late = CompletableDeferred<CatalogLoad>()
        var calls = 0
        val coordinator = LocalCatalogCoordinator(this) {
            if (++calls == 1) withContext(NonCancellable) { late.await() } else ready
        }
        coordinator.load(); runCurrent()
        coordinator.retryLoad(); runCurrent()
        assertEquals(6, assertIs<LocalCatalogState.Ready>(coordinator.state.value).content.lessons.size)
        late.complete(CatalogLoad.Empty(EmptyContentReason.EMPTY_CATALOG)); runCurrent()
        assertIs<LocalCatalogState.Ready>(coordinator.state.value)
        coordinator.dispose()
        assertEquals(2, calls)

        val afterDispose = CompletableDeferred<CatalogLoad>()
        val disposed = LocalCatalogCoordinator(this) { withContext(NonCancellable) { afterDispose.await() } }
        disposed.load(); runCurrent()
        disposed.dispose()
        afterDispose.complete(ready); runCurrent()
        assertIs<LocalCatalogState.Loading>(disposed.state.value)
        disposed.retryLoad(); runCurrent()
        assertIs<LocalCatalogState.Loading>(disposed.state.value)
    }
}
