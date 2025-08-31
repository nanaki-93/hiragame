package com.github.nanaki_93.models

import kotlinx.serialization.Serializable

@Serializable
data class AIQuestion(
    val japanese: String,
    val romanization: String,
    val translation: String,
    val topic: String,
    val level: Level = Level.N5,
)

