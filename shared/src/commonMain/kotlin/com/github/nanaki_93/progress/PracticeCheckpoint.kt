package com.github.nanaki_93.progress

import com.github.nanaki_93.content.ChoiceExercise
import com.github.nanaki_93.content.CompletionExercise
import com.github.nanaki_93.content.Exercise
import com.github.nanaki_93.content.PracticeSet
import com.github.nanaki_93.content.ProductionExercise
import com.github.nanaki_93.content.ReadingExercise
import com.github.nanaki_93.practice.Assessment
import com.github.nanaki_93.practice.PracticeOutcome
import com.github.nanaki_93.practice.PracticeSession
import com.github.nanaki_93.practice.SessionView
import com.github.nanaki_93.practice.authoredFeedback

sealed interface PracticeRestoreResult {
    data class Restored(val session: PracticeSession) : PracticeRestoreResult
    /** The original checkpoint is left intact for explicit fresh-start recovery. */
    data object Unavailable : PracticeRestoreResult
}

private fun PracticeOutcome.compact(): CompactOutcome = when (this) {
    is PracticeOutcome.Correct -> CompactOutcome.CORRECT
    is PracticeOutcome.Incorrect -> CompactOutcome.INCORRECT
    is PracticeOutcome.Skipped -> CompactOutcome.SKIPPED
    is PracticeOutcome.Revealed -> CompactOutcome.REVEALED
    is PracticeOutcome.SelfAssessed -> when (assessment) {
        Assessment.MET_CRITERIA -> CompactOutcome.SELF_MET_CRITERIA
        Assessment.NEEDS_PRACTICE -> CompactOutcome.SELF_NEEDS_PRACTICE
    }
}

private fun Exercise.checkpointType(): CheckpointExerciseType = when (this) {
    is ChoiceExercise -> CheckpointExerciseType.CHOICE
    is ReadingExercise -> CheckpointExerciseType.READING
    is CompletionExercise -> CheckpointExerciseType.COMPLETION
    is ProductionExercise -> CheckpointExerciseType.PRODUCTION
}

private fun Exercise.reconstruct(outcome: CompactOutcome): PracticeOutcome {
    val feedback = authoredFeedback(this)
    return when (outcome) {
        CompactOutcome.CORRECT -> PracticeOutcome.Correct(feedback)
        CompactOutcome.INCORRECT -> PracticeOutcome.Incorrect(feedback)
        CompactOutcome.SKIPPED -> PracticeOutcome.Skipped(feedback)
        CompactOutcome.REVEALED -> PracticeOutcome.Revealed(feedback)
        // No learner response is recoverable. Empty means "not retained", not a new submission.
        CompactOutcome.SELF_MET_CRITERIA -> PracticeOutcome.SelfAssessed(
            Assessment.MET_CRITERIA, "", feedback as com.github.nanaki_93.practice.AuthoredFeedback.Production,
        )
        CompactOutcome.SELF_NEEDS_PRACTICE -> PracticeOutcome.SelfAssessed(
            Assessment.NEEDS_PRACTICE, "", feedback as com.github.nanaki_93.practice.AuthoredFeedback.Production,
        )
    }
}

/** Project only the committed frontier, never a draft, review position, response, or authored feedback.
 * The caller supplies stable tokens for the run and accepted transition (not the runtime session ID).
 * Replaying the same frontier with a different token is not a new learning action.
 */
