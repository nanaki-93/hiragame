package com.github.nanaki_93.practice

import com.github.nanaki_93.content.ChoiceExercise
import com.github.nanaki_93.content.CompletionExercise
import com.github.nanaki_93.content.Exercise
import com.github.nanaki_93.content.KanaReadingAnswer
import com.github.nanaki_93.content.JapaneseText
import com.github.nanaki_93.content.RomajiReadingAnswer
import com.github.nanaki_93.content.PracticeSet
import com.github.nanaki_93.content.ProductionExercise
import com.github.nanaki_93.content.ReadingExercise

/** One entry per authored exercise, in authored order; no randomization or repeated padding. */
data class PlannedExercise(val id: String, val exercise: Exercise)

sealed interface PracticeOutcome {
    val feedback: AuthoredFeedback

    data class Correct(override val feedback: AuthoredFeedback) : PracticeOutcome
    data class Incorrect(override val feedback: AuthoredFeedback) : PracticeOutcome
    data class SelfAssessed(val assessment: Assessment, val response: String, override val feedback: AuthoredFeedback.Production) : PracticeOutcome
    data class Skipped(override val feedback: AuthoredFeedback) : PracticeOutcome
    data class Revealed(override val feedback: AuthoredFeedback) : PracticeOutcome
}

data class OutcomeCounts(
    val correct: Int = 0,
    val incorrect: Int = 0,
    val skipped: Int = 0,
    val revealed: Int = 0,
    val selfAssessed: Int = 0,
) {
    val completed: Int get() = correct + incorrect + skipped + revealed + selfAssessed
}

sealed interface SessionView {
    data class Prompt(val index: Int, val validation: InvalidReason? = null) : SessionView
    data class Feedback(val index: Int) : SessionView
    /** A resolved item; returnTo is the unchanged active frontier or completion. */
    data class Review(val index: Int, val returnTo: SessionView) : SessionView
    data object Complete : SessionView
    data object Left : SessionView
}

/** An expected identity and revision are captured with each UI action, not read at dispatch time. */
sealed interface PracticeCommand {
    val sessionId: Long
    val revision: Long

    data class Submit(override val sessionId: Long, override val revision: Long, val answer: PracticeAnswer) : PracticeCommand
    data class Skip(override val sessionId: Long, override val revision: Long) : PracticeCommand
    data class Reveal(override val sessionId: Long, override val revision: Long) : PracticeCommand
    data class Continue(override val sessionId: Long, override val revision: Long) : PracticeCommand
    data class Retry(override val sessionId: Long, override val revision: Long) : PracticeCommand
    data class Previous(override val sessionId: Long, override val revision: Long) : PracticeCommand
    data class Next(override val sessionId: Long, override val revision: Long) : PracticeCommand
    data class Return(override val sessionId: Long, override val revision: Long) : PracticeCommand
    /** IDs must increase across restarts, so no callback from any prior session can match. */
    data class Restart(override val sessionId: Long, override val revision: Long, val newSessionId: Long) : PracticeCommand
    data class Leave(override val sessionId: Long, override val revision: Long) : PracticeCommand
}

/** Snapshots copy their collections so a caller cannot modify an active plan or outcome slots. */
class PracticeSession private constructor(
    val id: Long,
    val revision: Long,
    private val orderedPlan: List<PlannedExercise>,
    private val slots: List<PracticeOutcome?>,
    val view: SessionView,
) {
    val plan: List<PlannedExercise> get() = orderedPlan.map { it.copy(exercise = it.exercise.snapshot()) }
    val outcomes: List<PracticeOutcome?> get() = slots.map { it?.snapshot() }
    val isComplete: Boolean get() = view == SessionView.Complete ||
        (view is SessionView.Review && view.returnTo == SessionView.Complete)
    val counts: OutcomeCounts get() {
        var counts = OutcomeCounts()
        for (outcome in slots) counts = when (outcome) {
            is PracticeOutcome.Correct -> counts.copy(correct = counts.correct + 1)
            is PracticeOutcome.Incorrect -> counts.copy(incorrect = counts.incorrect + 1)
            is PracticeOutcome.Skipped -> counts.copy(skipped = counts.skipped + 1)
            is PracticeOutcome.Revealed -> counts.copy(revealed = counts.revealed + 1)
            is PracticeOutcome.SelfAssessed -> counts.copy(selfAssessed = counts.selfAssessed + 1)
            null -> counts
        }
        return counts
    }

    internal fun transition(command: PracticeCommand): PracticeSession {
        if (command.sessionId != id || command.revision != revision || view == SessionView.Left) return this
        if (command is PracticeCommand.Leave) return updated(view = SessionView.Left)
        if (command is PracticeCommand.Restart) return if (command.newSessionId > id) {
            PracticeSession(command.newSessionId, 0, orderedPlan, List(orderedPlan.size) { null }, SessionView.Prompt(0))
        } else this
        return when (val current = view) {
            is SessionView.Prompt -> when (command) {
                is PracticeCommand.Submit -> when (val result = evaluate(orderedPlan[current.index].exercise, command.answer)) {
                    is EvaluationResult.Invalid -> updated(view = current.copy(validation = result.reason))
                    is EvaluationResult.Objective -> resolve(
                        current.index,
                        if (result.correct) PracticeOutcome.Correct(result.feedback) else PracticeOutcome.Incorrect(result.feedback),
                    )
                    is EvaluationResult.SelfAssessed -> resolve(
                        current.index, PracticeOutcome.SelfAssessed(result.assessment, result.response, result.feedback),
                    )
                }
                is PracticeCommand.Skip -> resolve(current.index, PracticeOutcome.Skipped(authoredFeedback(orderedPlan[current.index].exercise)))
                is PracticeCommand.Reveal -> resolve(current.index, PracticeOutcome.Revealed(authoredFeedback(orderedPlan[current.index].exercise)))
                is PracticeCommand.Previous -> if (current.index > 0) review(current.index - 1, current) else this
                else -> this
            }
            is SessionView.Feedback -> when (command) {
                is PracticeCommand.Continue -> updated(view = if (current.index + 1 == orderedPlan.size) SessionView.Complete else SessionView.Prompt(current.index + 1))
                is PracticeCommand.Retry -> updated(
                    slots = slots.toMutableList().also { it[current.index] = null },
                    view = SessionView.Prompt(current.index),
                )
                is PracticeCommand.Previous -> if (current.index > 0) review(current.index - 1, current) else this
                else -> this
            }
            is SessionView.Review -> when (command) {
                is PracticeCommand.Previous -> if (current.index > 0) review(current.index - 1, current.returnTo) else this
                is PracticeCommand.Next -> {
                    val frontier = when (val target = current.returnTo) {
                        is SessionView.Prompt -> target.index
                        is SessionView.Feedback -> target.index
                        SessionView.Complete -> orderedPlan.size
                        else -> error("Review must return to a frontier")
                    }
                    if (current.index + 1 < frontier) review(current.index + 1, current.returnTo)
                    else updated(view = current.returnTo)
                }
                is PracticeCommand.Return -> updated(view = current.returnTo)
                else -> this
            }
            SessionView.Complete -> if (command is PracticeCommand.Previous) review(orderedPlan.lastIndex, SessionView.Complete) else this
            SessionView.Left -> this
        }
    }

    private fun review(index: Int, returnTo: SessionView): PracticeSession {
        check(slots[index] != null) { "Cannot review an unresolved item" }
        return updated(view = SessionView.Review(index, returnTo))
    }

    private fun resolve(index: Int, outcome: PracticeOutcome): PracticeSession {
        check(slots[index] == null) { "Current prompt is already resolved" }
        return updated(slots = slots.toMutableList().also { it[index] = outcome }, view = SessionView.Feedback(index))
    }

    private fun updated(slots: List<PracticeOutcome?> = this.slots, view: SessionView): PracticeSession =
        PracticeSession(id, revision + 1, orderedPlan, slots.toList(), view)

    companion object {
        internal fun create(id: Long, exercises: List<Exercise>): PracticeSession = PracticeSession(
            id, 0, exercises.map { PlannedExercise(it.id, it.snapshot()) }, List(exercises.size) { null }, SessionView.Prompt(0),
        )
    }
}

