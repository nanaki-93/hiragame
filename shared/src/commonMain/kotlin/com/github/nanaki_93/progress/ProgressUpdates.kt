package com.github.nanaki_93.progress

/** Pure domain changes. The persistence owner supplies envelope identity, revision and save time. */
sealed interface ProgressUpdate {
    data class Applied(val snapshot: SaveEnvelope) : ProgressUpdate
    data class Unchanged(val snapshot: SaveEnvelope) : ProgressUpdate
    /** The previous validated snapshot remains the only usable value. */
    data class Rejected(val snapshot: SaveEnvelope) : ProgressUpdate
}

private inline fun update(snapshot: SaveEnvelope, transform: () -> SaveEnvelope): ProgressUpdate {
    // Callers must not use a malformed baseline as a source for a new usable snapshot.
    try {
        validateSave(snapshot)
        val candidate = transform()
        if (candidate == snapshot) return ProgressUpdate.Unchanged(snapshot)
        // Validation alone cannot detect an oversized wire representation.
        encodeSave(candidate)
        return ProgressUpdate.Applied(candidate)
    } catch (_: IllegalArgumentException) {
        return ProgressUpdate.Rejected(snapshot)
    }
}

private fun <T> List<T>.replaceById(id: String, key: (T) -> String, value: T): List<T> {
    val index = indexOfFirst { key(it) == id }
    return if (index < 0) this + value else toMutableList().also { it[index] = value }
}

fun changePreferences(snapshot: SaveEnvelope, preferences: SavePreferences): ProgressUpdate =
    update(snapshot) { snapshot.copy(preferences = preferences) }

/** A visit changes the active stage/checkpoint, but never implicitly clears completion. */
fun visitLesson(
    snapshot: SaveEnvelope,
    lessonId: String,
    contentVersion: Int,
    stage: LessonStage,
    checkpointId: String?,
    updatedAtEpochMs: Long,
): ProgressUpdate = update(snapshot) {
    val previous = snapshot.lessonProgress.firstOrNull { it.lessonId == lessonId }
    val record = LessonProgress(
        lessonId, contentVersion, updatedAtEpochMs, stage, checkpointId,
        completedAtEpochMs = previous?.completedAtEpochMs,
    )
    snapshot.copy(lessonProgress = snapshot.lessonProgress.replaceById(lessonId, LessonProgress::lessonId, record))
}

/** Completion is an explicit action on an encountered lesson; a replay cannot erase its first completion. */
fun completeLesson(
    snapshot: SaveEnvelope,
    lessonId: String,
    completedAtEpochMs: Long,
): ProgressUpdate = update(snapshot) {
    val previous = snapshot.lessonProgress.firstOrNull { it.lessonId == lessonId }
        ?: throw IllegalArgumentException("Lesson has not been encountered")
    if (previous.completedAtEpochMs != null) snapshot else snapshot.copy(
        lessonProgress = snapshot.lessonProgress.replaceById(
            lessonId, LessonProgress::lessonId,
            previous.copy(updatedAtEpochMs = completedAtEpochMs, completedAtEpochMs = completedAtEpochMs),
        ),
    )
}

/** Ratings and optional scheduling values are supplied explicitly by a future consumer, never inferred here. */
fun recordReview(snapshot: SaveEnvelope, record: ReviewItemProgress): ProgressUpdate = update(snapshot) {
    val previous = snapshot.reviewItems.firstOrNull { it.itemId == record.itemId }
    // A token identifies one action. Reusing it with different state is not a new review.
    if (previous?.lastActionToken == record.lastActionToken) {
        if (previous != record) throw IllegalArgumentException("Reused review action token")
        snapshot
    } else snapshot.copy(reviewItems = snapshot.reviewItems.replaceById(
        record.itemId, ReviewItemProgress::itemId, record,
    ))
}

/** Availability is a projection over catalog IDs grouped by owning document, never a filter of saved records.
 * An empty child set means the document exists but the saved checkpoint cannot be resolved.
 */
enum class ProgressAvailability { AVAILABLE, DOCUMENT_UNAVAILABLE, CHECKPOINT_UNAVAILABLE }

fun projectAvailability(
    documentId: String,
    checkpointId: String?,
    childrenByDocument: Map<String, Set<String>>,
): ProgressAvailability {
    val children = childrenByDocument[documentId] ?: return ProgressAvailability.DOCUMENT_UNAVAILABLE
    return if (checkpointId != null && checkpointId !in children) ProgressAvailability.CHECKPOINT_UNAVAILABLE
        else ProgressAvailability.AVAILABLE
}

fun lessonAvailability(
    record: LessonProgress,
    checkpointsByLesson: Map<String, Set<String>>,
): ProgressAvailability = projectAvailability(record.lessonId, record.checkpointId, checkpointsByLesson)

fun reviewAvailability(
    record: ReviewItemProgress,
    reviewItemsByDocument: Map<String, Set<String>>,
): ProgressAvailability = projectAvailability(record.documentId, record.itemId, reviewItemsByDocument)

fun practiceAvailability(
    record: PracticeCheckpoint,
    exercisesBySet: Map<String, Set<String>>,
): ProgressAvailability {
    val exercises = exercisesBySet[record.setId] ?: return ProgressAvailability.DOCUMENT_UNAVAILABLE
    return if (record.exerciseIds.any { it !in exercises }) ProgressAvailability.CHECKPOINT_UNAVAILABLE
        else ProgressAvailability.AVAILABLE
}
