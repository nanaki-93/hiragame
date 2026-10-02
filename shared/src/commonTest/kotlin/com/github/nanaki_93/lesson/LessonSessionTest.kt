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
import com.github.nanaki_93.progress.LessonProgress
import com.github.nanaki_93.progress.LessonStage
import com.github.nanaki_93.practice.Assessment
import com.github.nanaki_93.practice.AuthoredFeedback
import com.github.nanaki_93.practice.InvalidReason
import com.github.nanaki_93.practice.PracticeAnswer
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

    private fun LessonSession.next() = reduceLesson(this, LessonCommand.Next(id, revision))
    private fun LessonSession.previous() = reduceLesson(this, LessonCommand.Previous(id, revision))
    private fun LessonSession.continueLesson() = reduceLesson(this, LessonCommand.Continue(id, revision))
    private fun LessonSession.skip() = reduceLesson(this, LessonCommand.Skip(id, revision))
    private fun LessonSession.reveal() = reduceLesson(this, LessonCommand.Reveal(id, revision))
    private fun LessonSession.submit(answer: PracticeAnswer) = reduceLesson(this, LessonCommand.Submit(id, revision, answer))
    private fun LessonSession.skipRemaining() = reduceLesson(this, LessonCommand.SkipRemaining(id, revision))
    private fun LessonSession.retry() = reduceLesson(this, LessonCommand.Retry(id, revision))

    @Test fun navigationIsExplicitAndUnresolvedPromptsAreGuarded() {
        var session = startLessonSession(7, lesson())
        assertEquals(LessonStage.SITUATION, session.stage)
        assertNull(session.checkpointId)
        assertSame(session, session.previous())
        session = session.next()
        assertEquals(LessonStage.DIALOGUE, session.stage)
        assertNull(session.item)
        session = session.next()
        assertEquals("turn-time", session.checkpointId)
        session = session.next().next().next()
        assertEquals(LessonStage.UNDERSTANDING, session.stage)
        assertNull(session.checkpointId)
        session = session.next()
        assertEquals("exercise-check-response", session.checkpointId)
        assertEquals(LessonItemView.PROMPT, session.itemView)
        assertNull(session.feedback)
        assertSame(session, session.next())
        assertSame(session, session.continueLesson())
        assertSame(session, session.retry())
        session = session.skip()
        assertEquals(LessonOutcome.SKIPPED, session.outcome)
        assertEquals(LessonItemView.FEEDBACK, session.itemView)
        assertEquals("Why", assertIs<AuthoredFeedback.Choice>(session.feedback).explanation)
        assertSame(session, session.next())
        assertSame(session, session.skip())
        session = session.continueLesson()
        assertEquals(LessonStage.GUIDED_PRACTICE, session.stage)
        assertNull(session.item)
    }

    @Test fun backtrackingRetainsOutcomesAndRetryClearsOnlyTheChosenSlot() {
        val source = lesson().copy(exercises = listOf(choice("first"), choice("second"), reading("read")))
        var session = startLessonSession(1, source).next().next().next().next().next().next()
        // Dialogue entry + three turns + Understanding entry + first prompt.
        assertEquals("first", session.checkpointId)
        session = session.skip().continueLesson()
        assertEquals("second", session.checkpointId)
        val blocked = session.next()
        assertSame(session, blocked)
        session = session.previous()
        assertEquals("first", session.checkpointId)
        assertEquals(LessonOutcome.SKIPPED, session.outcome)
        assertEquals(LessonItemView.FEEDBACK, session.itemView)
        assertIs<AuthoredFeedback.Choice>(session.feedback)
        session = session.previous()
        assertNull(session.item)
        session = session.next()
        assertEquals(LessonOutcome.SKIPPED, session.outcome)
        session = session.retry()
        assertNull(session.outcome)
        assertNull(session.feedback)
        assertEquals(LessonItemView.PROMPT, session.itemView)
        assertSame(session, session.continueLesson())
        session = session.skipRemaining()
        assertEquals(LessonStage.GUIDED_PRACTICE, session.stage)
        assertEquals(listOf(LessonOutcome.SKIPPED, LessonOutcome.SKIPPED), session.outcomes)
        assertNull(session.item)
    }

    @Test fun skipRemainingOnlySkipsUnresolvedItemsAndNeverChangesPriorOutcomes() {
        val source = lesson().copy(exercises = listOf(choice("first"), choice("second"), choice("third")))
        var session = startLessonSession(8, source)
        while (session.stage != LessonStage.UNDERSTANDING) session = session.next()
        session = session.next().skip().continueLesson()
        assertEquals("second", session.checkpointId)
        val skipCommand = LessonCommand.SkipRemaining(session.id, session.revision)
        session = reduceLesson(session, skipCommand)
        assertEquals(LessonStage.GUIDED_PRACTICE, session.stage)
        assertNull(session.item)
        assertEquals(List(3) { LessonOutcome.SKIPPED }, session.outcomes)
        assertSame(session, reduceLesson(session, skipCommand))
        session = session.previous()
        assertEquals("third", session.checkpointId)
        assertEquals(LessonItemView.FEEDBACK, session.itemView)
        session = session.retry()
        assertEquals(LessonItemView.PROMPT, session.itemView)
        assertEquals(2, session.outcomes.size)
        assertSame(session, session.next())
    }

    @Test fun emptyStagesNeedAnExplicitNextAndSkipRemainingDoesNotInventItems() {
        val source = lesson().copy(dialogue = Dialogue(emptyList(), emptyList()), exercises = emptyList())
        var session = startLessonSession(2, source)
        for (expected in LessonStage.entries.drop(1)) {
            session = session.next()
            assertEquals(expected, session.stage)
            assertNull(session.item)
            if (expected == LessonStage.ROLE_PLAY) {
                assertEquals(LessonItemView.PROMPT, session.itemView)
                assertSame(session, session.next())
                session = session.skip()
                assertEquals(LessonOutcome.SKIPPED, session.outcome)
                session = session.continueLesson()
            } else assertSame(session, session.skipRemaining())
        }
        assertSame(session, session.next())
        assertEquals(listOf(LessonOutcome.SKIPPED), session.outcomes)
    }

    @Test fun resumeKeepsSavedPlaceReadOnlyAndDoesNotRecreateFeedback() {
        val record = LessonProgress(lesson().id, 1, 20, LessonStage.GUIDED_PRACTICE,
            "exercise-complete-time", completedAtEpochMs = 18)
        val session = assertIs<LessonResumeResult.Available>(resumeLessonSession(10, lesson(), record)).session
        assertEquals(0, session.revision)
        assertEquals("exercise-complete-time", session.checkpointId)
        assertEquals(LessonItemView.PROMPT, session.itemView)
        assertNull(session.feedback)
        assertEquals(LESSON_RESUME_NOTICE, session.notice)
        assertTrue(session.resumed)
        assertTrue(session.outcomes.isEmpty())
        assertSame(session, session.continueLesson())
        val entry = assertIs<LessonResumeResult.Available>(resumeLessonSession(11, lesson(),
            record.copy(checkpointId = null))).session
        assertNull(entry.item)
        assertNull(entry.notice)
        assertEquals(LessonStage.GUIDED_PRACTICE, entry.stage)
        assertIs<LessonResumeResult.Incompatible>(resumeLessonSession(12, lesson(),
            record.copy(stage = LessonStage.UNDERSTANDING)))
    }

    @Test fun staleRepeatedAndOldRunCommandsNeverChangeNewSession() {
        val initial = startLessonSession(3, lesson())
        val next = LessonCommand.Next(initial.id, initial.revision)
        val moved = reduceLesson(initial, next)
        assertSame(moved, reduceLesson(moved, next))
        assertSame(moved, reduceLesson(moved, LessonCommand.Next(42, moved.revision)))
        assertSame(moved, reduceLesson(moved, LessonCommand.Restart(moved.id, moved.revision, moved.id)))
        val restarted = reduceLesson(moved, LessonCommand.Restart(moved.id, moved.revision, 4))
        assertEquals(4, restarted.id)
        assertEquals(0, restarted.revision)
        assertEquals(LessonStage.SITUATION, restarted.stage)
        assertSame(restarted, reduceLesson(restarted, next))
        assertSame(restarted, reduceLesson(restarted, LessonCommand.Leave(3, moved.revision)))
        val left = reduceLesson(restarted, LessonCommand.Leave(4, 0))
        assertTrue(left.left)
        assertSame(left, reduceLesson(left, LessonCommand.Next(4, left.revision)))
        assertSame(left, reduceLesson(left, LessonCommand.Restart(4, left.revision, 5)))
    }

    private fun atStage(source: Lesson, target: LessonStage): LessonSession {
        var session = startLessonSession(100, source)
        while (session.stage != target) session = if (session.item is LessonPlanItem.Prompt) {
            session.skip().continueLesson()
        } else session.next()
        return session
    }

    @Test fun objectiveAnswersUseAuthoredEvaluationAndInvalidSubmissionsStayAtPrompt() {
        val choice = atStage(lesson(), LessonStage.UNDERSTANDING).next()
        val wrongType = choice.submit(PracticeAnswer.Text("yes"))
        assertEquals(InvalidReason.WRONG_ANSWER_TYPE, wrongType.validation)
        assertEquals(LessonItemView.PROMPT, wrongType.itemView)
        assertNull(wrongType.feedback)
        assertSame(wrongType, reduceLesson(wrongType, LessonCommand.Submit(choice.id, choice.revision,
            PracticeAnswer.Choice("yes"))))
        val unknown = wrongType.submit(PracticeAnswer.Choice("missing"))
        assertEquals(InvalidReason.UNKNOWN_CHOICE, unknown.validation)
        val incorrect = unknown.submit(PracticeAnswer.Choice("no"))
        assertNull(incorrect.validation)
        assertEquals(LessonOutcome.INCORRECT, incorrect.outcome)
        assertEquals("Why", assertIs<AuthoredFeedback.Choice>(incorrect.feedback).explanation)
        assertSame(incorrect, incorrect.next())
        assertSame(incorrect, incorrect.submit(PracticeAnswer.Choice("yes")))
        assertEquals(LessonStage.GUIDED_PRACTICE, incorrect.continueLesson().stage)
        val correct = incorrect.retry().submit(PracticeAnswer.Choice("yes"))
        assertEquals(listOf(LessonOutcome.CORRECT), correct.outcomes)
        assertEquals(LessonItemView.FEEDBACK, correct.itemView)
        assertEquals(LessonOutcome.CORRECT, correct.previous().next().outcome)
    }

    @Test fun guidedReadingAndFillUseAuthoredNormalizationAndRevealNeverGrades() {
        val source = lesson().copy(exercises = listOf(reading("read"), completion("fill")))
        var session = atStage(source, LessonStage.GUIDED_PRACTICE).next()
        assertEquals(InvalidReason.BLANK_INPUT, session.submit(PracticeAnswer.Text("   ")).validation)
        session = session.submit(PracticeAnswer.Text(" はい。 "))
        assertEquals(LessonOutcome.CORRECT, session.outcome)
        assertEquals(text, assertIs<AuthoredFeedback.Reading>(session.feedback).stimulus)
        session = session.continueLesson()
        assertEquals("fill", session.checkpointId)
        assertEquals(LessonOutcome.INCORRECT, session.submit(PracticeAnswer.Text("違う")).outcome)
        session = session.reveal()
        assertEquals(LessonOutcome.REVEALED, session.outcome)
        assertEquals(text, assertIs<AuthoredFeedback.Completion>(session.feedback).completedExample)
        assertEquals(listOf(LessonOutcome.CORRECT, LessonOutcome.REVEALED), session.outcomes)
        assertEquals(LessonOutcome.SKIPPED, session.retry().skip().outcome)
    }

    @Test fun productionRequiresTextAndAssessmentAndNeverAutomaticallyGrades() {
        var session = atStage(lesson(), LessonStage.ROLE_PLAY).next()
        assertEquals(InvalidReason.WRONG_ANSWER_TYPE,
            session.submit(PracticeAnswer.Text("a plausible response")).validation)
        session = session.submit(PracticeAnswer.SelfAssessment(" \n ", Assessment.MET_CRITERIA))
        assertEquals(InvalidReason.BLANK_INPUT, session.validation)
        assertSame(session, session.continueLesson())
        session = session.submit(PracticeAnswer.SelfAssessment("My own response", Assessment.NEEDS_PRACTICE))
        assertEquals(LessonOutcome.SELF_NEEDS_PRACTICE, session.outcome)
        assertEquals(listOf(LessonOutcome.SKIPPED, LessonOutcome.SKIPPED, LessonOutcome.SELF_NEEDS_PRACTICE), session.outcomes)
        assertEquals("Self-assessment criterion", assertIs<AuthoredFeedback.Production>(session.feedback).criteria.first().label)
        assertEquals("Example response", assertIs<AuthoredFeedback.Production>(session.feedback).examples.first().label)
        session = session.continueLesson()
        assertEquals(LessonStage.SUMMARY, session.stage)
        session = session.previous().retry().submit(PracticeAnswer.SelfAssessment("not an exact match", Assessment.MET_CRITERIA))
        assertEquals(listOf(LessonOutcome.SKIPPED, LessonOutcome.SKIPPED, LessonOutcome.SELF_MET_CRITERIA), session.outcomes)
        assertEquals(LessonOutcome.REVEALED, session.retry().reveal().outcome)
        assertEquals(LessonOutcome.SKIPPED, session.retry().skip().outcome)
    }

    @Test fun objectiveOnlyRolePlayHasBoundedReflectionWithNullCheckpoint() {
        val source = lesson().copy(exercises = emptyList(), rolePlay = RolePlayObjective(
            "Check the time", listOf("Ask politely"), examples = listOf(text)))
        var session = atStage(source, LessonStage.ROLE_PLAY)
        assertNull(session.checkpointId)
        assertEquals(LessonItemView.PROMPT, session.itemView)
        assertSame(session, session.next())
        assertEquals(InvalidReason.WRONG_ANSWER_TYPE,
            session.submit(PracticeAnswer.Text("my response")).validation)
        session = session.submit(PracticeAnswer.SelfAssessment("   ", Assessment.MET_CRITERIA))
        assertEquals(InvalidReason.BLANK_INPUT, session.validation)
        session = session.submit(PracticeAnswer.SelfAssessment("自由な返事", Assessment.MET_CRITERIA))
        assertEquals(LessonOutcome.SELF_MET_CRITERIA, session.outcome)
        assertSame(session, session.next())
        val feedback = assertIs<AuthoredFeedback.Production>(session.feedback)
        assertEquals("Ask politely", feedback.criteria.single().text)
        assertEquals(text, feedback.examples.single().text)
        session = session.continueLesson()
        assertEquals(LessonStage.SUMMARY, session.stage)
        session = session.previous()
        assertEquals(LessonOutcome.SELF_MET_CRITERIA, session.outcome)
        session = session.retry()
        assertNull(session.outcome)
        assertNull(session.feedback)
        assertTrue(session.outcomes.isEmpty())
        session = session.reveal()
        assertEquals(listOf(LessonOutcome.REVEALED), session.outcomes)
        assertEquals(listOf(LessonOutcome.SKIPPED), session.retry().skip().outcomes)
        assertEquals(listOf(LessonOutcome.SKIPPED), session.retry().skipRemaining().outcomes)
        assertEquals(LessonStage.SUMMARY, session.retry().skipRemaining().stage)
        val record = LessonProgress(source.id, source.contentVersion, 20, LessonStage.ROLE_PLAY, null)
        val resumed = assertIs<LessonResumeResult.Available>(resumeLessonSession(101, source, record)).session
        assertEquals(LESSON_RESUME_NOTICE, resumed.notice)
        assertEquals(LessonItemView.PROMPT, resumed.itemView)
        assertNull(resumed.feedback)
        assertTrue(resumed.outcomes.isEmpty())
        val restarted = reduceLesson(session, LessonCommand.Restart(session.id, session.revision, 102))
        assertEquals(LessonStage.SITUATION, restarted.stage)
        assertTrue(restarted.outcomes.isEmpty())
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