/** Never expose feedback that aliases a stored exercise or a stored outcome. */
private fun JapaneseText.snapshot(): JapaneseText = copy(segments = segments.toList())

private fun AuthoredFeedback.snapshot(): AuthoredFeedback = when (this) {
    is AuthoredFeedback.Choice -> copy(correctOption = correctOption.copy(text = correctOption.text?.snapshot()))
    is AuthoredFeedback.Reading -> copy(
        stimulus = stimulus.snapshot(),
        acceptedAnswers = acceptedAnswers.map {
            when (it) {
                is ReadingExpectedAnswer.Kana -> it.copy(text = it.text.snapshot())
                is ReadingExpectedAnswer.Romaji -> it.copy()
            }
        },
    )
    is AuthoredFeedback.Completion -> copy(
        acceptedFills = acceptedFills.map { it.snapshot() },
        completedExample = completedExample.snapshot(),
    )
    is AuthoredFeedback.Production -> copy(
        examples = examples.map { it.copy(text = it.text.snapshot()) },
        criteria = criteria.map { it.copy() },
    )
}

private fun PracticeOutcome.snapshot(): PracticeOutcome = when (this) {
    is PracticeOutcome.Correct -> copy(feedback = feedback.snapshot())
    is PracticeOutcome.Incorrect -> copy(feedback = feedback.snapshot())
    is PracticeOutcome.Skipped -> copy(feedback = feedback.snapshot())
    is PracticeOutcome.Revealed -> copy(feedback = feedback.snapshot())
    is PracticeOutcome.SelfAssessed -> copy(feedback = feedback.snapshot() as AuthoredFeedback.Production)
}

/** Detach the authored exercise collections from the catalog's list instances. */
private fun Exercise.snapshot(): Exercise = when (this) {
    is ChoiceExercise -> copy(options = options.map { it.copy(text = it.text?.copy(segments = it.text.segments.toList())) })
    is ReadingExercise -> copy(
        stimulus = stimulus.copy(segments = stimulus.segments.toList()),
        acceptedAnswers = acceptedAnswers.map {
            when (it) {
                is KanaReadingAnswer -> it.copy(text = it.text.copy(segments = it.text.segments.toList()))
                is RomajiReadingAnswer -> it.copy()
            }
        },
    )
    is CompletionExercise -> copy(
        acceptedAnswers = acceptedAnswers.map { it.copy(segments = it.segments.toList()) },
        expectedCompletedExample = expectedCompletedExample.copy(segments = expectedCompletedExample.segments.toList()),
    )
    is ProductionExercise -> copy(exampleResponses = exampleResponses.map { it.copy(segments = it.segments.toList()) }, criteria = criteria.toList())
}

fun startSession(sessionId: Long, practiceSet: PracticeSet, limit: Int = 10): PracticeSession {
    require(limit in 1..10) { "Session limit must be 1 through 10" }
    val exercises = practiceSet.exercises.toList()
    require(exercises.isNotEmpty()) { "Cannot start an empty practice set" }
    require(exercises.map { it.id }.toSet().size == exercises.size) { "Duplicate exercise IDs in practice set" }
    return PracticeSession.create(sessionId, exercises.take(limit))
}

fun reduce(state: PracticeSession, command: PracticeCommand): PracticeSession = state.transition(command)
