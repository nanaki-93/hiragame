package com.github.nanaki_93.lesson

import com.github.nanaki_93.content.AdvisoryDifficulty
import com.github.nanaki_93.content.AnswerRepresentation
import com.github.nanaki_93.content.ChoiceExercise
import com.github.nanaki_93.content.ChoiceOption
import com.github.nanaki_93.content.CompletionExercise
import com.github.nanaki_93.content.ConversationGraph
import com.github.nanaki_93.content.Dialogue
import com.github.nanaki_93.content.DialogueTurn
import com.github.nanaki_93.content.JapaneseText
import com.github.nanaki_93.content.KanaReadingAnswer
import com.github.nanaki_93.content.Lesson
import com.github.nanaki_93.content.ProductionExercise
import com.github.nanaki_93.content.ReadingExercise
import com.github.nanaki_93.content.ReviewMetadata
import com.github.nanaki_93.content.ReviewStatus
import com.github.nanaki_93.content.ReviewerType
import com.github.nanaki_93.content.RightsStatus
import com.github.nanaki_93.content.RolePlayObjective
import com.github.nanaki_93.content.Speaker
import com.github.nanaki_93.content.TerminalNode
import com.github.nanaki_93.progress.LessonStage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LessonSessionTest {
    private val text = JapaneseText("はい。", "はい。", translation = "Yes.")
    private val review = ReviewMetadata(ReviewStatus.REVIEWED, ReviewerType.AGENT,
        "2026-10-02", "Reviewed", "Original", RightsStatus.PUBLISHABLE, "Original")
    private fun choice(id: String) = ChoiceExercise(id, "Choose", listOf(
        ChoiceOption("yes", label = "Yes"), ChoiceOption("no", label = "No")), "yes", "Why")
    private fun reading(id: String) = ReadingExercise(id, "Read", text, AnswerRepresentation.KANA,
        listOf(KanaReadingAnswer(text)), "Why")
    private fun completion(id: String) = CompletionExercise(id, "Fill", "{blank}", listOf(text), text, "Why")
    private fun production(id: String) = ProductionExercise(id, "Say it", listOf(text), listOf("Be clear"))

    private fun lesson() = Lesson(
        1, 3, "lesson-confirm-meeting-time", "workplace-clarification", "Confirm the meeting time",
        "A colleague mentions a meeting time", "Politely confirm the time", AdvisoryDifficulty.BEGINNER,
        5, emptyList(), Dialogue(listOf(Speaker("colleague", "Colleague", "Coworker")), listOf(
            DialogueTurn("turn-time", "colleague", text), DialogueTurn("turn-check", "colleague", text),
            DialogueTurn("turn-confirm", "colleague", text))),
        emptyList(), emptyList(), RolePlayObjective("Check the time", listOf("Ask politely")),
        emptyList(), review, listOf(choice("exercise-check-response"),
            completion("exercise-complete-time"), production("exercise-ask-confirmation")),
        ConversationGraph("node-time", listOf(TerminalNode("node-time", "End"))),
    )

    @Test fun supplementalSeedShapeMapsTurnsAndExercisesButNeverGraphNodes() {
        val source = lesson()
        val plan = buildLessonPlan(source)
        assertEquals(LessonStage.entries, plan.stages.map { it.stage })
        assertSame(source, plan.lesson)
        assertEquals(listOf("turn-time", "turn-check", "turn-confirm"),
            plan.stage(LessonStage.DIALOGUE).items.map { it.checkpointId })
        assertTrue(plan.stage(LessonStage.DIALOGUE).items.all { it is LessonPlanItem.Turn })
        assertEquals(listOf("exercise-check-response"),
            plan.stage(LessonStage.UNDERSTANDING).items.map { it.checkpointId })
        assertEquals(listOf("exercise-complete-time"),
            plan.stage(LessonStage.GUIDED_PRACTICE).items.map { it.checkpointId })
        assertEquals(listOf("exercise-ask-confirmation"),
            plan.stage(LessonStage.ROLE_PLAY).items.map { it.checkpointId })
        assertEquals(listOf("exercise-check-response", "exercise-complete-time", "exercise-ask-confirmation"),
            plan.stages.flatMap { it.items }.filterIsInstance<LessonPlanItem.Prompt>().map { it.checkpointId })
        assertEquals("Check the time", plan.lesson.rolePlay.task)
        for (stage in plan.stages) {
            assertTrue(stage.containsCheckpoint(null), "${stage.stage} stage entry")
            assertFalse(stage.containsCheckpoint("node-time"), "graph nodes are not F07 tasks")
        }
        assertTrue(plan.stage(LessonStage.DIALOGUE).containsCheckpoint("turn-check"))
        assertFalse(plan.stage(LessonStage.UNDERSTANDING).containsCheckpoint("exercise-complete-time"))
        assertFalse(plan.stage(LessonStage.GUIDED_PRACTICE).containsCheckpoint("exercise-check-response"))
        assertFalse(plan.stage(LessonStage.ROLE_PLAY).containsCheckpoint("exercise-check-response"))
        assertFalse(plan.stage(LessonStage.SITUATION).containsCheckpoint("turn-time"))
        assertFalse(plan.stage(LessonStage.SUMMARY).containsCheckpoint("exercise-ask-confirmation"))
        assertTrue(plan.stage(LessonStage.SITUATION).items.isEmpty())
        assertTrue(plan.stage(LessonStage.SUMMARY).items.isEmpty())
        assertNull(plan.stage(LessonStage.SITUATION).empty)
        assertNull(plan.stage(LessonStage.SUMMARY).empty)
    }

    @Test fun interleavedAuthoredExercisesKeepPerStageOrderAndDoNotStopAtTen() {
        val choices = (1..12).map { choice("choice-$it") }
        val source = lesson().copy(exercises = listOf(
            reading("reading-1"), choices[0], production("produce-1"),
            completion("fill-1"), choices[1], reading("reading-2"),
            production("produce-2"), completion("fill-2")) + choices.drop(2))
        val plan = buildLessonPlan(source)
        assertEquals(choices.map { it.id }, plan.stage(LessonStage.UNDERSTANDING).items.map { it.checkpointId })
        assertEquals(listOf("reading-1", "fill-1", "reading-2", "fill-2"),
            plan.stage(LessonStage.GUIDED_PRACTICE).items.map { it.checkpointId })
        assertEquals(listOf("produce-1", "produce-2"),
            plan.stage(LessonStage.ROLE_PLAY).items.map { it.checkpointId })
        assertEquals(source.exercises.size,
            plan.stages.flatMap { it.items }.filterIsInstance<LessonPlanItem.Prompt>().size)
        assertEquals(source.exercises.map { it.id }.toSet(),
            plan.stages.flatMap { it.items }.filterIsInstance<LessonPlanItem.Prompt>().map { it.checkpointId }.toSet())
        assertTrue(plan.stages.none { it.empty != null })
        assertIs<LessonPlanItem.Prompt>(plan.stage(LessonStage.UNDERSTANDING).items.last())
    }

    @Test fun emptyStagesAndObjectiveOnlyRolePlayHaveExplicitEntryWithoutInventingItems() {
        val plan = buildLessonPlan(lesson().copy(dialogue = Dialogue(emptyList(), emptyList()),
            exercises = emptyList()))
        assertEquals(EmptyLessonStage.NO_DIALOGUE_TURNS, plan.stage(LessonStage.DIALOGUE).empty)
        assertEquals(EmptyLessonStage.NO_EXERCISES, plan.stage(LessonStage.UNDERSTANDING).empty)
        assertEquals(EmptyLessonStage.NO_EXERCISES, plan.stage(LessonStage.GUIDED_PRACTICE).empty)
        assertEquals(EmptyLessonStage.ROLE_PLAY_OBJECTIVE_ONLY, plan.stage(LessonStage.ROLE_PLAY).empty)
        assertEquals("Check the time", plan.lesson.rolePlay.task)
        assertTrue(plan.stages.all { it.items.isEmpty() && it.containsCheckpoint(null) })
        assertTrue(plan.stages.all { !it.containsCheckpoint("node-time") })
    }

    @Test fun mutatedAuthoredListsCannotIntroduceDuplicateExecutableIds() {
        val exercises = lesson().exercises.toMutableList()
        val source = lesson().copy(exercises = exercises)
        exercises.add(exercises.first())
        assertFailsWith<IllegalArgumentException> { buildLessonPlan(source) }
        val turns = lesson().dialogue.turns.toMutableList()
        val altered = lesson().copy(dialogue = Dialogue(emptyList(), turns))
        turns.add(turns.first())
        assertFailsWith<IllegalArgumentException> { buildLessonPlan(altered) }
    }
}
