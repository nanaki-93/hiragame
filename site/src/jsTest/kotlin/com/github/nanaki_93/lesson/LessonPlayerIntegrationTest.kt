package com.github.nanaki_93.lesson

import com.github.nanaki_93.content.*
import com.github.nanaki_93.practice.Assessment
import com.github.nanaki_93.practice.PracticeAnswer
import com.github.nanaki_93.progress.*
import com.github.nanaki_93.storage.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

/** Final-gate scenarios: canonical bytes via an injected source, deterministic clock, fake store; no browser. */
@OptIn(ExperimentalCoroutinesApi::class)
class LessonPlayerIntegrationTest {
    private val root = "site/src/jsMain/resources/public/content"
    private fun canonical(relative: String): String {
        val fs: dynamic = js("require('fs')")
        val path: dynamic = js("require('path')")
        var dir: String = js("process.cwd()") as String
        while (!(fs.existsSync(path.resolve(dir, root, "catalog.json")) as Boolean)) {
            val parent = path.dirname(dir) as String
            check(parent != dir) { "Canonical lesson files missing" }
            dir = parent
        }
        return fs.readFileSync(path.resolve(dir, root, relative), "utf8") as String
    }
    private val manifest by lazy { Json.parseToJsonElement(canonical("catalog.json")).jsonObject }
    private val lessonEntries by lazy { (manifest.getValue("entries") as JsonArray).map { it.jsonObject }
        .filter { it.getValue("kind").jsonPrimitive.content == "lesson" } }
    private fun source(overrides: Map<String, String> = emptyMap()) = object : ContentTextSource {
        val reads = mutableListOf<String>()
        override suspend fun readText(relativePath: String): String {
            reads += relativePath
            return overrides[relativePath] ?: canonical(relativePath)
        }
    }
    private var serial = 0
    private fun owner(backing: MemoryProgressBacking = MemoryProgressBacking()) =
        LocalProgressOwner(MemoryProgressStore(backing), { 2_000L }, { "f07_${++serial}" })
    private fun active(coordinator: LocalLessonCoordinator) = assertIs<LocalLessonState.Active>(coordinator.state.value)
    private fun dispatch(coordinator: LocalLessonCoordinator, command: (Long, Long) -> LessonCommand) {
        val session = active(coordinator).session
        coordinator.dispatch(command(session.id, session.revision))
    }
    private fun next(c: LocalLessonCoordinator) = dispatch(c) { id, rev -> LessonCommand.Next(id, rev) }
    private fun submit(c: LocalLessonCoordinator, answer: PracticeAnswer) =
        dispatch(c) { id, rev -> LessonCommand.Submit(id, rev, answer) }
    private fun continueItem(c: LocalLessonCoordinator) = dispatch(c) { id, rev -> LessonCommand.Continue(id, rev) }
    private fun answer(item: LessonPlanItem.Prompt): PracticeAnswer = when (val ex = item.exercise) {
        is ChoiceExercise -> PracticeAnswer.Choice(ex.correctOptionId)
        is ReadingExercise -> PracticeAnswer.Text(when (val expected = ex.acceptedAnswers.first()) {
            is KanaReadingAnswer -> expected.text.surface
            is RomajiReadingAnswer -> expected.text
        })
        is CompletionExercise -> PracticeAnswer.Text(ex.acceptedAnswers.first().surface)
        is ProductionExercise -> PracticeAnswer.SelfAssessment("自分の言葉で伝えます。", Assessment.MET_CRITERIA)
    }

