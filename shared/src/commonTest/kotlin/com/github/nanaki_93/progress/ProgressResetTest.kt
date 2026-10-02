package com.github.nanaki_93.progress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class ProgressResetTest {
    private fun populatedSnapshot() = SaveEnvelope(
        savedAtEpochMs = 90,
        snapshotId = "local_snapshot",
        revision = 12,
        preferences = SavePreferences(
            colorMode = SavedColorMode.DARK,
            showReadings = false,
            showTranslation = false,
            showRomaji = true,
        ),
        lessonProgress = listOf(LessonProgress(
            lessonId = "unavailable_lesson", contentVersion = 3, updatedAtEpochMs = 50,
            stage = LessonStage.SUMMARY, checkpointId = "unavailable_checkpoint", completedAtEpochMs = 50,
        )),
        practiceProgress = listOf(PracticeCheckpoint(
            setId = "unavailable_set", contentVersion = 3, updatedAtEpochMs = 60,
            runToken = "run_1", lastTransitionToken = "transition_1",
            exerciseIds = listOf("unavailable_exercise"), outcomes = listOf(CompactOutcome.CORRECT),
            frontier = 1, view = CheckpointView.COMPLETE, lastCompletedAtEpochMs = 60,
            exerciseTypes = listOf(CheckpointExerciseType.CHOICE),
        )),
        reviewItems = listOf(ReviewItemProgress(
            itemId = "unavailable_item", documentId = "unavailable_document", outcome = ReviewOutcome.GOOD,
            lastReviewedAtEpochMs = 70, dueAtEpochMs = 80, intervalMs = 10,
            step = 2, repetitions = 3, lapses = 1, lastActionToken = "action_1",
        )),
    )

    @Test fun progressOnlyClearsEveryRecordIncludingUnavailableOnesButPreservesAllPreferences() {
        val original = populatedSnapshot()
        validateSave(original) // Unknown stable IDs are valid without a content catalog.

        val reset = resetLearnerState(original, ResetScope.PROGRESS_ONLY)

        assertEquals(original.copy(lessonProgress = emptyList(), practiceProgress = emptyList(), reviewItems = emptyList()), reset)
        assertNotSame(original, reset)
        assertEquals(1, original.lessonProgress.size)
        assertEquals(1, original.practiceProgress.size)
        assertEquals(1, original.reviewItems.size)
        validateSave(reset)
    }

    @Test fun fullResetClearsAllRecordsAndRestoresEveryPreferenceDefaultWithoutChangingWriteMetadata() {
        val original = populatedSnapshot()
        validateSave(original)

        val reset = resetLearnerState(original, ResetScope.FULL_LEARNER_STATE)

        assertEquals(original.copy(
            preferences = SavePreferences(),
            lessonProgress = emptyList(), practiceProgress = emptyList(), reviewItems = emptyList(),
        ), reset)
        assertEquals(SavedColorMode.SYSTEM, reset.preferences.colorMode)
        assertEquals(SavePreferences(), reset.preferences)
        assertEquals(90L, reset.savedAtEpochMs)
        assertEquals("local_snapshot", reset.snapshotId)
        assertEquals(12L, reset.revision)
        assertEquals(SavedColorMode.DARK, original.preferences.colorMode)
        validateSave(reset)
    }
}
