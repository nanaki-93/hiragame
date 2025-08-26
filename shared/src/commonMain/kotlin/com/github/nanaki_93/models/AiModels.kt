package com.github.nanaki_93.models

import kotlinx.serialization.Serializable

@Serializable
data class AIWordQuestion(
    val hiraganaWord: String,
    val romanization: String,
    val topic: String
)

@Serializable
data class AISentenceQuestion(
    val hiraganaSentence: String,
    val romanization: String,
    val topic: String
)

@Serializable
data class AIGenerationRequest(
    val mode: String, // "word" or "sentence"
    val topic: String,
    val difficulty: Int = 1
)