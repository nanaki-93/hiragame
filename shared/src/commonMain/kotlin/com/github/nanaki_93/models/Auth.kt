package com.github.nanaki_93.models

import kotlinx.serialization.Serializable

const val ACCESS_TOKEN = "access_token"
const val REFRESH_TOKEN = "refresh_token"
@Serializable
data class SelectRequest(val gameMode: GameMode, val level: Level, val userId: String)

@Serializable
data class LevelListRequest(val gameMode: GameMode, val userId: String)


@Serializable
data class LoginRegisterRequest(
    val username: String,
    val password: String
)


@Serializable
data class AuthResponse(
    val token: String,
    val refreshToken: String,
    val userId: String,
    val username: String
)

@Serializable
data class ErrorResponse(
    val message: String,
    val status: Int
)


@Serializable
data class UserData(
    val userId: String,
    val username: String
)

@Serializable
data class JWTPayload(
    val sub: String,
    val username: String,
    val exp: Long,
    val iat: Long
)