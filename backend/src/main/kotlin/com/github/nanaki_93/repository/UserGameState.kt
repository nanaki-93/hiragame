package com.github.nanaki_93.repository



import com.github.nanaki_93.models.Level
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(
    name = "user_game_state",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", ])]
)
data class UserGameState(
    @Id
    @GeneratedValue
    @Column(nullable = false)
    val id: UUID? = null,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    //todo manage enum
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
    fun findByUserId(userId: UUID): UserGameState


}