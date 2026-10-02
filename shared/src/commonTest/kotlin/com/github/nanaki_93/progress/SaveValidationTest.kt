package com.github.nanaki_93.progress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.Json

class SaveValidationTest {
    private fun valid() = SaveEnvelope(
        savedAtEpochMs = SaveBounds.MAX_EPOCH_MS,
        snapshotId = "S".repeat(128),
        revision = SaveBounds.MAX_SAFE_INTEGER,
        lessonProgress = listOf(LessonProgress("unknown_lesson-1", 1, 0, LessonStage.GUIDED_PRACTICE, "unlisted_item", 0)),
        practiceProgress = listOf(PracticeCheckpoint(
            setId = "unknown_set", contentVersion = 1, updatedAtEpochMs = SaveBounds.MAX_EPOCH_MS,
            runToken = "R".repeat(128), lastTransitionToken = "T".repeat(128),
            exerciseIds = (0 until 10).map { "missing_$it" },
            outcomes = List(10) { CompactOutcome.SELF_NEEDS_PRACTICE },
            frontier = 10, view = CheckpointView.COMPLETE, lastCompletedAtEpochMs = SaveBounds.MAX_EPOCH_MS,
            exerciseTypes = List(10) { CheckpointExerciseType.PRODUCTION },
        )),
        reviewItems = listOf(ReviewItemProgress(
            "unlisted_review", "unlisted_doc", ReviewOutcome.AGAIN, 0,
            dueAtEpochMs = SaveBounds.MAX_EPOCH_MS, intervalMs = SaveBounds.MAX_EPOCH_MS,
            step = SaveBounds.MAX_SCHEDULE_COUNT, repetitions = SaveBounds.MAX_SCHEDULE_COUNT,
            lapses = SaveBounds.MAX_SCHEDULE_COUNT, lastActionToken = "A".repeat(128),
        )),
    )

    private fun rejected(snapshot: SaveEnvelope) {
        assertFailsWith<IllegalArgumentException> { validateSave(snapshot) }
    }

    @Test fun unknownIdsAndInclusiveBoundsSurviveValidationAndSerialization() {
        val snapshot = valid()
        validateSave(snapshot)
        val json = Json.encodeToString(SaveEnvelope.serializer(), snapshot)
        assertEquals(snapshot, Json.decodeFromString(SaveEnvelope.serializer(), json))
        assertEquals("unknown_set", snapshot.practiceProgress.single().setId)
        // The wire format has no response, feedback, catalog, or history slot.
        listOf("response", "answer", "feedback", "history", "transcript").forEach {
            kotlin.test.assertFalse(json.contains("\"$it\""))
        }
    }

    @Test fun envelopeAndRecordLimits() {
        val s = valid()
        rejected(s.copy(schemaVersion = 0))
        rejected(s.copy(savedAtEpochMs = -1))
        rejected(s.copy(savedAtEpochMs = SaveBounds.MAX_EPOCH_MS + 1))
        rejected(s.copy(revision = -1))
        rejected(s.copy(revision = SaveBounds.MAX_SAFE_INTEGER + 1))
        rejected(s.copy(snapshotId = "a".repeat(129)))
        rejected(s.copy(snapshotId = "bad token"))
        rejected(s.copy(lessonProgress = List(1001) { s.lessonProgress.single().copy(lessonId = "l$it") }, practiceProgress = emptyList()))
        rejected(s.copy(lessonProgress = List(1000) { s.lessonProgress.single().copy(lessonId = "l$it") }))
        rejected(s.copy(reviewItems = List(5001) { s.reviewItems.single().copy(itemId = "r$it") }))
        validateSave(s.copy(lessonProgress = List(999) { s.lessonProgress.single().copy(lessonId = "l$it") }))
        validateSave(s.copy(reviewItems = List(5000) { s.reviewItems.single().copy(itemId = "r$it") }))
        rejected(s.copy(lessonProgress = listOf(s.lessonProgress.single(), s.lessonProgress.single())))
        rejected(s.copy(practiceProgress = listOf(s.practiceProgress.single(), s.practiceProgress.single())))
        rejected(s.copy(reviewItems = listOf(s.reviewItems.single(), s.reviewItems.single())))
    }

