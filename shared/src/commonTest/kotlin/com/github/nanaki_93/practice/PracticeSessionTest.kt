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
