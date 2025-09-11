package com.github.nanaki_93.service

import com.github.nanaki_93.models.AuthResponse
import com.github.nanaki_93.models.LoginRegisterRequest
import com.github.nanaki_93.repository.JpUser
import com.github.nanaki_93.repository.UserRepository
import com.github.nanaki_93.util.toUUID
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JWTService,
    private val gameService: GameService,
) {

    fun register(request: LoginRegisterRequest): AuthResponse {
        return try {
            // Check if user already exists
            if (userRepository.existsByUsername(request.username)) {
                throw Exception("User already exists with this email")
            }

            // Create new user
            val hashedPassword = passwordEncoder.encode(request.password)
            val user = JpUser(username = request.username, password = hashedPassword)

            val savedUser = userRepository.save(user)

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


    fun refreshToken(refreshToken: String): AuthResponse {
        return try {
            if (!jwtService.validateToken(refreshToken)) {
                throw Exception("Invalid refresh token")
            }

            val userId = jwtService.extractUserId(refreshToken)
            val user = userRepository.findById(userId.toUUID()).orElse(null) ?: throw Exception("User not found")

            toAuthResponse(user)
        } catch (e: Exception) {
            throw e
        }
    }

    private fun toAuthResponse(user: JpUser): AuthResponse {
        // Generate tokens
        val accessToken = jwtService.generateAccessToken(user.id.toString(), user.username)
        val refreshToken = jwtService.generateRefreshToken(user.id.toString(), user.username)

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