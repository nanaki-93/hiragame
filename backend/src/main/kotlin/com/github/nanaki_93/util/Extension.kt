package com.github.nanaki_93.util

import com.github.nanaki_93.models.AIQuestion
import com.github.nanaki_93.repository.HiraganaQuestion


fun AIQuestion.toHiraganaQuestion(): HiraganaQuestion =
    HiraganaQuestion(
        hiragana = hiragana,
        romanization = romanization,
        translation = translation,
        topic = topic,
        difficulty = level
    )