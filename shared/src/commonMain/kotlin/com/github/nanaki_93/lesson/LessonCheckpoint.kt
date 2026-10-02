package com.github.nanaki_93.lesson

import com.github.nanaki_93.content.Lesson
import com.github.nanaki_93.progress.LessonProgress
import com.github.nanaki_93.progress.LessonStage

/** Resolution is a read-only interpretation of a saved place, never a save repair. */
sealed interface LessonCheckpointResolution {
    data object NoRecord : LessonCheckpointResolution

    /** A null item means entry to the named stage (also valid for empty stages). */
    data class Available(
        val record: LessonProgress,
        val stage: LessonStagePlan,
        val item: LessonPlanItem?,
    ) : LessonCheckpointResolution

    data class Incompatible(
        val record: LessonProgress,
        val reason: LessonCheckpointMismatch,
    ) : LessonCheckpointResolution
}

enum class LessonCheckpointMismatch {
    DIFFERENT_LESSON,
    MOVED_TO_ANOTHER_STAGE,
    UNSUPPORTED_GRAPH_NODE,
    REMOVED_ITEM,
}

/** Only IDs executable at the recorded stage can resume; content version is not a compatibility gate. */
fun resolveLessonCheckpoint(
    lesson: Lesson,
    record: LessonProgress?,
): LessonCheckpointResolution {
    if (record == null) return LessonCheckpointResolution.NoRecord
    if (record.lessonId != lesson.id) return LessonCheckpointResolution.Incompatible(
        record, LessonCheckpointMismatch.DIFFERENT_LESSON,
    )
    val plan = buildLessonPlan(lesson)
    val stage = plan.stage(record.stage)
    val id = record.checkpointId
    if (id == null) return LessonCheckpointResolution.Available(record, stage, null)
    val item = stage.items.firstOrNull { it.checkpointId == id }
    if (item != null) return LessonCheckpointResolution.Available(record, stage, item)
    val reason = when {
        plan.stages.any { it.stage != record.stage && it.items.any { item -> item.checkpointId == id } } ->
            LessonCheckpointMismatch.MOVED_TO_ANOTHER_STAGE
        lesson.conversationGraph?.nodes?.any { it.id == id } == true ->
            LessonCheckpointMismatch.UNSUPPORTED_GRAPH_NODE
        else -> LessonCheckpointMismatch.REMOVED_ITEM
    }
    return LessonCheckpointResolution.Incompatible(record, reason)
}
