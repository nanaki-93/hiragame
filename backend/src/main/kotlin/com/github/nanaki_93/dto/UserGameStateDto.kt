package com.github.nanaki_93.dto

import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.models.Level
import com.github.nanaki_93.repository.UserGameState
import java.time.LocalDateTime
import java.util.UUID

data class UserGameStateDto(

    val id: UUID? = null,
    val userId: UUID,
    val gameMode: GameMode = GameMode.SIGN,
    val level: Level = Level.N5,
    val score: Int = 0,
    val streak: Int = 0,
    val totalAnswered: Int = 0,
    val correctAnswers: Int = 0,
    val lastAnswerCorrect: Boolean? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)


fun UserGameStateDto.toModel() = UserGameState(
    id = id,
    userId = userId,
    gameMode = gameMode,
    level = level,
    score = score,
    streak = streak,
    totalAnswered = totalAnswered,
    correctAnswers = correctAnswers,
    lastAnswerCorrect = lastAnswerCorrect,
)