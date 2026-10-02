package com.github.nanaki_93.progress

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Versioned learner data only. Lists carry stable IDs; they are not catalog indexes. */
@Serializable
@SerialName("saveEnvelope")
data class SaveEnvelope(
    @SerialName("schemaVersion") val schemaVersion: Int = 1,
    @SerialName("savedAtEpochMs") val savedAtEpochMs: Long,
    @SerialName("snapshotId") val snapshotId: String,
    @SerialName("revision") val revision: Long,
    @SerialName("preferences") val preferences: SavePreferences = SavePreferences(),
    @SerialName("lessonProgress") val lessonProgress: List<LessonProgress> = emptyList(),
    @SerialName("practiceProgress") val practiceProgress: List<PracticeCheckpoint> = emptyList(),
    @SerialName("reviewItems") val reviewItems: List<ReviewItemProgress> = emptyList(),
)

@Serializable
@SerialName("savePreferences")
data class SavePreferences(
    @SerialName("colorMode") val colorMode: SavedColorMode = SavedColorMode.SYSTEM,
    @SerialName("showReadings") val showReadings: Boolean = true,
    @SerialName("showTranslation") val showTranslation: Boolean = true,
    @SerialName("showRomaji") val showRomaji: Boolean = false,
)

@Serializable
enum class SavedColorMode {
    @SerialName("system") SYSTEM,
    @SerialName("light") LIGHT,
    @SerialName("dark") DARK,
}

@Serializable
enum class LessonStage {
    @SerialName("situation") SITUATION,
    @SerialName("dialogue") DIALOGUE,
    @SerialName("understanding") UNDERSTANDING,
    @SerialName("guidedPractice") GUIDED_PRACTICE,
    @SerialName("rolePlay") ROLE_PLAY,
    @SerialName("summary") SUMMARY,
}

@Serializable
@SerialName("lessonProgressRecord")
data class LessonProgress(
    @SerialName("lessonId") val lessonId: String,
    @SerialName("contentVersion") val contentVersion: Int,
    @SerialName("updatedAtEpochMs") val updatedAtEpochMs: Long,
    @SerialName("stage") val stage: LessonStage,
    @SerialName("checkpointId") val checkpointId: String? = null,
    @SerialName("completedAtEpochMs") val completedAtEpochMs: Long? = null,
)

@Serializable
enum class ReviewOutcome {
    @SerialName("again") AGAIN,
    @SerialName("hard") HARD,
    @SerialName("good") GOOD,
}

/** Scheduling fields describe stored state, not a scheduling algorithm. */
@Serializable
@SerialName("reviewItemProgress")
data class ReviewItemProgress(
    @SerialName("itemId") val itemId: String,
    @SerialName("documentId") val documentId: String,
    @SerialName("outcome") val outcome: ReviewOutcome,
    @SerialName("lastReviewedAtEpochMs") val lastReviewedAtEpochMs: Long,
    @SerialName("dueAtEpochMs") val dueAtEpochMs: Long? = null,
    @SerialName("intervalMs") val intervalMs: Long? = null,
    @SerialName("step") val step: Int? = null,
    @SerialName("repetitions") val repetitions: Int? = null,
    @SerialName("lapses") val lapses: Int? = null,
    @SerialName("lastActionToken") val lastActionToken: String,
)

@Serializable
enum class CheckpointView {
    @SerialName("prompt") PROMPT,
    @SerialName("feedback") FEEDBACK,
    @SerialName("complete") COMPLETE,
}

@Serializable
enum class CompactOutcome {
    @SerialName("correct") CORRECT,
    @SerialName("incorrect") INCORRECT,
    @SerialName("skipped") SKIPPED,
    @SerialName("revealed") REVEALED,
    @SerialName("selfMetCriteria") SELF_MET_CRITERIA,
    @SerialName("selfNeedsPractice") SELF_NEEDS_PRACTICE,
}

@Serializable
enum class CheckpointExerciseType {
    @SerialName("choice") CHOICE,
    @SerialName("reading") READING,
    @SerialName("completion") COMPLETION,
    @SerialName("production") PRODUCTION,
}

/** A slot is identified by its position in exerciseIds; no responses or authored feedback. */
@Serializable
@SerialName("practiceCheckpoint")
data class PracticeCheckpoint(
    @SerialName("setId") val setId: String,
    @SerialName("contentVersion") val contentVersion: Int,
    @SerialName("updatedAtEpochMs") val updatedAtEpochMs: Long,
    @SerialName("runToken") val runToken: String,
    @SerialName("lastTransitionToken") val lastTransitionToken: String,
    @SerialName("exerciseIds") val exerciseIds: List<String>,
    @SerialName("outcomes") val outcomes: List<CompactOutcome>,
    /** Index of the active item, or exerciseIds.size for completion. */
    @SerialName("frontier") val frontier: Int,
    @SerialName("view") val view: CheckpointView,
    @SerialName("lastCompletedAtEpochMs") val lastCompletedAtEpochMs: Long? = null,
    @SerialName("exerciseTypes") val exerciseTypes: List<CheckpointExerciseType>,
)
