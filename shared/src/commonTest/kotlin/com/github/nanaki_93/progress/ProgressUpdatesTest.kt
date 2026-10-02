package com.github.nanaki_93.progress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProgressUpdatesTest {
    private fun baseline() = SaveEnvelope(savedAtEpochMs = 10, snapshotId = "snapshot", revision = 0,
        lessonProgress = listOf(LessonProgress("lostLesson", 1, 8, LessonStage.DIALOGUE, "lostTurn")),
        practiceProgress = listOf(PracticeCheckpoint(
            "lostSet", 1, 8, "run", "transition", listOf("lostExercise"), emptyList(),
            0, CheckpointView.PROMPT, exerciseTypes = listOf(CheckpointExerciseType.CHOICE),
        )),
        reviewItems = listOf(ReviewItemProgress("lostReview", "lostDocument", ReviewOutcome.AGAIN, 8,
            lastActionToken = "old")),
    )

    private fun applied(result: ProgressUpdate): SaveEnvelope = assertIs<ProgressUpdate.Applied>(result).snapshot

    @Test fun finishIsAtomicIdempotentAndReplayPreservesFirstCompletionAndOtherRecords() {
        val initial = baseline()
        val visited = applied(visitLesson(initial, "newLesson", 2, LessonStage.SITUATION, null, 11))
        val summary = applied(visitLesson(visited, "newLesson", 2, LessonStage.SUMMARY, null, 12))
        assertEquals(null, summary.lessonProgress.last().completedAtEpochMs) // Entering Summary is not Finish.
        val completed = applied(completeLesson(summary, "newLesson", 2, 13))
        assertEquals(LessonProgress("newLesson", 2, 13, LessonStage.SUMMARY, null, 13),
            completed.lessonProgress.last())
        assertEquals(completed, assertIs<ProgressUpdate.Unchanged>(
            completeLesson(completed, "newLesson", 2, 14)).snapshot)
        assertEquals(completed, assertIs<ProgressUpdate.Rejected>(
            completeLesson(completed, "newLesson", 2, -1)).snapshot)
        assertEquals(completed, assertIs<ProgressUpdate.Rejected>(
            completeLesson(completed, "newLesson", 0, 14)).snapshot)

        val revisited = applied(visitLesson(completed, "newLesson", 3, LessonStage.DIALOGUE, "turn_2", 15))
        assertEquals(13L, revisited.lessonProgress.last().completedAtEpochMs)
        assertEquals("turn_2", revisited.lessonProgress.last().checkpointId)
        val replayFinished = applied(completeLesson(revisited, "newLesson", 3, 17))
        assertEquals(LessonProgress("newLesson", 3, 17, LessonStage.SUMMARY, null, 13),
            replayFinished.lessonProgress.last())
        assertEquals(replayFinished, assertIs<ProgressUpdate.Unchanged>(
            completeLesson(replayFinished, "newLesson", 3, 18)).snapshot)
        val enteredSummaryAgain = applied(visitLesson(
            applied(visitLesson(replayFinished, "newLesson", 3, LessonStage.DIALOGUE, "turn_2", 19)),
            "newLesson", 3, LessonStage.SUMMARY, null, 20))
        assertEquals(13L, enteredSummaryAgain.lessonProgress.last().completedAtEpochMs)
        assertEquals(enteredSummaryAgain, assertIs<ProgressUpdate.Unchanged>(
            completeLesson(enteredSummaryAgain, "newLesson", 3, 21)).snapshot)
        assertEquals(initial.lessonProgress.single(), replayFinished.lessonProgress.first())
        assertEquals(initial.practiceProgress, replayFinished.practiceProgress)
        assertEquals(initial.reviewItems, replayFinished.reviewItems)
        assertEquals(initial.preferences, replayFinished.preferences)
        assertEquals(initial.snapshotId, replayFinished.snapshotId)
        assertEquals(initial.revision, replayFinished.revision)
        assertEquals(initial.savedAtEpochMs, replayFinished.savedAtEpochMs)
        assertEquals(replayFinished, assertIs<SaveDecodeResult.Valid>(decodeSave(encodeSave(replayFinished))).snapshot)
    }

    @Test fun finishFromOldCheckpointIsOneValidatedChangeAndCannotLeaveAnOldPlace() {
        val initial = baseline()
        val oldPlace = applied(visitLesson(initial, "newLesson", 2, LessonStage.GUIDED_PRACTICE, "exercise_1", 11))
        val finished = applied(completeLesson(oldPlace, "newLesson", 3, 12))
        assertEquals(LessonProgress("newLesson", 3, 12, LessonStage.SUMMARY, null, 12),
            finished.lessonProgress.last())
        assertEquals(initial.reviewItems, finished.reviewItems)
        assertEquals(initial.practiceProgress, finished.practiceProgress)
    }

    @Test fun reviewValuesAreExplicitAndDuplicateTokensDoNotApplyAgain() {
        val initial = baseline()
        val review = ReviewItemProgress("newReview", "unlistedDoc", ReviewOutcome.HARD, 12,
            dueAtEpochMs = 20, intervalMs = 8, step = 1, repetitions = 2, lapses = 3,
            lastActionToken = "action_1")
        val updated = applied(recordReview(initial, review))
        val next = applied(recordReview(updated, review.copy(outcome = ReviewOutcome.GOOD,
            lastActionToken = "action_2", dueAtEpochMs = null)))
        assertEquals(ReviewOutcome.GOOD, next.reviewItems.last().outcome)
        assertEquals(null, next.reviewItems.last().dueAtEpochMs)
        assertEquals(initial.reviewItems, next.reviewItems.take(1))
        assertEquals(initial.lessonProgress, next.lessonProgress)
        assertEquals(initial.practiceProgress, next.practiceProgress)
        assertIs<ProgressUpdate.Unchanged>(recordReview(next, next.reviewItems.last()))
        assertEquals(next, assertIs<ProgressUpdate.Rejected>(recordReview(next,
            next.reviewItems.last().copy(outcome = ReviewOutcome.AGAIN))).snapshot)
        assertEquals(next, assertIs<SaveDecodeResult.Valid>(decodeSave(encodeSave(next))).snapshot)
    }

    @Test fun noOpPreferencesAndUnrelatedEditsKeepUnknownContent() {
        val initial = baseline()
        assertIs<ProgressUpdate.Unchanged>(changePreferences(initial, initial.preferences))
        val changed = applied(changePreferences(initial, initial.preferences.copy(colorMode = SavedColorMode.DARK)))
        assertEquals(initial.lessonProgress, changed.lessonProgress)
        assertEquals(initial.practiceProgress, changed.practiceProgress)
        assertEquals(initial.reviewItems, changed.reviewItems)
        assertEquals(initial.snapshotId, changed.snapshotId)
        assertIs<ProgressUpdate.Unchanged>(changePreferences(changed, changed.preferences))
    }

    @Test fun availabilityDoesNotModifyOrDiscardUnresolvedRecords() {
        val snapshot = baseline()
        val lesson = snapshot.lessonProgress.single()
        val review = snapshot.reviewItems.single()
        assertEquals(ProgressAvailability.DOCUMENT_UNAVAILABLE,
            lessonAvailability(lesson, emptyMap()))
        assertEquals(ProgressAvailability.CHECKPOINT_UNAVAILABLE,
            lessonAvailability(lesson, mapOf("lostLesson" to emptySet())))
        assertEquals(ProgressAvailability.AVAILABLE,
            lessonAvailability(lesson, mapOf("lostLesson" to setOf("lostTurn"))))
        assertEquals(ProgressAvailability.AVAILABLE,
            lessonAvailability(lesson.copy(checkpointId = null), mapOf("lostLesson" to emptySet())))
        assertEquals(ProgressAvailability.DOCUMENT_UNAVAILABLE,
            reviewAvailability(review, emptyMap()))
        assertEquals(ProgressAvailability.CHECKPOINT_UNAVAILABLE,
            reviewAvailability(review, mapOf("lostDocument" to emptySet())))
        assertEquals(ProgressAvailability.AVAILABLE,
            reviewAvailability(review, mapOf("lostDocument" to setOf("lostReview"))))
        val practice = snapshot.practiceProgress.single()
        assertEquals(ProgressAvailability.DOCUMENT_UNAVAILABLE,
            practiceAvailability(practice, emptyMap()))
        assertEquals(ProgressAvailability.CHECKPOINT_UNAVAILABLE,
            practiceAvailability(practice, mapOf("lostSet" to emptySet())))
        assertEquals(ProgressAvailability.AVAILABLE,
            practiceAvailability(practice, mapOf("lostSet" to setOf("lostExercise"))))
        assertEquals(snapshot, baseline())
    }

    @Test fun childIdsInOtherDocumentsCannotResolveSavedProgress() {
        val snapshot = baseline()
        val lesson = snapshot.lessonProgress.single()
        val review = snapshot.reviewItems.single()
        val practice = snapshot.practiceProgress.single()
        val otherOnlyLessons = mapOf("lostLesson" to emptySet<String>(), "otherLesson" to setOf("lostTurn"))
        val otherOnlyReviews = mapOf("lostDocument" to emptySet<String>(), "otherDocument" to setOf("lostReview"))
        val otherOnlyExercises = mapOf("lostSet" to emptySet<String>(), "otherSet" to setOf("lostExercise"))
        assertEquals(ProgressAvailability.CHECKPOINT_UNAVAILABLE, lessonAvailability(lesson, otherOnlyLessons))
        assertEquals(ProgressAvailability.CHECKPOINT_UNAVAILABLE, reviewAvailability(review, otherOnlyReviews))
        assertEquals(ProgressAvailability.CHECKPOINT_UNAVAILABLE, practiceAvailability(practice, otherOnlyExercises))

        // A second unresolved exercise must not be masked by an exercise from another set.
        val twoExercises = practice.copy(exerciseIds = listOf("lostExercise", "secondExercise"))
        val mixedSets = mapOf("lostSet" to setOf("lostExercise"), "otherSet" to setOf("secondExercise"))
        assertEquals(ProgressAvailability.CHECKPOINT_UNAVAILABLE, practiceAvailability(twoExercises, mixedSets))
        assertEquals(ProgressAvailability.DOCUMENT_UNAVAILABLE,
            lessonAvailability(lesson, mapOf("otherLesson" to setOf("lostTurn"))))
        assertEquals(ProgressAvailability.DOCUMENT_UNAVAILABLE,
            reviewAvailability(review, mapOf("otherDocument" to setOf("lostReview"))))
        assertEquals(ProgressAvailability.DOCUMENT_UNAVAILABLE,
            practiceAvailability(practice, mapOf("otherSet" to setOf("lostExercise"))))

        assertEquals(ProgressAvailability.AVAILABLE,
            lessonAvailability(lesson, otherOnlyLessons + ("lostLesson" to setOf("lostTurn"))))
        assertEquals(ProgressAvailability.AVAILABLE,
            reviewAvailability(review, otherOnlyReviews + ("lostDocument" to setOf("lostReview"))))
        assertEquals(ProgressAvailability.AVAILABLE,
            practiceAvailability(twoExercises, mixedSets + ("lostSet" to setOf("lostExercise", "secondExercise"))))
        assertEquals(snapshot, baseline())
    }

    @Test fun invalidMutationsLeavePreviousValidSnapshotIntact() {
        val initial = baseline()
        val attempts = listOf(
            visitLesson(initial, "bad/id", 1, LessonStage.SUMMARY, null, 11),
            visitLesson(initial, "newLesson", 0, LessonStage.SUMMARY, null, 11),
            visitLesson(initial, "lostLesson", 1, LessonStage.SUMMARY, "bad.id", 11),
            visitLesson(initial, "lostLesson", 1, LessonStage.SUMMARY, null, -1),
            completeLesson(initial, "notEncountered", 1, 11),
            completeLesson(initial, "lostLesson", 0, 11),
            completeLesson(initial, "lostLesson", 1, -1),
            completeLesson(initial, "lostLesson", 1, SaveBounds.MAX_EPOCH_MS + 1),
            recordReview(initial, initial.reviewItems.single().copy(itemId = "bad/id", lastActionToken = "new")),
            recordReview(initial, initial.reviewItems.single().copy(step = -1, lastActionToken = "new")),
        )
        attempts.forEach { assertEquals(initial, assertIs<ProgressUpdate.Rejected>(it).snapshot) }
        assertEquals(initial, assertIs<SaveDecodeResult.Valid>(decodeSave(encodeSave(initial))).snapshot)
    }

    @Test fun combinedRecordAndWireByteLimitsRejectWithoutPruning() {
        val initial = baseline().copy(lessonProgress = (0 until 999).map {
            LessonProgress("lesson$it", 1, 0, LessonStage.SITUATION)
        }) // 999 lessons + one practice checkpoint
        validateSave(initial)
        assertEquals(initial, assertIs<ProgressUpdate.Rejected>(visitLesson(
            initial, "extra", 1, LessonStage.DIALOGUE, null, 11,
        )).snapshot)
        val maxReviews = baseline().copy(reviewItems = (0 until SaveBounds.MAX_REVIEW_RECORDS).map {
            ReviewItemProgress("r$it", "doc", ReviewOutcome.AGAIN, 0, lastActionToken = "token")
        })
        encodeSave(maxReviews)
        assertEquals(maxReviews, assertIs<ProgressUpdate.Rejected>(recordReview(maxReviews,
            ReviewItemProgress("extra", "doc", ReviewOutcome.HARD, 11, lastActionToken = "next"),
        )).snapshot)
        // Find a valid 4,999-item snapshot close to the wire ceiling; the last
        // structurally valid review item then pushes it past the byte limit.
        fun padded(count: Int) = baseline().copy(reviewItems = (0 until 4999).map {
            ReviewItemProgress("r$it", if (it < count) "d".repeat(128) else "d",
                ReviewOutcome.GOOD, 0, lastActionToken = "t".repeat(128))
        })
        var low = 0
        var high = 4999
        while (low < high) {
            val mid = (low + high + 1) / 2
            val fits = try { encodeSave(padded(mid)); true } catch (_: SaveEncodeException) { false }
            if (fits) low = mid else high = mid - 1
        }
        val nearLimit = padded(low)
        encodeSave(nearLimit)
        val extra = ReviewItemProgress("last", "d".repeat(128), ReviewOutcome.GOOD, 0,
            lastActionToken = "t".repeat(128))
        assertEquals(nearLimit, assertIs<ProgressUpdate.Rejected>(recordReview(nearLimit, extra)).snapshot)
    }
}
