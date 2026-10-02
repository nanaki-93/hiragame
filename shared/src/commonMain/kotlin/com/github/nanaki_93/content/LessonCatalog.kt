package com.github.nanaki_93.content

import com.github.nanaki_93.lesson.LessonCheckpointResolution
import com.github.nanaki_93.lesson.resolveLessonCheckpoint
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
    /** Suggested starter order, shown only when guidance is enabled; never restricts the catalog. */
    val beginnerPathCards: List<LessonCard>,
    val recommendation: LessonRecommendation?,
    /** Saved IDs not present among the resolved catalog lessons; never removed from the save. */
    val unavailableSavedLessons: List<LessonSavedStatus>,
)

data class LessonRecommendation(
    val lessonId: String,
    val reason: String,
    val revisit: Boolean,
)

// The preserved short meeting-time seed is supplemental, not one of the five ordered starters.
private const val SUPPLEMENTAL_SEED_ID = "lesson-confirm-meeting-time"

private fun recommendLesson(cards: List<LessonCard>, snapshot: SaveEnvelope): LessonRecommendation? {
    val unfinished = cards.filterNot { it.status.isCompleted }
    val saved = unfinished.filter { it.status.record != null && it.status.checkpointAvailable == true }
        .maxByOrNull { it.status.record!!.updatedAtEpochMs }
    if (saved != null) return LessonRecommendation(
        saved.lessonId, "Suggested because a saved place is available; browsing does not change progress.", false,
    )
    val aids = snapshot.preferences
    if (aids.showReadings || aids.showTranslation || aids.showRomaji) {
        val beginner = unfinished.firstOrNull {
            it.difficulty == AdvisoryDifficulty.BEGINNER && it.lessonId != SUPPLEMENTAL_SEED_ID
        }
        if (beginner != null) return LessonRecommendation(
            beginner.lessonId, "Suggested because reading, translation, or romaji support is enabled.", false,
        )
    }
    val next = unfinished.firstOrNull()
    if (next != null) return LessonRecommendation(next.lessonId, "Suggested as the next lesson in catalog order.", false)
    return cards.firstOrNull()?.let {
        LessonRecommendation(it.lessonId, "Revisit suggestion: all available lessons are marked completed.", true)
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
                checkpointAvailable = record?.let {
                    resolveLessonCheckpoint(lesson, it) is LessonCheckpointResolution.Available
                }),
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
        beginnerPathCards = if (options.beginnerPath) cards.filter {
            it.difficulty == AdvisoryDifficulty.BEGINNER && it.lessonId != SUPPLEMENTAL_SEED_ID
        }.take(5) else emptyList(), // Keep the starter plan short as the optional curriculum grows.
        recommendation = recommendLesson(cards, snapshot),
        unavailableSavedLessons = snapshot.lessonProgress.filter { it.lessonId !in availableIds }.map {
            LessonSavedStatus(it, documentAvailable = false, checkpointAvailable = false)
        },
    )
}
