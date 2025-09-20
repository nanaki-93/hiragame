package com.github.nanaki_93.repository

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.security.core.userdetails.User
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.emptyList

@Entity
@Table(name = "jp_user")
class JpUser(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(unique = true, nullable = false)
    val username: String = "",

    @Column(nullable = false)
    val password: String = "", // Hashed password

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "refresh_token")
    val refreshToken: String? = null,

    @Column(name = "expires_at")
    val expiresAt: LocalDateTime? = null
)


@Repository
interface UserRepository : JpaRepository<JpUser, UUID> {
    fun findByUsername(username: String): Optional<JpUser>
    fun existsByUsername(username: String): Boolean
    fun findByRefreshToken(refreshToken: String): JpUser?
}

fun JpUser.copy(
    id: UUID? = this.id,
    username: String? = this.username,
    password: String? = this.password,
    createdAt: LocalDateTime? = this.createdAt,
    updatedAt: LocalDateTime? = this.updatedAt,
    refreshToken: String? = this.refreshToken,
    expiresAt: LocalDateTime? = this.expiresAt
): JpUser {
    return JpUser(
        id = id ?: this.id,
        username = username ?: this.username,
        password = password ?: this.password,
        createdAt = createdAt ?: this.createdAt,
        updatedAt = updatedAt ?: this.updatedAt,
        refreshToken = refreshToken ?: this.refreshToken,
        expiresAt = expiresAt ?: this.expiresAt
    )
}

fun JpUser.ToUserDetail() = User.builder()
    .username(username)
    .password("")
    .authorities(emptyList()).build()






