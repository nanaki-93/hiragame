package com.github.nanaki_93.models

import kotlinx.serialization.Serializable


@Serializable
data class GameStateUi(
    val userId: String,
    val feedback: String = "",
    val isCorrect: Boolean? = null,
    val userInput: String? = null,
    val stats: GameStatisticsUi,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val gameState: GameState = GameState.LOADING
)

@Serializable
data class GameStatisticsUi(
    val score: Int = 0,
    val streak: Int = 0,
    val totalAnswered: Int = 0,
    val correctAnswers: Int = 0,
    val lastAnswerCorrect: Boolean? = null,
)


enum class GameState {
    LOADING,
    STATS_DISPLAY,
    MODE_SELECTION,
    LEVEL_SELECTION,
    PLAYING,
    SHOWING_FEEDBACK
}
