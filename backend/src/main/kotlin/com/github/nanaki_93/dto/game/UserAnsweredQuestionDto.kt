package com.github.nanaki_93.dto.game

import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.models.Level
import com.github.nanaki_93.repository.UserAnsweredQuestion
import java.time.LocalDateTime
import java.util.UUID

data class UserAnsweredQuestionDto(
    val id: UUID? = null,
    val userId: UUID,
    val questionId: UUID,
    val isCorrect: Boolean,
    val answeredAt: LocalDateTime = LocalDateTime.now(),
    val attemps: Int? = null,
    val lastAttemptedAt: LocalDateTime = LocalDateTime.now(),
    val gameMode: GameMode,
    val level: Level,
)

fun UserAnsweredQuestionDto.toModel() = UserAnsweredQuestion(
    id = id,
    userId = userId,
    questionId = questionId,
    isCorrect = isCorrect,
    attemps = attemps,
    lastAttemptedAt = lastAttemptedAt,
    gameMode = gameMode,
    level = level,
)