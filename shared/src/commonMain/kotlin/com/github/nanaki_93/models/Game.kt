package com.github.nanaki_93.models

import kotlinx.serialization.Serializable

@Serializable
data class QuestionRequest(
    val userQuestionId: String,
    val japanese: String,
    val romanization: String,
    val translation: String? = null,
    val topic: String = "",
    val level: Level = Level.N5,
    val gameMode: GameMode = GameMode.SIGN,
    val hasKatakana: Boolean = false,
    val hasKanji: Boolean = false,
    val userInput: String? = null,
    val userId: String,
)

@Serializable
data class GameStateReq(
    val score: Int = 0,
    val streak: Int = 0,
    val totalAnswered: Int = 0,
    val correctAnswers: Int = 0,
    val feedback: String = "",
    val isCorrect: Boolean? = null,
    val userInput: String? = null,
    val userId: String,
)


@Serializable
data class GameStateRequest(val gameStateReq: GameStateReq)

@Serializable
data class SelectRequest(val gameMode: GameMode, val level: Level, val userId: String)





