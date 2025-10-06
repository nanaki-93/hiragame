package com.github.nanaki_93.repository

import com.github.nanaki_93.dto.game.UserAnsweredQuestionDto
import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.models.Level
import com.github.nanaki_93.models.SelectRequest
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.addAll
import kotlin.compareTo
import kotlin.text.append

@Entity
@Table(name = "user_answered_question")
class UserAnsweredQuestion(
    @Id
    @GeneratedValue
    @Column(nullable = false)
    val id: UUID? = null,

    @Column( nullable = false)
    val userId: UUID,

    @Column( nullable = false)
    val questionId: UUID,

    @Column( nullable = false)
    val isCorrect: Boolean,

    @Column( nullable = false)
    val answeredAt: LocalDateTime = LocalDateTime.now(),

    @Column
    val attemps: Int? = null,

    @Column( nullable = false)
    val lastAttemptedAt: LocalDateTime = LocalDateTime.now(),

    @Enumerated(EnumType.STRING)
    @Column( nullable = false)
    val gameMode: GameMode,

    @Enumerated(EnumType.STRING)
    @Column( nullable = false)
    val level: Level,
)

@Repository
interface UserAnsweredQuestionRepository : JpaRepository<UserAnsweredQuestion, UUID> {


    // Method 2: Random selection with object parameter
    @Query(nativeQuery = true, value = """
        SELECT question_id FROM user_answered_question 
        WHERE user_id = CAST(:#{#select.userId} AS UUID)
        AND game_mode = :#{#select.gameMode.name} 
        AND level = :#{#select.level.name}
        ORDER BY RANDOM() 
        LIMIT 1
    """)
    fun findRandomBySelect(@Param("select") select: SelectRequest): UUID?


    fun findByUserIdAndQuestionId(userId: UUID, questionId: UUID): Optional<UserAnsweredQuestion>

    // Find all questions a user has not answered in a specific game mode
    @Query("SELECT uaq FROM UserAnsweredQuestion uaq WHERE uaq.userId = :userId AND uaq.gameMode = :gameMode AND uaq.isCorrect = false and uaq.level= :level")
    fun findByUserIdAndGameModeAndNotCorrect(@Param("userId") userId: UUID, @Param("gameMode") gameMode: String, @Param("level") level: String): List<UserAnsweredQuestion>

    // Find all questions a user has answered in a specific game mode
    @Query("SELECT uaq FROM UserAnsweredQuestion uaq WHERE uaq.userId = :userId AND uaq.gameMode = :gameMode")
    fun findByUserIdAndGameMode(@Param("userId") userId: UUID, @Param("gameMode") gameMode: String): List<UserAnsweredQuestion>

    // Find all questions a user has answered correctly
    @Query("SELECT uaq FROM UserAnsweredQuestion uaq WHERE uaq.userId = :userId AND uaq.isCorrect = true")
    fun findCorrectAnswersByUserId(@Param("userId") userId: UUID): List<UserAnsweredQuestion>

    // Get user's answer history for a specific question
    @Query("SELECT uaq FROM UserAnsweredQuestion uaq WHERE uaq.userId = :userId AND uaq.questionId = :questionId ORDER BY uaq.answeredAt DESC")
    fun findUserAnswersForQuestion(@Param("userId") userId: UUID, @Param("questionId") questionId: UUID): List<UserAnsweredQuestion>

    // Check if user has already answered a question correctly in a specific game mode
    @Query("SELECT COUNT(uaq) > 0 FROM UserAnsweredQuestion uaq WHERE uaq.userId = :userId AND uaq.questionId = :questionId AND uaq.gameMode = :gameMode AND uaq.isCorrect = true")
    fun hasAnsweredCorrectly(@Param("userId") userId: UUID, @Param("questionId") questionId: UUID, @Param("gameMode") gameMode: String): Boolean

    // Get user's statistics
    @Query("SELECT COUNT(uaq) FROM UserAnsweredQuestion uaq WHERE uaq.userId = :userId AND uaq.isCorrect = true")
    fun countCorrectAnswersByUserId(@Param("userId") userId: UUID): Long

    @Query("SELECT COUNT(uaq) FROM UserAnsweredQuestion uaq WHERE uaq.userId = :userId")
    fun countTotalAnswersByUserId(@Param("userId") userId: UUID): Long
}

fun UserAnsweredQuestion.toDto() = UserAnsweredQuestionDto(
    id = id,
    userId = userId,
    questionId = questionId,
    isCorrect = isCorrect,
    attemps = attemps,
    lastAttemptedAt = lastAttemptedAt,
    gameMode = gameMode,
    level = level,
)



@Repository
class UserAnsweredQuestionBulkRepository(private val jdbcTemplate: JdbcTemplate) {
    fun bulkInsert(questions: List<UserAnsweredQuestion>) {
        if (questions.isEmpty()) return

        val sql = StringBuilder("INSERT INTO user_answered_question (user_id, question_id, is_correct, attemps, answered_at, game_mode, level) VALUES ")
        val params = mutableListOf<Any>()

        questions.forEachIndexed { i, q ->
            if (i > 0) sql.append(",")
            sql.append("(?, ?, ?, ?, ?, ?, ?)")
            params.addAll(
                listOf(
                    q.userId as Any,
                    q.questionId as Any,
                    q.isCorrect as Any,
                    q.attemps as Any,
                    q.answeredAt as Any,
                    q.gameMode.name as Any,
                    q.level.name as Any
                )
            )
        }

        jdbcTemplate.update(sql.toString(), *params.toTypedArray())
    }
}