    @Test fun allSixCanonicalLessonsPlayInAuthoredOrderAndFinishOnlyOnCommand() = runTest {
        val injected = source()
        val loader = BundledContentLoader(injected)
        val bundle = assertIs<CatalogLoad.Ready>(loader.load()).content
        assertEquals(6, bundle.lessons.size)
        assertEquals(lessonEntries.map { it.getValue("id").jsonPrimitive.content }, bundle.lessons.keys.toList())
        for ((id, lesson) in bundle.lessons) {
            val progress = owner()
            val coordinator = LocalLessonCoordinator(this, progress, loader::load, { 3_000L })
            try {
                coordinator.load(id)
                runCurrent()
                assertIs<LocalLessonState.Entry>(coordinator.state.value)
                coordinator.start()
                val plan = buildLessonPlan(lesson)
                assertEquals(lesson.dialogue.turns.map { it.id }, plan.stage(LessonStage.DIALOGUE).items.map { it.checkpointId })
                assertEquals(lesson.exercises.map { it.id }.toSet(), plan.stages.flatMap { it.items }
                    .filterIsInstance<LessonPlanItem.Prompt>().map { it.checkpointId }.toSet())
                assertEquals(lesson.exercises.size, plan.stages.sumOf { stage -> stage.items.count { it is LessonPlanItem.Prompt } })
                val seen = mutableListOf<String>()
                var steps = 0
                while (active(coordinator).session.stage != LessonStage.SUMMARY) {
                    check(++steps < 200) { "No progress through $id" }
                    val session = active(coordinator).session
                    session.checkpointId?.let(seen::add)
                    when (val item = session.item) {
                        is LessonPlanItem.Turn -> next(coordinator)
                        is LessonPlanItem.Prompt -> {
                            assertNull(session.outcome)
                            submit(coordinator, answer(item))
                            assertNotNull(active(coordinator).session.feedback)
                            assertEquals(LessonItemView.FEEDBACK, active(coordinator).session.itemView)
                            continueItem(coordinator)
                        }
                        null -> if (session.stage == LessonStage.ROLE_PLAY &&
                            plan.stage(LessonStage.ROLE_PLAY).items.isEmpty()) {
                            submit(coordinator, PracticeAnswer.SelfAssessment("自分で伝えます。", Assessment.NEEDS_PRACTICE))
                            continueItem(coordinator)
                        } else next(coordinator)
                    }
                }
                assertEquals(plan.stages.flatMap { it.items.map(LessonPlanItem::checkpointId) }, seen, id)
                assertFalse(active(coordinator).finished)
                assertNull(progress.state.value.snapshot.lessonProgress.single().completedAtEpochMs)
                val summary = active(coordinator).session
                assertEquals(lesson.exercises.size + if (plan.stage(LessonStage.ROLE_PLAY).items.isEmpty()) 1 else 0,
                    summary.outcomes.size)
                coordinator.finish(summary.id, summary.revision)
                assertTrue(active(coordinator).finished)
                val first = progress.state.value.snapshot.lessonProgress.single()
                assertEquals(LessonStage.SUMMARY, first.stage)
                assertNull(first.checkpointId)
                assertEquals(3_000L, first.completedAtEpochMs)
                coordinator.finish(summary.id, summary.revision)
                assertEquals(first, progress.state.value.snapshot.lessonProgress.single())
                assertTrue(progress.state.value.snapshot.reviewItems.isEmpty())
            } finally { coordinator.dispose(); progress.dispose() }
        }
        assertTrue(injected.reads.contains("catalog.json"))
    }

