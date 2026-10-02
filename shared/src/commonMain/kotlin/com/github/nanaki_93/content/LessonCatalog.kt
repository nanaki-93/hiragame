package com.github.nanaki_93.content

import com.github.nanaki_93.progress.LessonProgress
import com.github.nanaki_93.progress.LessonStage
import com.github.nanaki_93.progress.SaveEnvelope

/** Browsing choices are transient; neither these views nor their statuses are save data. */
data class CatalogViewOptions(
    val topicId: String? = null,
    val beginnerPath: Boolean = false,
)

/** A null record means not started. Availability describes content references, not a playable session. */
data class LessonSavedStatus(
    val record: LessonProgress?,
    val documentAvailable: Boolean,
    /** Null when there is no saved place; false when the document or stage item cannot be resolved. */
    val checkpointAvailable: Boolean?,
) {
    val completedAtEpochMs: Long? get() = record?.completedAtEpochMs
    val isCompleted: Boolean get() = completedAtEpochMs != null
    val savedStage: LessonStage? get() = record?.stage
}

data class LessonCard(
    val lessonId: String,
    val topicId: String,
    val title: String,
    val situation: String,
    val communicationGoal: String,
    val difficulty: AdvisoryDifficulty,
    val durationMinutes: Int,
    val prerequisiteLessonIds: List<String>,
    val status: LessonSavedStatus,
)

data class LessonTopic(val topic: Topic, val cards: List<LessonCard>)

data class LessonCatalogView(
    /** All workplace topics, regardless of the active filter, in catalog topic order. */
    val topics: List<LessonTopic>,
    /** Matching cards in catalog entry order (which can differ from topic order). */
    val cards: List<LessonCard>,
    val selectedTopicId: String?,
    val invalidTopicFilter: Boolean,
    val beginnerPath: Boolean,
    /** Saved IDs not present among the resolved catalog lessons; never removed from the save. */
    val unavailableSavedLessons: List<LessonSavedStatus>,
)

private fun checkpointPresent(lesson: Lesson, record: LessonProgress): Boolean {
    val id = record.checkpointId ?: return true // A stage boundary has no item to resolve.
    return when (record.stage) {
        LessonStage.SITUATION, LessonStage.SUMMARY -> false
        LessonStage.DIALOGUE -> lesson.dialogue.turns.any { it.id == id }
        LessonStage.UNDERSTANDING, LessonStage.GUIDED_PRACTICE -> lesson.exercises.any { it.id == id }
        LessonStage.ROLE_PLAY -> lesson.exercises.any { it is ProductionExercise && it.id == id } ||
            lesson.conversationGraph?.nodes?.any { it.id == id } == true
    }
}

/** Projection only: no save validation, repair, visit, completion, or persistence occurs here. */
fun projectLessonCatalog(
    catalog: ContentCatalog,
    lessons: Map<String, Lesson>,
    snapshot: SaveEnvelope,
    options: CatalogViewOptions = CatalogViewOptions(),
): LessonCatalogView {
    val records = snapshot.lessonProgress.associateBy { it.lessonId }
    val lessonEntries = catalog.entries.filter { it.kind == DocumentKind.LESSON }
    val cards = lessonEntries.mapNotNull { entry ->
        val lesson = lessons[entry.id]?.takeIf { it.id == entry.id && it.topicId == entry.topicId }
            ?: return@mapNotNull null
        val record = records[entry.id]
        LessonCard(
            entry.id, entry.topicId, lesson.title, lesson.situation, lesson.communicationGoal,
            lesson.difficulty, lesson.durationMinutes, lesson.prerequisiteLessonIds,
            LessonSavedStatus(record, documentAvailable = true,
                checkpointAvailable = record?.let { checkpointPresent(lesson, it) }),
        )
    }
    val topicIds = lessonEntries.map { it.topicId }.toSet()
    val topics = catalog.topics.filter { it.id in topicIds }.map { topic ->
        LessonTopic(topic, cards.filter { it.topicId == topic.id })
    }
    val availableIds = cards.map { it.lessonId }.toSet()
    return LessonCatalogView(
        topics = topics,
        cards = cards.filter { options.topicId == null || it.topicId == options.topicId },
        selectedTopicId = options.topicId,
        invalidTopicFilter = options.topicId != null && topics.none { it.topic.id == options.topicId },
        beginnerPath = options.beginnerPath,
        unavailableSavedLessons = snapshot.lessonProgress.filter { it.lessonId !in availableIds }.map {
            LessonSavedStatus(it, documentAvailable = false, checkpointAvailable = false)
        },
    )
}
