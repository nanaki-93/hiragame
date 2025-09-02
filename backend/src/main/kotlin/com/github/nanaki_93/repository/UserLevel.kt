package com.github.nanaki_93.repository

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "user_level")
data class UserLevel(
    @Id
    @GeneratedValue
    @Column(nullable = false)
    val id: UUID? = null,

    @Column(nullable = false)
    val userId: UUID,

    @Column(nullable = false)
    val level: String,

    @Column(nullable = false)
    val isCompleted: Boolean,

    @Column(nullable = false)
    val isAvailable: Boolean,

    @Column(nullable = false)
    val answeredAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    val gameMode: String,

    @Column
    val correctCount: Int? = null
)

@Repository
interface UserLevelRepository : JpaRepository<UserLevel, UUID> {
    fun findByUserId(userId: UUID): List<UserLevel>
    fun findByUserIdAndLevelAndGameMode(userId: UUID, level: String, gameMode: String): UserLevel
}