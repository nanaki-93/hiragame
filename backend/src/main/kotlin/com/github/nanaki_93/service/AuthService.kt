package com.github.nanaki_93.service

import com.github.nanaki_93.models.AuthResponse
import com.github.nanaki_93.models.LoginRequest
import com.github.nanaki_93.models.RegisterRequest
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

    fun register(request: RegisterRequest): Result<AuthResponse> {
        return try {
            // Check if user already exists
            if (userRepository.existsByUsername(request.username)) {
                return Result.failure(Exception("User already exists with this email"))
            }

            // Create new user
            val hashedPassword = passwordEncoder.encode(request.password)
            val user = JpUser(username = request.username, password = hashedPassword)

            val savedUser = userRepository.save(user)

            gameService.initGame(savedUser.id)
            //todo initialiaze db state for the user
            toAuthResponse(savedUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun login(request: LoginRequest): Result<AuthResponse> {
        return try {

            val user = userRepository.findByUsername(request.username).orElse(null) ?: return Result.failure(Exception("Invalid credentials"))
            if (!passwordEncoder.matches(request.password, user.password)) {
                return Result.failure(Exception("Invalid credentials"))
            }
            toAuthResponse(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    fun refreshToken(refreshToken: String): Result<AuthResponse> {
        return try {
            if (!jwtService.validateToken(refreshToken)) {
                return Result.failure(Exception("Invalid refresh token"))
            }

            val userId = jwtService.extractUserId(refreshToken)
            val user = userRepository.findById(userId.toUUID()).orElse(null) ?: return Result.failure(Exception("User not found"))

            toAuthResponse(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun toAuthResponse(user: JpUser): Result<AuthResponse> {
        // Generate tokens
        val accessToken = jwtService.generateAccessToken(user.id.toString(), user.username)
        val refreshToken = jwtService.generateRefreshToken(user.id.toString(), user.username)

        return Result.success(
            AuthResponse(
                token = accessToken,
                refreshToken = refreshToken,
                userId = user.id.toString(),
                username = user.username
            )
        )
    }


    fun getUserByUsername(username: String): JpUser? {
        return userRepository.findByUsername(username).orElse(null)
    }
}