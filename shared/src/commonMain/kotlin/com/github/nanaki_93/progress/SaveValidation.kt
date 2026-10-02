package com.github.nanaki_93.progress

/** Structural limits are independent of which bundled documents happen to be installed. */
object SaveBounds {
    const val SCHEMA_VERSION = 1
    const val MAX_ID_LENGTH = 128
    const val MAX_LEARNING_RECORDS = 1_000
    const val MAX_REVIEW_RECORDS = 5_000
    const val MAX_EXERCISES = 10
    const val MAX_SCHEDULE_COUNT = 1_000_000
    const val MAX_JSON_BYTES = 2 * 1024 * 1024
    const val MAX_JSON_DEPTH = 32
    const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L
    const val MAX_EPOCH_MS = 253_402_300_799_999L
}

private val stableId = Regex("[A-Za-z0-9][A-Za-z0-9_-]*")

private fun id(value: String) {
    require(value.length in 1..SaveBounds.MAX_ID_LENGTH && stableId.matches(value)) { "Invalid save ID" }
}

private fun token(value: String) {
    require(value.length in 1..SaveBounds.MAX_ID_LENGTH && stableId.matches(value)) { "Invalid save token" }
}

private fun date(value: Long) {
    require(value in 0..SaveBounds.MAX_EPOCH_MS) { "Invalid save timestamp" }
}

private fun count(value: Int) {
    require(value in 0..SaveBounds.MAX_SCHEDULE_COUNT) { "Invalid scheduling counter" }
}

/** Throws on invalid input. Call before treating a snapshot as usable, not only before encoding it. */
fun validateSave(snapshot: SaveEnvelope) {
    require(snapshot.schemaVersion == SaveBounds.SCHEMA_VERSION) { "Unsupported save schema" }
    date(snapshot.savedAtEpochMs)
    token(snapshot.snapshotId)
    require(snapshot.revision in 0..SaveBounds.MAX_SAFE_INTEGER) { "Invalid save revision" }
    require(snapshot.lessonProgress.size + snapshot.practiceProgress.size <= SaveBounds.MAX_LEARNING_RECORDS) {
        "Too many learning records"
    }
    require(snapshot.reviewItems.size <= SaveBounds.MAX_REVIEW_RECORDS) { "Too many review records" }

    val lessons = HashSet<String>()
    snapshot.lessonProgress.forEach { record ->
        id(record.lessonId)
        require(lessons.add(record.lessonId)) { "Duplicate lesson ID" }
        require(record.contentVersion > 0) { "Invalid content version" }
        date(record.updatedAtEpochMs)
        record.checkpointId?.let(::id)
        record.completedAtEpochMs?.let(::date)
    }

    val sets = HashSet<String>()
    snapshot.practiceProgress.forEach { record ->
        id(record.setId)
        require(sets.add(record.setId)) { "Duplicate practice set ID" }
        require(record.contentVersion > 0) { "Invalid content version" }
        date(record.updatedAtEpochMs)
        token(record.runToken)
        token(record.lastTransitionToken)
        require(record.exerciseIds.size in 1..SaveBounds.MAX_EXERCISES) { "Invalid practice length" }
        require(record.exerciseTypes.size == record.exerciseIds.size) { "Invalid practice exercise types" }
        record.exerciseIds.forEach(::id)
        require(record.exerciseIds.size == record.exerciseIds.toSet().size) { "Duplicate exercise ID" }
        val resolved = record.outcomes.size
        val total = record.exerciseIds.size
        require(resolved <= total) { "Too many practice outcomes" }
        record.outcomes.forEachIndexed { index, outcome ->
            require(if (record.exerciseTypes[index] == CheckpointExerciseType.PRODUCTION) {
                outcome != CompactOutcome.CORRECT && outcome != CompactOutcome.INCORRECT
            } else {
                outcome != CompactOutcome.SELF_MET_CRITERIA && outcome != CompactOutcome.SELF_NEEDS_PRACTICE
            }) { "Incompatible practice outcome" }
        }
        when (record.view) {
            CheckpointView.PROMPT -> require(record.frontier in 0 until total && resolved == record.frontier) {
                "Invalid prompt frontier"
            }
            CheckpointView.FEEDBACK -> require(record.frontier in 0 until total && resolved == record.frontier + 1) {
                "Invalid feedback frontier"
            }
            CheckpointView.COMPLETE -> require(record.frontier == total && resolved == total && record.lastCompletedAtEpochMs != null) {
                "Invalid completion frontier"
            }
        }
        record.lastCompletedAtEpochMs?.let(::date)
    }

    val items = HashSet<String>()
    snapshot.reviewItems.forEach { record ->
        id(record.itemId)
        id(record.documentId)
        require(items.add(record.itemId)) { "Duplicate review item ID" }
        date(record.lastReviewedAtEpochMs)
        record.dueAtEpochMs?.let(::date)
        record.intervalMs?.let { require(it in 0..SaveBounds.MAX_EPOCH_MS) { "Invalid review interval" } }
        record.step?.let(::count)
        record.repetitions?.let(::count)
        record.lapses?.let(::count)
        token(record.lastActionToken)
    }
}