fun projectPracticeCheckpoint(
    session: PracticeSession,
    practiceSet: PracticeSet,
    updatedAtEpochMs: Long,
    runToken: String,
    transitionToken: String,
    previous: PracticeCheckpoint? = null,
): PracticeCheckpoint {
    require(session.setId == practiceSet.id && (previous == null || previous.setId == practiceSet.id)) {
        "Practice set mismatch"
    }
    previous?.let {
        validateSave(SaveEnvelope(savedAtEpochMs = it.updatedAtEpochMs, snapshotId = "checkpoint", revision = 0,
            practiceProgress = listOf(it)))
    }
    val frontierView = when (val view = session.view) {
        is SessionView.Review -> view.returnTo
        SessionView.Left -> throw IllegalArgumentException("Left sessions do not commit checkpoints")
        else -> view
    }
    val exercises = session.plan
    require(exercises.size in 1..SaveBounds.MAX_EXERCISES && session.outcomes.size == exercises.size)
    val byId = practiceSet.exercises.associateBy { it.id }
    require(byId.size == practiceSet.exercises.size) { "Duplicate authored exercise" }
    require(exercises.all { entry -> byId[entry.id]?.let { it::class == entry.exercise::class } == true }) {
        "Practice plan does not resolve in set"
    }
    val outcomes = session.outcomes
    val resolved = outcomes.takeWhile { it != null }.size
    require(outcomes.drop(resolved).all { it == null }) { "Noncontiguous outcomes" }
    val (view, frontier) = when (frontierView) {
        is SessionView.Prompt -> CheckpointView.PROMPT to frontierView.index
        is SessionView.Feedback -> CheckpointView.FEEDBACK to frontierView.index
        SessionView.Complete -> CheckpointView.COMPLETE to exercises.size
        else -> throw IllegalArgumentException("Invalid practice frontier")
    }
    require((view == CheckpointView.PROMPT && resolved == frontier) ||
        (view == CheckpointView.FEEDBACK && resolved == frontier + 1) ||
        (view == CheckpointView.COMPLETE && resolved == frontier)) { "Invalid practice frontier" }
    val candidate = PracticeCheckpoint(
        setId = practiceSet.id, contentVersion = practiceSet.contentVersion,
        updatedAtEpochMs = updatedAtEpochMs, runToken = runToken,
        lastTransitionToken = transitionToken, exerciseIds = exercises.map { it.id },
        outcomes = outcomes.take(resolved).map { it!!.compact() }, frontier = frontier, view = view,
        lastCompletedAtEpochMs = if (view == CheckpointView.COMPLETE) updatedAtEpochMs else previous?.lastCompletedAtEpochMs,
        exerciseTypes = exercises.map { it.exercise.checkpointType() },
    )
    // Validate the same bounds used by the save codec before returning a usable record.
    validateSave(SaveEnvelope(savedAtEpochMs = updatedAtEpochMs, snapshotId = "checkpoint", revision = 0,
        practiceProgress = listOf(candidate)))
    if (previous != null && previous.runToken == runToken) {
        require(previous.exerciseIds == candidate.exerciseIds && previous.exerciseTypes == candidate.exerciseTypes) { "Run plan changed" }
        require(previous.view != CheckpointView.COMPLETE || candidate.view == CheckpointView.COMPLETE) {
            "Completed run cannot reopen"
        }
        if (previous.view == candidate.view && previous.frontier == candidate.frontier &&
            previous.outcomes == candidate.outcomes) return previous
        require(previous.lastTransitionToken != transitionToken) { "Reused transition token" }
    } else if (previous != null) {
        require(previous.runToken != runToken && previous.lastTransitionToken != transitionToken) { "Reused run token" }
    }
    return candidate
}

/** Resolve stable IDs against current authored material, independent of contentVersion.
 * The caller must allocate a new runtimeSessionId, distinct from every still-active callback identity.
 */
fun restorePractice(runtimeSessionId: Long, practiceSet: PracticeSet, checkpoint: PracticeCheckpoint): PracticeRestoreResult {
    if (runtimeSessionId <= 0 || checkpoint.setId != practiceSet.id) return PracticeRestoreResult.Unavailable
    try {
        validateSave(SaveEnvelope(savedAtEpochMs = checkpoint.updatedAtEpochMs, snapshotId = "checkpoint", revision = 0,
            practiceProgress = listOf(checkpoint)))
        val byId = practiceSet.exercises.associateBy { it.id }
        if (byId.size != practiceSet.exercises.size) return PracticeRestoreResult.Unavailable
        val exercises = checkpoint.exerciseIds.map { byId[it] ?: return PracticeRestoreResult.Unavailable }
        if (exercises.indices.any { exercises[it].checkpointType() != checkpoint.exerciseTypes[it] }) {
            return PracticeRestoreResult.Unavailable
        }
        val outcomes = exercises.mapIndexed { index, exercise ->
            checkpoint.outcomes.getOrNull(index)?.let(exercise::reconstruct)
        }
        val view = when (checkpoint.view) {
            CheckpointView.PROMPT -> SessionView.Prompt(checkpoint.frontier)
            CheckpointView.FEEDBACK -> SessionView.Feedback(checkpoint.frontier)
            CheckpointView.COMPLETE -> SessionView.Complete
        }
        return PracticeRestoreResult.Restored(PracticeSession.restore(
            runtimeSessionId, practiceSet.id, exercises, outcomes, view,
        ))
    } catch (_: IllegalArgumentException) {
        return PracticeRestoreResult.Unavailable
    } catch (_: NoSuchElementException) {
        // A mutable or incompatible authored exercise cannot supply its expected feedback.
        return PracticeRestoreResult.Unavailable
    }
}
