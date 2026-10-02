package com.github.nanaki_93.review

import com.github.nanaki_93.content.*
import com.github.nanaki_93.progress.*

const val REVIEW_DAY_MS = 86_400_000L
private val intervals = listOf(10 * 60_000L, REVIEW_DAY_MS, 3 * REVIEW_DAY_MS, 7 * REVIEW_DAY_MS, 14 * REVIEW_DAY_MS, 30 * REVIEW_DAY_MS)

data class ReviewCard(val item: ReviewItem, val documentId: String, val context: String,
                      val cue: String, val phrase: Phrase? = null, val exercise: Exercise? = null)
data class PlannedReview(val card: ReviewCard, val previous: ReviewItemProgress?)

/** Only encountered lessons/practice contribute explicitly authored items. Never import the legacy corpus. */
fun eligibleReviewCards(lessons: Collection<Lesson>, practices: Collection<PracticeSet>, save: SaveEnvelope): List<ReviewCard> {
    val cards = mutableListOf<ReviewCard>()
    fun add(id: String, context: String, items: List<ReviewItem>, phrases: List<Phrase>, exercises: List<Exercise>) {
        for (item in items) {
            val phrase = if (item.targetKind == ReviewTargetKind.PHRASE) phrases.firstOrNull { it.id == item.targetId } else null
            val exercise = if (item.targetKind == ReviewTargetKind.EXERCISE) exercises.firstOrNull { it.id == item.targetId } else null
            if (phrase != null || exercise != null) cards += ReviewCard(item, id, context,
                phrase?.text?.translation ?: item.skill, phrase, exercise)
        }
    }
    for (lesson in lessons) if (save.lessonProgress.any { it.lessonId == lesson.id && it.completedAtEpochMs != null } ||
        save.reviewItems.any { it.documentId == lesson.id }) add(lesson.id, lesson.situation, lesson.reviewItems, lesson.phrases, lesson.exercises)
    for (practice in practices) if (save.practiceProgress.any { it.setId == practice.id && it.lastCompletedAtEpochMs != null } ||
        save.reviewItems.any { it.documentId == practice.id }) add(practice.id, practice.description, practice.reviewItems, practice.phrases, practice.exercises)
    return cards.distinctBy { it.item.id }
}

fun reviewDueCount(cards: List<ReviewCard>, save: SaveEnvelope, now: Long): Int = cards.count { card ->
    save.reviewItems.firstOrNull { it.itemId == card.item.id && it.documentId == card.documentId }
        ?.let { (it.dueAtEpochMs ?: it.lastReviewedAtEpochMs) <= now } == true
}

fun planReview(cards: List<ReviewCard>, save: SaveEnvelope, now: Long, ahead: Boolean = false,
               limit: Int = 10, newLimit: Int = 3): List<PlannedReview> {
    require(limit in 1..10 && newLimit in 0..3 && now in 0..SaveBounds.MAX_EPOCH_MS)
    val records = save.reviewItems.associateBy { it.itemId }
    val planned = cards.distinctBy { it.item.id }.map { PlannedReview(it, records[it.item.id]?.takeIf { record -> record.documentId == it.documentId }) }
    val existing = planned.filter { it.previous != null }.sortedWith(compareBy({ it.previous!!.dueAtEpochMs ?: it.previous.lastReviewedAtEpochMs }, { it.card.item.id }))
    val due = existing.filter { (it.previous!!.dueAtEpochMs ?: it.previous.lastReviewedAtEpochMs) <= now }
    val fresh = planned.filter { it.previous == null }.take(newLimit)
    return (due + fresh + if (ahead) existing.filterNot { it in due } else emptyList()).take(limit)
}

/** UTC storage; display buckets use the offset AT the timestamp, so DST callers can pass different offsets. */
fun localReviewDay(epochMs: Long, offsetMinutesEast: Int): Long {
    require(offsetMinutesEast in -840..840)
    val local = epochMs + offsetMinutesEast * 60_000L
    return if (local >= 0) local / REVIEW_DAY_MS else (local - REVIEW_DAY_MS + 1) / REVIEW_DAY_MS
}

fun scheduleReview(card: ReviewCard, previous: ReviewItemProgress?, outcome: ReviewOutcome, now: Long, token: String): ReviewItemProgress {
    require(now in 0..SaveBounds.MAX_EPOCH_MS)
    require(previous == null || previous.itemId == card.item.id && previous.documentId == card.documentId)
    // A clock moving backwards must not move the learning history or due time before the last review.
    val timestamp = maxOf(now, previous?.lastReviewedAtEpochMs ?: now)
    val oldStep = (previous?.step ?: 0).coerceIn(0, intervals.lastIndex)
    val step = when (outcome) {
        ReviewOutcome.AGAIN -> 0
        ReviewOutcome.HARD -> maxOf(1, oldStep - 1)
        ReviewOutcome.GOOD -> minOf(intervals.lastIndex, oldStep + 1)
    }
    val interval = intervals[step]
    return ReviewItemProgress(card.item.id, card.documentId, outcome, timestamp,
        minOf(SaveBounds.MAX_EPOCH_MS, timestamp + interval), interval, step,
        minOf(SaveBounds.MAX_SCHEDULE_COUNT, (previous?.repetitions ?: 0) + 1),
        minOf(SaveBounds.MAX_SCHEDULE_COUNT, (previous?.lapses ?: 0) + if (outcome == ReviewOutcome.AGAIN) 1 else 0), token)
}

/** Compare the whole expected record. A replay after any intervening rating cannot advance the schedule. */
fun commitReview(save: SaveEnvelope, planned: PlannedReview, outcome: ReviewOutcome, now: Long, token: String): ProgressUpdate {
    val current = save.reviewItems.firstOrNull { it.itemId == planned.card.item.id }
    if (current?.lastActionToken == token) return ProgressUpdate.Unchanged(save)
    if (current != planned.previous) return ProgressUpdate.Rejected(save)
    return try { recordReview(save, scheduleReview(planned.card, current, outcome, now, token)) }
    catch (_: IllegalArgumentException) { ProgressUpdate.Rejected(save) }
}
