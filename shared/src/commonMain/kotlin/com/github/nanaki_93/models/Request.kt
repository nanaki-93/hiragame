package com.github.nanaki_93.models

import kotlinx.serialization.Serializable


@Serializable
data class SelectRequest(val gameMode: GameMode, val level: Level, val userId: String)

@Serializable
data class LevelListRequest(val gameMode: GameMode, val userId: String)


@Serializable
data class LoginRequest(
    val name: String,
    val password: String
)

@Serializable
data class RegisterRequest(
    val name: String,
    val password: String,
)

@Serializable
data class AuthResponse(
    val token: String,
    val refreshToken: String,
    val userId: String,
    val name: String,
    val message: String? = null
)

@Serializable
data class ErrorResponse(
    val message: String,
    val status: Int
)


@Serializable
data class UserData(
    val userId: String,
    val name: String
)

@Serializable
data class JWTPayload(
    val sub: String,
    val userId: String,
    val name: String,
    val exp: Long,
    val iat: Long
)