    @Test fun canonicalEveryStageReopensReadOnlyAndRemovedOrMovedCheckpointNeedsConsent() = runTest {
        val bundle = assertIs<CatalogLoad.Ready>(BundledContentLoader(source()).load()).content
        for ((id, lesson) in bundle.lessons) {
            val backing = MemoryProgressBacking()
            val progress = owner(backing)
            val plan = buildLessonPlan(lesson)
            for (stage in plan.stages) for (checkpoint in listOf<String?>(null) + stage.items.map { it.checkpointId }) {
                val record = LessonProgress(id, 1, 100L, stage.stage, checkpoint, completedAtEpochMs = 90L)
                progress.mutate { ProgressUpdate.Applied(it.copy(lessonProgress = listOf(record))) }
                val before = backing.raw
                val c = LocalLessonCoordinator(this, progress, BundledContentLoader(source())::load)
                c.load(id); runCurrent()
                assertIs<LocalLessonState.Entry>(c.state.value)
                c.resume()
                assertEquals(stage.stage, active(c).session.stage)
                assertEquals(checkpoint, active(c).session.checkpointId)
                assertEquals(before, backing.raw)
                assertEquals(record, progress.state.value.snapshot.lessonProgress.single())
                if (active(c).session.item is LessonPlanItem.Prompt) {
                    assertEquals(LESSON_RESUME_NOTICE, active(c).session.notice)
                    assertTrue(active(c).session.outcomes.isEmpty())
                }
                c.dispose()
            }
            val badIds = listOf("removed-checkpoint") + lesson.exercises.take(1).map { it.id }
            for (bad in badIds) {
                val invalid = LessonProgress(id, 1, 100L, LessonStage.DIALOGUE, bad, 90L)
                progress.mutate { ProgressUpdate.Applied(it.copy(lessonProgress = listOf(invalid))) }
                val old = backing.raw
                val c = LocalLessonCoordinator(this, progress, BundledContentLoader(source())::load, { 3_000L })
                c.load(id); runCurrent()
                assertIs<LocalLessonState.RecoveryRequired>(c.state.value)
                assertEquals(old, backing.raw)
                c.recoverToSituation()
                assertEquals(LessonStage.SITUATION, active(c).session.stage)
                assertEquals(90L, progress.state.value.snapshot.lessonProgress.single().completedAtEpochMs)
                c.dispose()
            }
            progress.dispose()
        }
    }

    @Test fun committedStageCheckpointsSurviveLeaveAndFreshOwnerForEveryCanonicalLesson() = runTest {
        for (entry in lessonEntries) {
            val id = entry.getValue("id").jsonPrimitive.content
            val backing = MemoryProgressBacking()
            var progress = owner(backing)
            var c = LocalLessonCoordinator(this, progress, BundledContentLoader(source())::load, { 3_000L })
            try {
                c.load(id); runCurrent(); c.start()
                val visited = mutableSetOf<LessonStage>()
                var steps = 0
                while (true) {
                    check(++steps < 200) { "Stalled reopening $id" }
                    val session = active(c).session
                    if (visited.add(session.stage)) {
                        val saved = progress.state.value.snapshot.lessonProgress.single()
                        assertEquals(session.stage, saved.stage)
                        assertEquals(session.checkpointId, saved.checkpointId)
                        dispatch(c) { sid, rev -> LessonCommand.Leave(sid, rev) }
                        c.dispose(); progress.dispose()
                        progress = owner(backing)
                        c = LocalLessonCoordinator(this, progress, BundledContentLoader(source())::load, { 3_000L })
                        c.load(id); runCurrent()
                        assertIs<LocalLessonState.Entry>(c.state.value)
                        c.resume()
                        assertEquals(session.stage, active(c).session.stage)
                        assertEquals(session.checkpointId, active(c).session.checkpointId)
                    }
                    if (session.stage == LessonStage.SUMMARY) break
                    when {
                        active(c).session.item is LessonPlanItem.Prompt ||
                            (active(c).session.stage == LessonStage.ROLE_PLAY &&
                                active(c).session.plan.stage(LessonStage.ROLE_PLAY).items.isEmpty()) ->
                            dispatch(c) { sid, rev -> LessonCommand.SkipRemaining(sid, rev) }
                        else -> next(c)
                    }
                }
                assertEquals(LessonStage.entries.toSet(), visited)
                assertNull(progress.state.value.snapshot.lessonProgress.single().completedAtEpochMs)
            } finally { c.dispose(); progress.dispose() }
        }
    }

