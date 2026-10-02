package com.github.nanaki_93.review

import com.github.nanaki_93.progress.ReviewOutcome
import com.github.nanaki_93.storage.*

/** The owner handles persistence and conflicts; this session owns only recall/reveal and commit identity. */
class LocalReviewSession(
    val plan: List<PlannedReview>, private val owner: LocalProgressOwner,
    private val now: () -> Long = { kotlin.js.Date.now().toLong() },
    private val token: () -> String = { "review-${kotlin.js.Date.now().toLong()}-${kotlin.random.Random.nextInt(1, Int.MAX_VALUE)}" },
) {
    private val generation = owner.generation
    var index = 0; private set
    var revealed = false; private set
    var error: String? = null; private set
    val expired: Boolean get() = !owner.isCurrentGeneration(generation)
    val current: PlannedReview? get() = if (expired) null else plan.getOrNull(index)
    val complete: Boolean get() = index == plan.size && !expired
    fun reveal(expectedIndex: Int) { if (!expired && expectedIndex == index && current != null) revealed = true }
    fun rate(expectedIndex: Int, outcome: ReviewOutcome) {
        val item = current ?: return
        if (!revealed || expectedIndex != index) return
        val time = now(); val action = token()
        if (expired) return
        when (owner.mutate { commitReview(it, item, outcome, time, action) }) {
            ProgressMutationResult.Accepted, ProgressMutationResult.Unchanged -> {
                if (!expired) { index++; revealed = false; error = null }
            }
            is ProgressMutationResult.Rejected -> error = "The schedule changed or could not be updated. Exit and start a fresh review."
        }
    }
}
