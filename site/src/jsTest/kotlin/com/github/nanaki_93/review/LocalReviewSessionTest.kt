package com.github.nanaki_93.review

import com.github.nanaki_93.content.*
import com.github.nanaki_93.progress.*
import com.github.nanaki_93.storage.*
import kotlin.test.*

class LocalReviewSessionTest {
    @Test fun revealIsRequiredRatingsPersistOnceAndReloadInvalidatesSession() {
        val backing = MemoryProgressBacking()
        var serial = 0
        val owner = LocalProgressOwner(MemoryProgressStore(backing), { 100L }, { "save-${++serial}" })
        val card = ReviewCard(ReviewItem("item", ReviewTargetKind.PHRASE, "phrase", "Recall", "meaning-to-japanese"), "lesson", "Work", "Say it")
        val session = LocalReviewSession(listOf(PlannedReview(card, null)), owner, { 100L }, { "rating" })
        session.rate(0, ReviewOutcome.GOOD); assertTrue(owner.state.value.snapshot.reviewItems.isEmpty())
        session.reveal(0); session.rate(0, ReviewOutcome.GOOD); session.rate(0, ReviewOutcome.AGAIN)
        assertTrue(session.complete); assertEquals(1, owner.state.value.snapshot.reviewItems.single().repetitions)
        val restored = LocalProgressOwner(MemoryProgressStore(backing), { 100L }, { "reloaded" })
        assertEquals(owner.state.value.snapshot.reviewItems, restored.state.value.snapshot.reviewItems)
        owner.reloadSavedState(); assertTrue(session.expired)
        owner.dispose(); restored.dispose()
    }
}