    @Test fun canonicalInvalidRetryRevealSkipAndSelfAssessmentStayDistinct() = runTest {
        val id = lessonEntries.first().getValue("id").jsonPrimitive.content
        val progress = owner()
        val c = LocalLessonCoordinator(this, progress, BundledContentLoader(source())::load, { 3_000L })
        try {
            c.load(id); runCurrent(); c.start()
            while (active(c).session.stage != LessonStage.UNDERSTANDING) next(c)
            next(c)
            val first = active(c).session
            assertIs<LessonPlanItem.Prompt>(first.item)
            submit(c, PracticeAnswer.Text(""))
            assertNotNull(active(c).session.validation)
            assertNull(active(c).session.outcome)
            val prompt = active(c).session.item as LessonPlanItem.Prompt
            val choice = prompt.exercise as ChoiceExercise
            submit(c, PracticeAnswer.Choice(choice.options.first { it.id != choice.correctOptionId }.id))
            assertEquals(LessonOutcome.INCORRECT, active(c).session.outcome)
            val feedback = active(c).session.feedback
            dispatch(c) { sid, rev -> LessonCommand.Previous(sid, rev) }
            next(c)
            assertEquals(feedback, active(c).session.feedback)
            dispatch(c) { sid, rev -> LessonCommand.Retry(sid, rev) }
            assertNull(active(c).session.outcome)
            submit(c, PracticeAnswer.Choice(choice.correctOptionId))
            assertEquals(LessonOutcome.CORRECT, active(c).session.outcome)
            continueItem(c)
            if (active(c).session.stage == LessonStage.UNDERSTANDING && active(c).session.item != null) {
                dispatch(c) { sid, rev -> LessonCommand.Reveal(sid, rev) }
                assertEquals(LessonOutcome.REVEALED, active(c).session.outcome)
                continueItem(c)
            }
            while (active(c).session.stage != LessonStage.ROLE_PLAY) {
                val s = active(c).session
                when {
                    s.item is LessonPlanItem.Prompt -> dispatch(c) { sid, rev -> LessonCommand.SkipRemaining(sid, rev) }
                    else -> next(c)
                }
            }
            next(c)
            submit(c, PracticeAnswer.Text(""))
            assertNotNull(active(c).session.validation)
            submit(c, PracticeAnswer.SelfAssessment(" ", Assessment.MET_CRITERIA))
            assertNotNull(active(c).session.validation)
            submit(c, PracticeAnswer.SelfAssessment("伝えます。", Assessment.NEEDS_PRACTICE))
            assertEquals(LessonOutcome.SELF_NEEDS_PRACTICE, active(c).session.outcome)
            assertFalse(active(c).session.outcomes.contains(LessonOutcome.INCORRECT)) // retry replaced it
            assertFalse(SaveCodec.encodeSave(progress.state.value.snapshot).contains("伝えます。"))
        } finally { c.dispose(); progress.dispose() }
    }

    @Test fun canonicalBackupRestoreInvalidatesOldCallbacksAndDoesNotSerializeAnswers() = runTest {
        val id = lessonEntries.first().getValue("id").jsonPrimitive.content
        val backing = MemoryProgressBacking()
        val progress = owner(backing)
        val c = LocalLessonCoordinator(this, progress, BundledContentLoader(source())::load, { 3_000L })
        c.load(id); runCurrent(); c.start()
        val stale = active(c).session
        next(c)
        val current = progress.state.value.snapshot
        val backup = BackupCodec.encodeBackup(current, "1.0", 4_000L)
        assertFalse(backup.contains("自分の言葉で伝えます"))
        assertFalse(backup.contains("draft"))
        val restored = owner()
        try {
            val decoded = assertIs<BackupDecodeResult.Valid>(BackupCodec.decodeBackup(backup))
            val preparation = assertIs<ReplacementPreparation.Ready>(restored.beginRestore(decoded.backup)).token
            assertIs<ReplacementResult.Replaced>(restored.confirmReplacement(preparation))
            assertEquals(current.lessonProgress, restored.state.value.snapshot.lessonProgress)
            assertEquals(current.reviewItems, restored.state.value.snapshot.reviewItems)
            assertTrue(progress.reloadSavedState())
            assertIs<LocalLessonState.Entry>(c.state.value)
            c.dispatch(LessonCommand.Next(stale.id, stale.revision))
            assertIs<LocalLessonState.Entry>(c.state.value)
        } finally { c.dispose(); progress.dispose(); restored.dispose() }
    }
}
