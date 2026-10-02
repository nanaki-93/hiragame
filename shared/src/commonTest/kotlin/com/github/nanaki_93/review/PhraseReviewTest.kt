package com.github.nanaki_93.review

import com.github.nanaki_93.content.*
import com.github.nanaki_93.progress.*
import kotlin.test.*

class PhraseReviewTest {
    private fun card(id: String) = ReviewCard(ReviewItem(id, ReviewTargetKind.PHRASE, "phrase-$id", "Recall", "meaning-to-japanese"), "lesson", "A colleague asks", "Confirm")
    private val save = SaveEnvelope(savedAtEpochMs = 100, snapshotId = "save", revision = 0)
    @Test fun successIsBoundedAndLapsePreservesRepetitions() {
        var record: ReviewItemProgress? = null
        repeat(15) { record = scheduleReview(card("item"), record, ReviewOutcome.GOOD, 100, "action-$it") }
        assertEquals(30 * REVIEW_DAY_MS, record!!.intervalMs)
        assertEquals(15, record!!.repetitions)
        val lapse = scheduleReview(card("item"), record, ReviewOutcome.AGAIN, 99, "lapse")
        assertEquals(600_000L, lapse.intervalMs); assertEquals(16, lapse.repetitions); assertEquals(1, lapse.lapses)
        assertEquals(100L, lapse.lastReviewedAtEpochMs)
        val hard = scheduleReview(card("item"), lapse, ReviewOutcome.HARD, 500, "hard")
        assertEquals(REVIEW_DAY_MS, hard.intervalMs)
    }
    @Test fun duplicateAndOldActionsNeverAdvance() {
        val plan = PlannedReview(card("item"), null)
        val first = assertIs<ProgressUpdate.Applied>(commitReview(save, plan, ReviewOutcome.GOOD, 100, "one")).snapshot
        assertIs<ProgressUpdate.Unchanged>(commitReview(first, plan, ReviewOutcome.GOOD, 200, "one"))
        assertIs<ProgressUpdate.Rejected>(commitReview(first, plan, ReviewOutcome.GOOD, 200, "two"))
        val secondPlan = plan.copy(previous = first.reviewItems.single())
        val second = assertIs<ProgressUpdate.Applied>(commitReview(first, secondPlan, ReviewOutcome.AGAIN, 300, "two")).snapshot
        assertIs<ProgressUpdate.Rejected>(commitReview(second, plan, ReviewOutcome.GOOD, 400, "one"))
        assertEquals(second, (decodeSave(encodeSave(second)) as SaveDecodeResult.Valid).snapshot)
    }
    @Test fun dueFirstNewCapAndNoDuplicates() {
        val cards = (0..20).map { card("item-$it") }
        val records = cards.take(11).map { scheduleReview(it, null, ReviewOutcome.AGAIN, 0, "action-${it.item.id}") }
        val scheduled = save.copy(reviewItems = records)
        val queue = planReview(cards + cards, scheduled, 600_000)
        assertEquals(10, queue.size); assertTrue(queue.all { it.previous != null }); assertEquals(10, queue.map { it.card.item.id }.distinct().size)
        assertEquals(3, planReview(cards, scheduled, 599_999).size)
        assertEquals(10, planReview(cards, scheduled, 599_999, ahead = true).size)
        assertEquals(11, reviewDueCount(cards, scheduled, 600_000))
    }
    @Test fun localDaysAndClockBoundaries() {
        assertEquals(0L, localReviewDay(0, 480))
        assertEquals(-1L, localReviewDay(0, -60))
        assertEquals(1L, localReviewDay(REVIEW_DAY_MS - 480 * 60_000, 480))
        assertEquals(0L, localReviewDay(REVIEW_DAY_MS - 480 * 60_000 - 1, 480))
        val end = scheduleReview(card("end"), null, ReviewOutcome.GOOD, SaveBounds.MAX_EPOCH_MS, "end")
        assertEquals(SaveBounds.MAX_EPOCH_MS, end.dueAtEpochMs)
    }

    @Test fun unavailableAndReassignedItemsRemainSavedButDoNotInflateDueCount() {
        val available = card("available")
        val moved = card("moved")
        val records = listOf(
            scheduleReview(available, null, ReviewOutcome.AGAIN, 0, "one"),
            scheduleReview(card("removed"), null, ReviewOutcome.AGAIN, 0, "two"),
            scheduleReview(moved.copy(documentId = "retired_document"), null, ReviewOutcome.AGAIN, 0, "three"),
        )
        val prior = save.copy(reviewItems = records)
        assertEquals(1, reviewDueCount(listOf(available, moved), prior, 600_000))
        assertEquals(records, prior.reviewItems)
    }
}
