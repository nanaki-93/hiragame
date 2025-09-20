package com.github.nanaki_93.service

import com.github.nanaki_93.models.AuthResponse
import com.github.nanaki_93.models.LoginRegisterRequest
import com.github.nanaki_93.repository.JpUser
import com.github.nanaki_93.repository.UserRepository
import com.github.nanaki_93.repository.copy
import com.github.nanaki_93.util.toUUID
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.MalformedJwtException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JWTService,
    private val gameService: GameService,
) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(AuthService::class.java)
    }

    fun register(request: LoginRegisterRequest): AuthResponse {
        return try {
            // todo use the proper exception
            if (userRepository.existsByUsername(request.username)) {
                throw Exception("User already exists with this email")
            }

            // Create new user

            val savedUser = userRepository.save(
                JpUser(
                    username = request.username,
                    password = passwordEncoder.encode(request.password)
                )
            )

            gameService.initGame(savedUser.id)
            toAuthResponse(savedUser)
        } catch (e: Exception) {
            throw e
        }
    }

    fun login(request: LoginRegisterRequest): AuthResponse {
        return try {

            val user = userRepository.findByUsername(request.username).orElse(null) ?: throw Exception("Invalid credentials")
            if (!passwordEncoder.matches(request.password, user.password)) {
                throw Exception("Invalid credentials")
            }
            toAuthResponse(user)
        } catch (e: Exception) {
            throw e
        }
    }

    fun logout(token: String) {
        try {
            jwtService.extractUserId(token).toUUID().let { userId ->
                userRepository.save(userRepository.findById(userId).get().copy(refreshToken = null, expiresAt = null))
            }
        } catch (e: Exception) {
            throw e
        }
    }


    fun refreshToken(refreshToken: String): AuthResponse {
        return try {
            val user = userRepository.findByRefreshToken(refreshToken)?: throw MalformedJwtException("Invalid refresh token")
            val userId = jwtService.extractUserId(refreshToken)

            if (user.expiresAt?.isBefore(LocalDateTime.now()) == true) {
                throw MalformedJwtException("Invalid refresh token")
            }

            if (user.id.toString() != userId) {
                throw MalformedJwtException("Invalid refresh token")
            }

            if (!jwtService.validateToken(refreshToken)) {
                logger.error("Invalid refresh token: $refreshToken")
                throw MalformedJwtException("Invalid refresh token")
            }

            toAuthResponse(user)
        } catch (e: Exception) {
            throw e
        }
    }

    private fun toAuthResponse(user: JpUser): AuthResponse {
        // Generate tokens
        val accessToken = jwtService.generateAccessToken(user.id.toString(), user.username)
        val refreshToken = jwtService.generateRefreshToken(user.id.toString(), user.username)

        val expiresAt: LocalDateTime = jwtService.extractExpiration(refreshToken)
            .toInstant()
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()

        userRepository.save(user.copy(refreshToken = refreshToken, expiresAt = expiresAt))

        return AuthResponse(
            token = accessToken,
            refreshToken = refreshToken,
            userId = user.id.toString(),
            username = user.username
        )
    }


    fun getUserByUsername(username: String): JpUser? {
        return userRepository.findByUsername(username).orElse(null)
    }
}