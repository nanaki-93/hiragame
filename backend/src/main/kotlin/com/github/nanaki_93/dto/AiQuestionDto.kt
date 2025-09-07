package com.github.nanaki_93.dto

import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.models.Level
import com.github.nanaki_93.repository.Question
import java.time.LocalDateTime
import java.util.UUID

data class AiQuestionDto(
    val level: Level = Level.N5,
    val nQuestions: Int = 5,
    val gameMode: GameMode = GameMode.WORD,
    val topic: String = "",
)




open class QuestionDto(
    val id: UUID? = null,
    val japanese: String,
    val romanization: String,
    val translation: String,
    val topic: String = "",
    val level: Level = Level.N5,
    val gameMode: GameMode = GameMode.SIGN,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val hasKatakana: Boolean = false,
    val hasKanji: Boolean = false,
)

fun QuestionDto.toModel() = Question(
    id = id,
    japanese = japanese,
    romanization = romanization,
    translation = translation,
    topic = topic,
    level = level,
    gameMode = gameMode,
    createdAt = createdAt,
    hasKatakana = hasKatakana,
    hasKanji = hasKanji,
)




