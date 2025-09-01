package com.github.nanaki_93.dto

import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.models.Level

data class QuestionDto(
    val level: Level = Level.N5,
    val nQuestions: Int = 5,
    val gameMode: GameMode = GameMode.WORD,
    val topic: String = "",
)