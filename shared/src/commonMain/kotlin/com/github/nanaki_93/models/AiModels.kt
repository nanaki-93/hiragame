package com.github.nanaki_93.models

import kotlinx.serialization.Serializable

@Serializable
data class AIWordQuestion(
    val hiraganaWord: String,
    val romanization: String,
    val englishWord: String,
    val topic: String
)

@Serializable
data class AISentenceQuestion(
    val hiraganaSentence: String,
    val romanization: String,
    val englishSentence: String,
    val topic: String
)

