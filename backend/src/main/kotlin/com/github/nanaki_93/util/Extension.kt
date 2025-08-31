package com.github.nanaki_93.util

import com.github.nanaki_93.models.AIQuestion
import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.repository.Question


fun AIQuestion.toHiraganaQuestion(gameMode: GameMode): Question =
    Question(
        japanese = hiragana,
        romanization = romanization,
        translation = translation,
        topic = topic,
        level = level.name,
        gameMode = gameMode.name,
    )