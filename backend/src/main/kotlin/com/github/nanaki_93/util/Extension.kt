package com.github.nanaki_93.util

import com.github.nanaki_93.models.AIQuestion
import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.repository.HiraganaQuestion


fun AIQuestion.toHiraganaQuestion(gameMode: GameMode): HiraganaQuestion =
    HiraganaQuestion(
        hiragana = hiragana,
        romanization = romanization,
        translation = translation,
        topic = topic,
        level = level.name,
        gameMode = gameMode.name,
    )