package com.github.nanaki_93.lesson

import com.github.nanaki_93.content.ChoiceExercise
import com.github.nanaki_93.content.CompletionExercise
import com.github.nanaki_93.content.DialogueTurn
import com.github.nanaki_93.content.Exercise
import com.github.nanaki_93.content.Lesson
import com.github.nanaki_93.content.ProductionExercise
import com.github.nanaki_93.content.ReadingExercise
import com.github.nanaki_93.progress.LessonStage

/** An executable item has a stable checkpoint ID; stage entries have a null checkpoint. */
sealed class LessonPlanItem {
    abstract val checkpointId: String

    data class Turn(val turn: DialogueTurn) : LessonPlanItem() {
        override val checkpointId: String get() = turn.id
    }

    data class Prompt(val exercise: Exercise) : LessonPlanItem() {
        override val checkpointId: String get() = exercise.id
    }
}

/** Only task-bearing stages can be empty. Role-play still has an authored objective to reflect on. */
enum class EmptyLessonStage {
    NO_DIALOGUE_TURNS,
    NO_EXERCISES,
    ROLE_PLAY_OBJECTIVE_ONLY,
}

data class LessonStagePlan(
    val stage: LessonStage,
    val items: List<LessonPlanItem>,
    val empty: EmptyLessonStage? = null,
) {
    /** Null is the stage-entry checkpoint, including empty stages and objective-only role-play. */
    fun containsCheckpoint(checkpointId: String?): Boolean =
        checkpointId == null || items.any { it.checkpointId == checkpointId }
}

/** Situation, phrase/grammar support, role-play objective and summary copy remain on the authored lesson. */
data class LessonPlan(val lesson: Lesson, val stages: List<LessonStagePlan>) {
    fun stage(stage: LessonStage): LessonStagePlan = stages.first { it.stage == stage }
}

/** F07 uses authored order within each stage, not practice's ten-item cap or conversation-graph traversal. */
fun buildLessonPlan(lesson: Lesson): LessonPlan {
    // Lesson validates IDs on construction; defend against callers mutating an input list later.
    require(lesson.exercises.map { it.id }.distinct().size == lesson.exercises.size) {
        "Duplicate lesson exercise ID"
    }
    require(lesson.dialogue.turns.map { it.id }.distinct().size == lesson.dialogue.turns.size) {
        "Duplicate dialogue turn ID"
    }
    val turns = lesson.dialogue.turns.map { LessonPlanItem.Turn(it) }
    val understanding = mutableListOf<LessonPlanItem>()
    val guided = mutableListOf<LessonPlanItem>()
    val rolePlay = mutableListOf<LessonPlanItem>()
    lesson.exercises.forEach { exercise ->
        val destination = when (exercise) {
            is ChoiceExercise -> understanding
            is ReadingExercise, is CompletionExercise -> guided
            is ProductionExercise -> rolePlay
        }
        destination.add(LessonPlanItem.Prompt(exercise))
    }
    return LessonPlan(lesson, listOf(
        LessonStagePlan(LessonStage.SITUATION, emptyList()),
        LessonStagePlan(LessonStage.DIALOGUE, turns,
            if (turns.isEmpty()) EmptyLessonStage.NO_DIALOGUE_TURNS else null),
        LessonStagePlan(LessonStage.UNDERSTANDING, understanding,
            if (understanding.isEmpty()) EmptyLessonStage.NO_EXERCISES else null),
        LessonStagePlan(LessonStage.GUIDED_PRACTICE, guided,
            if (guided.isEmpty()) EmptyLessonStage.NO_EXERCISES else null),
        LessonStagePlan(LessonStage.ROLE_PLAY, rolePlay,
            if (rolePlay.isEmpty()) EmptyLessonStage.ROLE_PLAY_OBJECTIVE_ONLY else null),
        LessonStagePlan(LessonStage.SUMMARY, emptyList()),
    ))
}
