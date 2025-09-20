package com.github.nanaki_93.models

import kotlinx.serialization.Serializable


@Serializable
data class UserQuestionDto(
    val questionId: String,
    val userInput: String? = null,
    val userId: String,
)


@Serializable
data class QuestionDto(
    val id: String? = null,
    val japanese: String ="",
    val topic: String = "",
    val level: Level = Level.N5,
    val gameMode: GameMode = GameMode.SIGN,
    val createdAt: String = "",
    val hasKatakana: Boolean = false,
    val hasKanji: Boolean = false,
)