package com.github.nanaki_93.practice

import com.github.nanaki_93.content.ChoiceExercise
import com.github.nanaki_93.content.ChoiceOption
import com.github.nanaki_93.content.JapaneseText
import com.github.nanaki_93.content.PracticeSet
import com.github.nanaki_93.content.ProductionExercise
import com.github.nanaki_93.content.ReadingExercise
import com.github.nanaki_93.content.KanaReadingAnswer
import com.github.nanaki_93.content.AnswerRepresentation
import com.github.nanaki_93.content.CompletionExercise
import com.github.nanaki_93.content.ReviewMetadata
import com.github.nanaki_93.content.ReviewStatus
import com.github.nanaki_93.content.ReviewerType
import com.github.nanaki_93.content.RightsStatus
import com.github.nanaki_93.progress.CheckpointView
import com.github.nanaki_93.progress.CheckpointExerciseType
import com.github.nanaki_93.progress.CompactOutcome
import com.github.nanaki_93.progress.PracticeCheckpoint
import com.github.nanaki_93.progress.PracticeRestoreResult
import com.github.nanaki_93.progress.SaveEnvelope
import com.github.nanaki_93.progress.decodeSave
import com.github.nanaki_93.progress.encodeSave
import com.github.nanaki_93.progress.projectPracticeCheckpoint
import com.github.nanaki_93.progress.restorePractice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PracticeCheckpointTest {
    private fun set() = PracticeSet(
        1, 1, "set", "topic", "Practice", "Practice description",
        review = ReviewMetadata(ReviewStatus.REVIEWED, ReviewerType.HUMAN, "2026-01-01", "review", "original", RightsStatus.PUBLISHABLE, "original"),
        exercises = listOf(
            ChoiceExercise("choice", "Pick", listOf(ChoiceOption("yes", label = "Yes"), ChoiceOption("no", label = "No")), "yes", "Authored explanation"),
            ProductionExercise("produce", "Write", listOf(JapaneseText("はい", "はい", translation = "Yes")), listOf("Be polite")),
        ),
    )

    private fun checkpoint(session: PracticeSession, set: PracticeSet = set(), previous: PracticeCheckpoint? = null,
                           token: String = "transition", time: Long = 100) =
        projectPracticeCheckpoint(session, set, time, "run", token, previous)

    @Test fun promptAndFeedbackRoundTripWithoutAnswerOrAuthoredText() {
        val source = set()
        val start = startSession(1, source)
        val prompt = checkpoint(start)
        assertEquals(listOf("choice", "produce"), prompt.exerciseIds)
        assertEquals(listOf(CheckpointExerciseType.CHOICE, CheckpointExerciseType.PRODUCTION), prompt.exerciseTypes)
        assertEquals(CheckpointView.PROMPT, prompt.view)
        val invalid = reduce(start, PracticeCommand.Submit(1, 0, PracticeAnswer.Choice("missing")))
        assertSame(prompt, checkpoint(invalid, previous = prompt, token = "ignored"))
        val feedback = reduce(invalid, PracticeCommand.Submit(1, 1, PracticeAnswer.Choice("no")))
        val saved = checkpoint(feedback, previous = prompt, token = "answered")
        assertEquals(listOf(CompactOutcome.INCORRECT), saved.outcomes)
        val wire = encodeSave(SaveEnvelope(savedAtEpochMs = 100, snapshotId = "save", revision = 0, practiceProgress = listOf(saved)))
        for (privateText in listOf("Authored explanation", "Pick", "no", "Be polite")) {
            assertTrue(privateText !in wire, "Leaked $privateText")
        }
        val decoded = assertIs<com.github.nanaki_93.progress.SaveDecodeResult.Valid>(decodeSave(wire)).snapshot.practiceProgress.single()
        val changed = source.copy(contentVersion = 2, exercises = listOf(
            (source.exercises[0] as ChoiceExercise).copy(explanation = "Updated explanation"), source.exercises[1],
        ))
        val restored = assertIs<PracticeRestoreResult.Restored>(restorePractice(900, changed, decoded)).session
        assertEquals(900, restored.id)
        assertEquals(0, restored.revision)
        assertEquals(SessionView.Feedback(0), restored.view)
        assertEquals("Updated explanation", (assertIs<PracticeOutcome.Incorrect>(restored.outcomes[0]).feedback as AuthoredFeedback.Choice).explanation)
        assertSame(restored, reduce(restored, PracticeCommand.Skip(1, 0)))
        assertSame(restored, reduce(restored, PracticeCommand.Continue(900, 1)))
        assertEquals(SessionView.Prompt(1), reduce(restored, PracticeCommand.Continue(900, 0)).view)
        assertEquals(OutcomeCounts(incorrect = 1), restored.counts)
        // Invalid submission has no saved draft or validation on resume.
        val clean = assertIs<PracticeRestoreResult.Restored>(restorePractice(901, source, prompt)).session
        assertEquals(SessionView.Prompt(0), clean.view)
    }

    @Test fun retryAndSelfAssessmentDoNotAccumulateResponsesOrOutcomes() {
        val source = set()
        var state = startSession(2, source)
        state = reduce(state, PracticeCommand.Reveal(2, state.revision))
        val first = checkpoint(state)
        state = reduce(state, PracticeCommand.Retry(2, state.revision))
        val retried = checkpoint(state, previous = first, token = "retry", time = 101)
        assertEquals(emptyList(), retried.outcomes)
        assertEquals(CheckpointView.PROMPT, retried.view)
        val resumed = assertIs<PracticeRestoreResult.Restored>(restorePractice(30, source, retried)).session
        assertEquals(OutcomeCounts(), resumed.counts)
        assertSame(resumed, reduce(resumed, PracticeCommand.Retry(2, state.revision)))
        state = reduce(resumed, PracticeCommand.Skip(30, 0))
        state = reduce(state, PracticeCommand.Continue(30, state.revision))
        val next = checkpoint(state, previous = retried, token = "next", time = 102)
        assertEquals(listOf(CompactOutcome.SKIPPED), next.outcomes)
        state = reduce(state, PracticeCommand.Submit(30, state.revision,
            PracticeAnswer.SelfAssessment("private learner sentence", Assessment.NEEDS_PRACTICE)))
        val assessed = checkpoint(state, previous = next, token = "assessed", time = 103)
        assertEquals(listOf(CompactOutcome.SKIPPED, CompactOutcome.SELF_NEEDS_PRACTICE), assessed.outcomes)
        val wire = encodeSave(SaveEnvelope(savedAtEpochMs = 103, snapshotId = "save", revision = 0, practiceProgress = listOf(assessed)))
        assertTrue("private learner sentence" !in wire)
        assertTrue("はい" !in wire)
        val feedback = assertIs<PracticeRestoreResult.Restored>(restorePractice(31, source, assessed)).session
        val outcome = assertIs<PracticeOutcome.SelfAssessed>(feedback.outcomes[1])
        assertEquals("", outcome.response)
        assertEquals(Assessment.NEEDS_PRACTICE, outcome.assessment)
        assertEquals(OutcomeCounts(skipped = 1, selfAssessed = 1), feedback.counts)
        assertEquals(SessionView.Complete, reduce(feedback, PracticeCommand.Continue(31, 0)).view)
        assertSame(assessed, checkpoint(reduce(feedback, PracticeCommand.Previous(31, 0)), previous = assessed, token = "history"))
    }

    @Test fun completionSurvivesNewRunAndHistoricalReviewDoesNotCompleteAgain() {
        val source = set()
        var state = startSession(4, source)
        repeat(2) {
            state = reduce(state, PracticeCommand.Skip(4, state.revision))
            state = reduce(state, PracticeCommand.Continue(4, state.revision))
        }
        val complete = checkpoint(state, time = 140)
        assertEquals(CheckpointView.COMPLETE, complete.view)
        assertEquals(140, complete.lastCompletedAtEpochMs)
        val wire = encodeSave(SaveEnvelope(savedAtEpochMs = 140, snapshotId = "save", revision = 0,
            practiceProgress = listOf(complete)))
        val decoded = assertIs<com.github.nanaki_93.progress.SaveDecodeResult.Valid>(decodeSave(wire)).snapshot.practiceProgress.single()
        assertEquals(complete, decoded)
        val restored = assertIs<PracticeRestoreResult.Restored>(restorePractice(50, source, decoded)).session
        assertTrue(restored.isComplete)
        val history = reduce(restored, PracticeCommand.Previous(50, 0))
        assertSame(complete, checkpoint(history, previous = complete, token = "history"))
        val returned = reduce(history, PracticeCommand.Return(50, history.revision))
        assertTrue(returned.isComplete)
        assertEquals(2, returned.counts.completed)
        assertSame(returned, reduce(returned, PracticeCommand.Continue(50, returned.revision)))
        val newRun = reduce(returned, PracticeCommand.Restart(50, returned.revision, 51))
        assertFailsWith<IllegalArgumentException> { checkpoint(newRun, previous = complete, token = "restart") }
        val restarted = projectPracticeCheckpoint(newRun, source, 150, "newRun", "restart", complete)
        assertEquals(140, restarted.lastCompletedAtEpochMs)
        assertEquals(emptyList(), restarted.outcomes)
        assertEquals(CheckpointView.PROMPT, restarted.view)
        assertEquals(140, checkpoint(reduce(startSession(60, source), PracticeCommand.Skip(60, 0)),
            previous = restarted, token = "answered", time = 151).lastCompletedAtEpochMs)
    }

    @Test fun missingAndIncompatibleContentRemainUnavailableWithoutChangingCheckpoint() {
        val source = set()
        val feedback = reduce(startSession(1, source), PracticeCommand.Skip(1, 0))
        val saved = checkpoint(feedback)
        assertEquals(PracticeRestoreResult.Unavailable, restorePractice(5, source.copy(id = "other"), saved))
        assertEquals(PracticeRestoreResult.Unavailable, restorePractice(5, source.copy(exercises = source.exercises.drop(1)), saved))
        val production = reduce(startSession(3, source.copy(exercises = listOf(source.exercises[1]))),
            PracticeCommand.Submit(3, 0, PracticeAnswer.SelfAssessment("secret", Assessment.MET_CRITERIA)))
        val productionSave = checkpoint(production, source.copy(exercises = listOf(source.exercises[1])))
        assertEquals(PracticeRestoreResult.Unavailable, restorePractice(5, source.copy(exercises = listOf(
            (source.exercises[0] as ChoiceExercise).copy(id = "produce"),
        )), productionSave))
        assertEquals(saved, saved.copy())
        assertIs<PracticeRestoreResult.Restored>(restorePractice(6, source.copy(contentVersion = 99), saved))
    }

    @Test fun reusedIdWithDifferentExerciseTypeCannotRestoreOldGradeOrPrompt() {
        val source = set()
        val choice = source.exercises.first() as ChoiceExercise
        val reading = ReadingExercise(choice.id, "Read", JapaneseText("はい", "はい", translation = "Yes"),
            AnswerRepresentation.KANA, listOf(KanaReadingAnswer(JapaneseText("はい", "はい", translation = "Yes"))), "Read it")
        val completion = CompletionExercise(choice.id, "Fill", "{blank}",
            listOf(JapaneseText("はい", "はい", translation = "Yes")),
            JapaneseText("はい", "はい", translation = "Yes"), "Fill it")
        for (answer in listOf("yes", "no")) {
            val grade = reduce(startSession(1, source), PracticeCommand.Submit(1, 0, PracticeAnswer.Choice(answer)))
            val saved = checkpoint(grade)
            for (replacement in listOf(reading, completion)) {
                assertEquals(PracticeRestoreResult.Unavailable, restorePractice(9,
                    source.copy(contentVersion = 2, exercises = listOf(replacement, source.exercises[1])), saved))
            }
            assertIs<PracticeRestoreResult.Restored>(restorePractice(9,
                source.copy(contentVersion = 2), saved))
        }
        val prompt = checkpoint(startSession(1, source))
        assertEquals(PracticeRestoreResult.Unavailable, restorePractice(9,
            source.copy(exercises = listOf(reading, source.exercises[1])), prompt))
        assertEquals(PracticeRestoreResult.Unavailable, restorePractice(9, source,
            prompt.copy(exerciseTypes = listOf(CheckpointExerciseType.CHOICE))))
    }

    @Test fun compactOutcomeKindsAndMaximumSessionLength() {
        val source = set()
        val longSet = source.copy(exercises = (1..12).map {
            (source.exercises[0] as ChoiceExercise).copy(id = "choice_$it")
        })
        val bounded = checkpoint(startSession(10, longSet), longSet)
        assertEquals(10, bounded.exerciseIds.size)
        assertEquals((1..10).map { "choice_$it" }, bounded.exerciseIds)
        assertIs<PracticeRestoreResult.Restored>(restorePractice(20, longSet, bounded))

        val correct = reduce(startSession(11, source), PracticeCommand.Submit(11, 0, PracticeAnswer.Choice("yes")))
        assertEquals(listOf(CompactOutcome.CORRECT), checkpoint(correct).outcomes)
        val reveal = reduce(startSession(12, source), PracticeCommand.Reveal(12, 0))
        assertEquals(listOf(CompactOutcome.REVEALED), checkpoint(reveal).outcomes)
        val productionSet = source.copy(exercises = listOf(source.exercises[1]))
        val met = reduce(startSession(13, productionSet), PracticeCommand.Submit(13, 0,
            PracticeAnswer.SelfAssessment("personal response", Assessment.MET_CRITERIA)))
        val record = checkpoint(met, productionSet)
        assertEquals(listOf(CompactOutcome.SELF_MET_CRITERIA), record.outcomes)
        assertEquals(Assessment.MET_CRITERIA, assertIs<PracticeOutcome.SelfAssessed>(
            assertIs<PracticeRestoreResult.Restored>(restorePractice(14, productionSet, record)).session.outcomes.single()
        ).assessment)
    }

    @Test fun malformedCheckpointsAndTokensCannotBecomeUsable() {
        val source = set()
        val saved = checkpoint(startSession(1, source))
        assertEquals(PracticeRestoreResult.Unavailable, restorePractice(1, source, saved.copy(frontier = 2)))
        assertEquals(PracticeRestoreResult.Unavailable, restorePractice(1, source, saved.copy(exerciseIds = listOf("absent"))))
        assertEquals(PracticeRestoreResult.Unavailable, restorePractice(0, source, saved))
        assertFailsWith<IllegalArgumentException> { checkpoint(startSession(1, source), token = "not valid") }
        val feedback = reduce(startSession(1, source), PracticeCommand.Skip(1, 0))
        assertFailsWith<IllegalArgumentException> { checkpoint(feedback, previous = saved) } // token replay
        assertFailsWith<IllegalArgumentException> { checkpoint(feedback, source.copy(id = "other")) }
    }
}
