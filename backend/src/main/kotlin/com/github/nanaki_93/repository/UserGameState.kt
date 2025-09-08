package com.github.nanaki_93.repository


import com.github.nanaki_93.dto.game.UserGameStateDto
import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.models.GameStateUi
import com.github.nanaki_93.models.GameStatisticsUi
import com.github.nanaki_93.models.Level
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(
    name = "user_game_state",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id"])]
)
class UserGameState(
    @Id
    @GeneratedValue
    @Column(nullable = false)
    val id: UUID? = null,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "gameMode", nullable = false)
    val gameMode: GameMode = GameMode.SIGN,

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false)
    val level: Level = Level.N5,

    @Column(nullable = false)
    val score: Int = 0,

    @Column(nullable = false)
    val streak: Int = 0,

    @Column(name = "total_answered", nullable = false)
    val totalAnswered: Int = 0,

    @Column(name = "correct_answers", nullable = false)
    val correctAnswers: Int = 0,

    @Column(name = "last_answer_correct")
    val lastAnswerCorrect: Boolean? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

@Repository
interface UserGameStateRepository : JpaRepository<UserGameState, UUID> {
    fun findByUserId(userId: UUID): UserGameState?
}


fun UserGameState.toDto() = UserGameStateDto(
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


fun UserGameState.toUi() = GameStateUi(
    userId = userId.toString(),
    stats = GameStatisticsUi(
        score = score,
        streak = streak,
        totalAnswered = totalAnswered,
        correctAnswers = correctAnswers,
        lastAnswerCorrect = lastAnswerCorrect,
    ),
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString()
)

