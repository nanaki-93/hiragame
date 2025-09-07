package com.github.nanaki_93.dto

import com.github.nanaki_93.models.Level
import com.github.nanaki_93.repository.UserLevel
import java.time.LocalDateTime
import java.util.UUID

data class UserLevelDto(
    val id: UUID? = null,
    val userId: UUID,
    val level: Level,
    val isCompleted: Boolean,
    val isAvailable: Boolean,
    val answeredAt: LocalDateTime = LocalDateTime.now(),
    val gameMode: String,
    val correctCount: Int
)


fun UserLevelDto.toModel() = UserLevel(
    id = id,
    userId = userId,
    level = level,
    isCompleted = isCompleted,
    isAvailable = isAvailable,
    answeredAt = answeredAt,
    gameMode = gameMode,
    correctCount = correctCount,
)