    @Test fun lessonAndReviewFieldsAreCheckedWithoutCatalog() {
        val s = valid()
        val lesson = s.lessonProgress.single()
        rejected(s.copy(lessonProgress = listOf(lesson.copy(lessonId = "../bad"))))
        rejected(s.copy(lessonProgress = listOf(lesson.copy(checkpointId = ""))))
        rejected(s.copy(lessonProgress = listOf(lesson.copy(contentVersion = 0))))
        rejected(s.copy(lessonProgress = listOf(lesson.copy(updatedAtEpochMs = -1))))
        rejected(s.copy(lessonProgress = listOf(lesson.copy(completedAtEpochMs = SaveBounds.MAX_EPOCH_MS + 1))))
        val review = s.reviewItems.single()
        rejected(s.copy(reviewItems = listOf(review.copy(documentId = "bad.id"))))
        rejected(s.copy(reviewItems = listOf(review.copy(itemId = ""))))
        rejected(s.copy(reviewItems = listOf(review.copy(lastActionToken = "a".repeat(129)))))
        rejected(s.copy(reviewItems = listOf(review.copy(lastReviewedAtEpochMs = -1))))
        rejected(s.copy(reviewItems = listOf(review.copy(dueAtEpochMs = -1))))
        rejected(s.copy(reviewItems = listOf(review.copy(intervalMs = SaveBounds.MAX_EPOCH_MS + 1))))
        rejected(s.copy(reviewItems = listOf(review.copy(step = -1))))
        rejected(s.copy(reviewItems = listOf(review.copy(repetitions = SaveBounds.MAX_SCHEDULE_COUNT + 1))))
        rejected(s.copy(reviewItems = listOf(review.copy(lapses = -1))))
    }

    @Test fun checkpointRequiresUniqueOrderedIdsAndContiguousCommittedOutcomes() {
        val s = valid()
        val p = s.practiceProgress.single()
        fun bad(record: PracticeCheckpoint) = rejected(s.copy(practiceProgress = listOf(record)))
        bad(p.copy(setId = "bad/set"))
        bad(p.copy(contentVersion = 0))
        bad(p.copy(updatedAtEpochMs = -1))
        bad(p.copy(runToken = ""))
        bad(p.copy(lastTransitionToken = "bad token"))
        bad(p.copy(exerciseIds = emptyList(), outcomes = emptyList(), frontier = 0))
        bad(p.copy(exerciseIds = List(11) { "id$it" }, outcomes = List(11) { CompactOutcome.CORRECT }, frontier = 11))
        bad(p.copy(exerciseTypes = emptyList()))
        bad(p.copy(outcomes = List(10) { CompactOutcome.CORRECT }))
        bad(p.copy(exerciseIds = List(10) { "same" }))
        bad(p.copy(exerciseIds = listOf("bad.id") + p.exerciseIds.drop(1)))
        bad(p.copy(outcomes = emptyList()))
        bad(p.copy(frontier = -1))
        bad(p.copy(lastCompletedAtEpochMs = null))
        bad(p.copy(lastCompletedAtEpochMs = -1))
        bad(p.copy(view = CheckpointView.PROMPT, frontier = 10))
        bad(p.copy(view = CheckpointView.FEEDBACK, frontier = 9, outcomes = List(9) { CompactOutcome.CORRECT }))
        val first = p.copy(view = CheckpointView.PROMPT, frontier = 0, outcomes = emptyList(), lastCompletedAtEpochMs = null)
        validateSave(s.copy(practiceProgress = listOf(first)))
        validateSave(s.copy(practiceProgress = listOf(first.copy(
            view = CheckpointView.FEEDBACK, outcomes = listOf(CompactOutcome.REVEALED),
        ))))
        // Completion from a previous run may remain while a fresh run is at its prompt.
        validateSave(s.copy(practiceProgress = listOf(first.copy(lastCompletedAtEpochMs = 0))))
    }
}
