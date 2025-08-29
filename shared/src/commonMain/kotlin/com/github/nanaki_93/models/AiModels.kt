package com.github.nanaki_93.models

import kotlinx.serialization.Serializable

@Serializable
data class AIQuestion(
    val hiragana: String,
    val romanization: String,
    val translation: String,
    val topic: String,
    val level: Int = 1,
)

