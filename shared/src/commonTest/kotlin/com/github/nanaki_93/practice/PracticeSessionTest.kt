package com.github.nanaki_93.practice

import com.github.nanaki_93.content.ChoiceExercise
import com.github.nanaki_93.content.ChoiceOption
import com.github.nanaki_93.content.CompletionExercise
import com.github.nanaki_93.content.KanaReadingAnswer
import com.github.nanaki_93.content.ReadingExercise
import com.github.nanaki_93.content.AnswerRepresentation
import com.github.nanaki_93.content.TextSegment
import com.github.nanaki_93.content.PracticeSet
import com.github.nanaki_93.content.ProductionExercise
import com.github.nanaki_93.content.JapaneseText
import com.github.nanaki_93.content.ReviewMetadata
import com.github.nanaki_93.content.ReviewStatus
import com.github.nanaki_93.content.ReviewerType
import com.github.nanaki_93.content.RightsStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PracticeSessionTest {
    private fun set(count: Int): PracticeSet = PracticeSet(
        1, 1, "set", "topic", "Practice", "Practice description",
        review = ReviewMetadata(ReviewStatus.REVIEWED, ReviewerType.HUMAN, "2026-01-01", "review", "original", RightsStatus.PUBLISHABLE, "original"),
        exercises = (1..count).map { number ->
            ChoiceExercise("item_$number", "Select the answer", listOf(
                ChoiceOption("yes", label = "Yes"), ChoiceOption("no", label = "No"),
            ), "yes", "Explanation")
        },
    )

    @Test fun planUsesAuthoredOrderAndBoundsWithoutRepeating() {
        val source = set(12)
        val default = startSession(42, source)
        assertEquals((1..10).map { "item_$it" }, default.plan.map { it.id })
        assertEquals(source.exercises.take(10), default.plan.map { it.exercise })
        assertEquals(10, default.outcomes.size)
        assertTrue(default.outcomes.all { it == null })
        assertEquals(OutcomeCounts(), default.counts)
        assertEquals(SessionView.Prompt(0), default.view)
        assertEquals(0, default.revision)
        assertEquals(listOf("item_1"), startSession(43, source, 1).plan.map { it.id })
        assertEquals(12, source.exercises.size)
        assertEquals(3, startSession(44, set(3)).plan.size)
        for (bad in listOf(-1, 0, 11, 100)) assertFailsWith<IllegalArgumentException> { startSession(1, source, bad) }
        assertFailsWith<IllegalArgumentException> { startSession(1, set(0)) }
    }

    @Test fun duplicateIdsAreRejectedEvenWhenBeyondTheLimit() {
        val exercises = set(11).exercises.toMutableList()
        val source = set(11).copy(exercises = exercises)
        exercises[10] = exercises[0] // A caller may mutate a list after PracticeSet validation.
        assertFailsWith<IllegalArgumentException> { startSession(1, source) }
    }

    @Test fun invalidSubmissionKeepsPromptAndDoesNotConsumeOutcome() {
        val initial = startSession(5, set(2))
        val invalid = reduce(initial, PracticeCommand.Submit(5, 0, PracticeAnswer.Choice("unknown")))
        assertEquals(SessionView.Prompt(0, InvalidReason.UNKNOWN_CHOICE), invalid.view)
        assertEquals(1, invalid.revision)
        assertEquals(OutcomeCounts(), invalid.counts)
        assertTrue(invalid.outcomes.all { it == null })
        assertSame(invalid, reduce(invalid, PracticeCommand.Submit(5, 0, PracticeAnswer.Choice("yes"))))
        val blank = reduce(invalid, PracticeCommand.Submit(5, 1, PracticeAnswer.Choice(" ")))
        assertEquals(SessionView.Prompt(0, InvalidReason.BLANK_INPUT), blank.view)
        val correct = reduce(blank, PracticeCommand.Submit(5, 2, PracticeAnswer.Choice("yes")))
        assertEquals(SessionView.Feedback(0), correct.view)
        assertIs<PracticeOutcome.Correct>(correct.outcomes[0])
        assertEquals(OutcomeCounts(correct = 1), correct.counts)
    }

    @Test fun resolutionIsSynchronousAndContinueIsRequiredEvenOnFinalItem() {
        val initial = startSession(9, set(2))
        val feedback = reduce(initial, PracticeCommand.Submit(9, 0, PracticeAnswer.Choice("no")))
        assertEquals(SessionView.Feedback(0), feedback.view)
        assertIs<PracticeOutcome.Incorrect>(feedback.outcomes[0])
        assertEquals(OutcomeCounts(incorrect = 1), feedback.counts)
        assertSame(feedback, reduce(feedback, PracticeCommand.Submit(9, 1, PracticeAnswer.Choice("yes"))))
        assertSame(feedback, reduce(feedback, PracticeCommand.Skip(9, 1)))
        val next = reduce(feedback, PracticeCommand.Continue(9, 1))
        assertEquals(SessionView.Prompt(1), next.view)
        assertEquals(null, next.outcomes[1])
        assertSame(next, reduce(next, PracticeCommand.Continue(9, 1)))
        val last = reduce(next, PracticeCommand.Submit(9, 2, PracticeAnswer.Choice("yes")))
        assertEquals(SessionView.Feedback(1), last.view)
        assertEquals(OutcomeCounts(correct = 1, incorrect = 1), last.counts)
        val complete = reduce(last, PracticeCommand.Continue(9, 3))
        assertEquals(SessionView.Complete, complete.view)
        assertEquals(4, complete.revision)
        assertSame(complete, reduce(complete, PracticeCommand.Continue(9, 4)))
    }

    @Test fun skipAndRevealExposeAuthoredFeedbackButNeverGradeCorrect() {
        var state = startSession(10, set(2))
        state = reduce(state, PracticeCommand.Skip(10, 0))
        val skipped = assertIs<PracticeOutcome.Skipped>(state.outcomes[0])
        assertEquals("Explanation", (skipped.feedback as AuthoredFeedback.Choice).explanation)
        assertEquals(SessionView.Feedback(0), state.view)
        state = reduce(state, PracticeCommand.Continue(10, 1))
        state = reduce(state, PracticeCommand.Reveal(10, 2))
        assertIs<PracticeOutcome.Revealed>(state.outcomes[1])
        assertEquals(OutcomeCounts(skipped = 1, revealed = 1), state.counts)
        assertEquals(2, state.counts.completed)
        assertEquals(SessionView.Feedback(1), state.view)
    }

    @Test fun selfAssessmentUsesItsOwnSlotAndSingleItemNeedsContinue() {
        val source = set(1).copy(exercises = listOf(ProductionExercise(
            "write", "Write a response", listOf(JapaneseText("はい", "はい", gloss = null, translation = "Yes")),
            listOf("Be polite"),
        )))
        val initial = startSession(17, source)
        val invalid = reduce(initial, PracticeCommand.Submit(17, 0, PracticeAnswer.Text("はい")))
        assertEquals(SessionView.Prompt(0, InvalidReason.WRONG_ANSWER_TYPE), invalid.view)
        val feedback = reduce(invalid, PracticeCommand.Submit(17, 1, PracticeAnswer.SelfAssessment(" はい ", Assessment.NEEDS_PRACTICE)))
        val outcome = assertIs<PracticeOutcome.SelfAssessed>(feedback.outcomes.single())
        assertEquals("はい", outcome.response)
        assertEquals(Assessment.NEEDS_PRACTICE, outcome.assessment)
        assertEquals(OutcomeCounts(selfAssessed = 1), feedback.counts)
        assertEquals(SessionView.Feedback(0), feedback.view)
        assertEquals(SessionView.Complete, reduce(feedback, PracticeCommand.Continue(17, 2)).view)
    }

    @Test fun planAndSlotsDoNotTrackMutationsOfTheInputListOrReturnedLists() {
        val exercises = set(2).exercises.toMutableList()
        val options = (exercises[0] as ChoiceExercise).options.toMutableList()
        exercises[0] = (exercises[0] as ChoiceExercise).copy(options = options)
        val source = set(2).copy(exercises = exercises)
        val state = startSession(1, source)
        exercises.clear()
        options.clear()
        assertEquals(listOf("item_1", "item_2"), state.plan.map { it.id })
        assertEquals(2, (state.plan[0].exercise as ChoiceExercise).options.size)
        ((state.plan[0].exercise as ChoiceExercise).options as MutableList).clear()
        assertEquals(2, (state.plan[0].exercise as ChoiceExercise).options.size)
        (state.plan as MutableList).clear()
        (state.outcomes as MutableList).clear()
        assertEquals(2, state.plan.size)
        assertEquals(2, state.outcomes.size)
    }

    @Test fun returnedFeedbackCannotMutateTheStoredPlanOrOutcome() {
        val text = JapaneseText("あ", "あ", gloss = "a", segments = mutableListOf(TextSegment("あ")))
        val reading = ReadingExercise("read", "Read this", text, AnswerRepresentation.KANA,
            mutableListOf(KanaReadingAnswer(text)), "Explanation")
        val state = reduce(startSession(20, set(1).copy(exercises = listOf(reading))),
            PracticeCommand.Submit(20, 0, PracticeAnswer.Text("あ")))
        val feedback = assertIs<AuthoredFeedback.Reading>(assertIs<PracticeOutcome.Correct>(state.outcomes[0]).feedback)
        (feedback.stimulus.segments as MutableList).clear()
        (assertIs<ReadingExpectedAnswer.Kana>(feedback.acceptedAnswers[0]).text.segments as MutableList).clear()
        (feedback.acceptedAnswers as MutableList).clear()
        (state.outcomes as MutableList).clear()
        val stored = assertIs<AuthoredFeedback.Reading>(assertIs<PracticeOutcome.Correct>(state.outcomes[0]).feedback)
        assertEquals(listOf(TextSegment("あ")), stored.stimulus.segments)
        assertEquals(listOf(TextSegment("あ")), assertIs<ReadingExpectedAnswer.Kana>(stored.acceptedAnswers.single()).text.segments)
        assertEquals(listOf(TextSegment("あ")), (state.plan[0].exercise as ReadingExercise).stimulus.segments)
        assertEquals(OutcomeCounts(correct = 1), state.counts)
    }

    @Test fun otherOutcomeFeedbackTypesAreDetachedToo() {
        val text = JapaneseText("あ", "あ", gloss = "a", segments = mutableListOf(TextSegment("あ")))
        val exercises = listOf(
            ChoiceExercise("choice", "Pick", listOf(ChoiceOption("yes", text = text), ChoiceOption("no", label = "No")), "yes", "Why"),
            CompletionExercise("fill", "Fill", "{blank}", listOf(text), text, "Why"),
            ProductionExercise("produce", "Write", listOf(text), listOf("Be clear")),
        )
        val answers = listOf<PracticeCommand>(
            PracticeCommand.Skip(21, 0), PracticeCommand.Reveal(22, 0),
            PracticeCommand.Submit(23, 0, PracticeAnswer.SelfAssessment("あ", Assessment.MET_CRITERIA)),
        )
        exercises.zip(answers).forEach { (exercise, command) ->
            val state = reduce(startSession(command.sessionId, set(1).copy(exercises = listOf(exercise))), command)
            val exposed = state.outcomes.single()!!.feedback
            val segments = when (exposed) {
                is AuthoredFeedback.Choice -> exposed.correctOption.text!!.segments
                is AuthoredFeedback.Completion -> exposed.acceptedFills[0].segments
                is AuthoredFeedback.Production -> exposed.examples[0].text.segments
                else -> error("Unexpected feedback")
            }
            (segments as MutableList).clear()
            val retained = state.outcomes.single()!!.feedback
            val retainedSegments = when (retained) {
                is AuthoredFeedback.Choice -> retained.correctOption.text!!.segments
                is AuthoredFeedback.Completion -> retained.acceptedFills[0].segments
                is AuthoredFeedback.Production -> retained.examples[0].text.segments
                else -> error("Unexpected feedback")
            }
            assertEquals(listOf(TextSegment("あ")), retainedSegments)
        }
    }

    @Test fun retryReplacesOneSlotWithoutGrowingThePlanOrSummary() {
        var state = startSession(30, set(2))
        val originalPlan = state.plan
        repeat(5) { attempt ->
            val feedback = reduce(state, PracticeCommand.Submit(30, state.revision, PracticeAnswer.Choice("no")))
            assertEquals(SessionView.Feedback(0), feedback.view)
            assertEquals(OutcomeCounts(incorrect = 1), feedback.counts)
            assertSame(feedback, reduce(feedback, PracticeCommand.Retry(30, state.revision)))
            state = reduce(feedback, PracticeCommand.Retry(30, feedback.revision))
            assertEquals(SessionView.Prompt(0), state.view)
            assertEquals(OutcomeCounts(), state.counts)
            assertEquals(listOf(null, null), state.outcomes)
            assertEquals(originalPlan, state.plan)
            assertEquals(2, state.outcomes.size, "attempt $attempt appended a slot")
            assertSame(state, reduce(state, PracticeCommand.Retry(30, feedback.revision)))
        }
        state = reduce(state, PracticeCommand.Submit(30, state.revision, PracticeAnswer.Choice("yes")))
        assertEquals(OutcomeCounts(correct = 1), state.counts)
        assertEquals(1, state.counts.completed)
        assertEquals(originalPlan, state.plan)
        val next = reduce(state, PracticeCommand.Continue(30, state.revision))
        assertEquals(SessionView.Prompt(1), next.view)
        assertSame(next, reduce(next, PracticeCommand.Retry(30, next.revision)))
    }

    @Test fun reviewFromPromptAndFeedbackIsReadOnlyAndReturnsToTheSameFrontier() {
        var state = startSession(31, set(3))
        state = reduce(state, PracticeCommand.Skip(31, state.revision))
        state = reduce(state, PracticeCommand.Continue(31, state.revision))
        state = reduce(state, PracticeCommand.Reveal(31, state.revision))
        state = reduce(state, PracticeCommand.Continue(31, state.revision))
        val frontier = state
        val review = reduce(state, PracticeCommand.Previous(31, state.revision))
        assertEquals(SessionView.Review(1, SessionView.Prompt(2)), review.view)
        val earlier = reduce(review, PracticeCommand.Previous(31, review.revision))
        assertEquals(SessionView.Review(0, SessionView.Prompt(2)), earlier.view)
        assertSame(earlier, reduce(earlier, PracticeCommand.Previous(31, earlier.revision)))
        for (command in listOf(
            PracticeCommand.Submit(31, earlier.revision, PracticeAnswer.Choice("yes")),
            PracticeCommand.Skip(31, earlier.revision), PracticeCommand.Reveal(31, earlier.revision),
            PracticeCommand.Retry(31, earlier.revision), PracticeCommand.Continue(31, earlier.revision),
        )) assertSame(earlier, reduce(earlier, command))
        val forward = reduce(earlier, PracticeCommand.Next(31, earlier.revision))
        assertEquals(SessionView.Review(1, SessionView.Prompt(2)), forward.view)
        val back = reduce(forward, PracticeCommand.Next(31, forward.revision))
        assertEquals(frontier.view, back.view)
        assertEquals(frontier.outcomes, back.outcomes)
        assertEquals(OutcomeCounts(skipped = 1, revealed = 1), back.counts)
        assertSame(back, reduce(back, PracticeCommand.Next(31, forward.revision)))

        val feedback = reduce(back, PracticeCommand.Submit(31, back.revision, PracticeAnswer.Choice("no")))
        val history = reduce(feedback, PracticeCommand.Previous(31, feedback.revision))
        assertEquals(SessionView.Review(1, feedback.view), history.view)
        val returned = reduce(history, PracticeCommand.Return(31, history.revision))
        assertEquals(feedback.view, returned.view)
        assertEquals(feedback.outcomes, returned.outcomes)
        assertSame(returned, reduce(returned, PracticeCommand.Return(31, history.revision)))
        val again = reduce(returned, PracticeCommand.Previous(31, returned.revision))
        assertEquals(feedback.view, reduce(again, PracticeCommand.Next(31, again.revision)).view)
    }

    @Test fun completionReviewNeverReopensTheSession() {
        var state = startSession(32, set(2))
        repeat(2) {
            state = reduce(state, PracticeCommand.Submit(32, state.revision, PracticeAnswer.Choice("yes")))
            state = reduce(state, PracticeCommand.Continue(32, state.revision))
        }
        assertEquals(SessionView.Complete, state.view)
        val review = reduce(state, PracticeCommand.Previous(32, state.revision))
        assertEquals(SessionView.Review(1, SessionView.Complete), review.view)
        assertTrue(review.isComplete)
        val previous = reduce(review, PracticeCommand.Previous(32, review.revision))
        assertEquals(SessionView.Review(0, SessionView.Complete), previous.view)
        assertTrue(previous.isComplete)
        assertSame(previous, reduce(previous, PracticeCommand.Submit(32, previous.revision, PracticeAnswer.Choice("no"))))
        val returned = reduce(previous, PracticeCommand.Return(32, previous.revision))
        assertEquals(SessionView.Complete, returned.view)
        assertTrue(returned.isComplete)
        assertEquals(OutcomeCounts(correct = 2), returned.counts)
        val last = reduce(returned, PracticeCommand.Previous(32, returned.revision))
        assertEquals(SessionView.Complete, reduce(last, PracticeCommand.Next(32, last.revision)).view)
        val restarted = reduce(last, PracticeCommand.Restart(32, last.revision, 33))
        assertEquals(SessionView.Prompt(0), restarted.view)
        assertTrue(!restarted.isComplete)
        assertEquals(OutcomeCounts(), restarted.counts)
        assertSame(restarted, reduce(restarted, PracticeCommand.Return(32, last.revision)))
    }

    @Test fun restartAndLeaveGuardIdentityAndRevisionAcrossLifetimes() {
        var state = startSession(40, set(2))
        val oldSubmit = PracticeCommand.Submit(40, 0, PracticeAnswer.Choice("yes"))
        state = reduce(state, oldSubmit)
        val oldContinue = PracticeCommand.Continue(40, state.revision)
        assertSame(state, reduce(state, PracticeCommand.Restart(40, 0, 41)))
        assertSame(state, reduce(state, PracticeCommand.Restart(40, state.revision, 40)))
        val restart = PracticeCommand.Restart(40, state.revision, 41)
        val restarted = reduce(state, restart)
        assertEquals(41, restarted.id)
        assertEquals(0, restarted.revision)
        assertEquals(SessionView.Prompt(0), restarted.view)
        assertEquals(OutcomeCounts(), restarted.counts)
        assertEquals(listOf(null, null), restarted.outcomes)
        assertEquals(state.plan, restarted.plan)
        for (command in listOf(oldSubmit, oldContinue, restart, PracticeCommand.Leave(40, state.revision))) {
            assertSame(restarted, reduce(restarted, command))
        }
        val left = reduce(restarted, PracticeCommand.Leave(41, 0))
        assertEquals(SessionView.Left, left.view)
        assertEquals(1, left.revision)
        for (command in listOf(
            PracticeCommand.Submit(41, 1, PracticeAnswer.Choice("yes")), PracticeCommand.Skip(41, 1),
            PracticeCommand.Restart(41, 1, 42), PracticeCommand.Leave(41, 1),
            PracticeCommand.Previous(41, 1), PracticeCommand.Return(41, 1),
        )) assertSame(left, reduce(left, command))
    }

    @Test fun retryFinalFeedbackStillRequiresASecondExplicitContinue() {
        var state = startSession(60, set(1))
        state = reduce(state, PracticeCommand.Reveal(60, state.revision))
        val oldContinue = PracticeCommand.Continue(60, state.revision)
        state = reduce(state, PracticeCommand.Retry(60, state.revision))
        assertEquals(OutcomeCounts(), state.counts)
        assertSame(state, reduce(state, oldContinue))
        state = reduce(state, PracticeCommand.Submit(60, state.revision, PracticeAnswer.Choice("no")))
        assertEquals(OutcomeCounts(incorrect = 1), state.counts)
        assertEquals(SessionView.Feedback(0), state.view)
        assertTrue(!state.isComplete)
        state = reduce(state, PracticeCommand.Continue(60, state.revision))
        assertEquals(SessionView.Complete, state.view)
        assertEquals(1, state.counts.completed)
        val left = reduce(state, PracticeCommand.Leave(60, state.revision))
        assertEquals(SessionView.Left, left.view)
        assertSame(left, reduce(left, PracticeCommand.Previous(60, left.revision)))
    }

    @Test fun summariesDistinguishAllFiveKinds() {
        val production = ProductionExercise("produce", "Write", listOf(JapaneseText("はい", "はい", translation = "Yes")), listOf("Polite"))
        val source = set(5).copy(exercises = set(4).exercises + production)
        var state = startSession(50, source)
        val commands: List<(PracticeSession) -> PracticeCommand> = listOf(
            { PracticeCommand.Submit(50, it.revision, PracticeAnswer.Choice("yes")) },
            { PracticeCommand.Submit(50, it.revision, PracticeAnswer.Choice("no")) },
            { PracticeCommand.Skip(50, it.revision) },
            { PracticeCommand.Reveal(50, it.revision) },
            { PracticeCommand.Submit(50, it.revision, PracticeAnswer.SelfAssessment("はい", Assessment.MET_CRITERIA)) },
        )
        for (command in commands) {
            state = reduce(state, command(state))
            state = reduce(state, PracticeCommand.Continue(50, state.revision))
        }
        assertEquals(SessionView.Complete, state.view)
        assertEquals(OutcomeCounts(1, 1, 1, 1, 1), state.counts)
        assertEquals(5, state.counts.completed)
        assertIs<PracticeOutcome.SelfAssessed>(state.outcomes.last())
        val history = reduce(state, PracticeCommand.Previous(50, state.revision))
        assertEquals(state.counts, history.counts)
        assertEquals(state.outcomes, history.outcomes)
    }

    @Test fun wrongSessionAndStaleRevisionsCannotResolveOrSkipAnotherItem() {
        val initial = startSession(100, set(2))
        assertSame(initial, reduce(initial, PracticeCommand.Skip(101, 0)))
        assertSame(initial, reduce(initial, PracticeCommand.Reveal(100, 1)))
        assertSame(initial, reduce(initial, PracticeCommand.Continue(100, 0)))
        val resolved = reduce(initial, PracticeCommand.Reveal(100, 0))
        for (command in listOf(
            PracticeCommand.Submit(100, 0, PracticeAnswer.Choice("yes")),
            PracticeCommand.Skip(100, 0), PracticeCommand.Reveal(100, 0), PracticeCommand.Continue(101, 1),
        )) assertSame(resolved, reduce(resolved, command))
        val next = reduce(resolved, PracticeCommand.Continue(100, 1))
        assertSame(next, reduce(next, PracticeCommand.Continue(100, 1)))
        assertSame(next, reduce(next, PracticeCommand.Skip(100, 0)))
        assertEquals(OutcomeCounts(revealed = 1), next.counts)
        assertEquals(null, next.outcomes[1])
    }
}
