package com.github.nanaki_93.progress

/** Reset scope is chosen before the site owner authorizes and persists a replacement. */
enum class ResetScope { PROGRESS_ONLY, FULL_LEARNER_STATE }

/** Pure payload transformation of a validated snapshot. The site owner assigns new write metadata. */
fun resetLearnerState(snapshot: SaveEnvelope, scope: ResetScope): SaveEnvelope = snapshot.copy(
    preferences = when (scope) {
        ResetScope.PROGRESS_ONLY -> snapshot.preferences
        ResetScope.FULL_LEARNER_STATE -> SavePreferences()
    },
    lessonProgress = emptyList(),
    practiceProgress = emptyList(),
    reviewItems = emptyList(),
    personalGlossary = if (scope == ResetScope.PROGRESS_ONLY) snapshot.personalGlossary else emptyList(),
)
