package com.github.nanaki_93.service

import kotlinx.browser.localStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.js.Date.Companion.now
import kotlinx.serialization.encodeToString

@Serializable
data class JWTPayload(
    val userId: String,
    val email: String,
    val exp: Long,
    val iat: Long
)

object TokenManager {
    private const val TOKEN_KEY = "jwt_token"
    private const val USER_KEY = "user_data"

    fun saveToken(token: String, userId: String, email: String) {
        localStorage.setItem(TOKEN_KEY, token)
        localStorage.setItem(USER_KEY, Json.encodeToString(UserData(userId, email)))
    }

    fun getToken(): String? {
        return localStorage.getItem(TOKEN_KEY)
    }

    fun getUserData(): UserData? {
        return try {
            localStorage.getItem(USER_KEY)?.let {
                Json.decodeFromString<UserData>(it)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun isTokenValid(): Boolean {
        val token = getToken() ?: return false

        return try {
            val payload = decodeJWTPayload(token)
            val currentTime = now()
            payload.exp > currentTime
        } catch (e: Exception) {
            false
        }
    }

    fun clearToken() {
        localStorage.removeItem(TOKEN_KEY)
        localStorage.removeItem(USER_KEY)
    }

    private fun decodeJWTPayload(token: String): JWTPayload {
        val parts = token.split(".")
        if (parts.size != 3) throw IllegalArgumentException("Invalid JWT token")

        val payload = parts[1]
        val decodedPayload = kotlinx.browser.window.atob(payload)
        return Json.decodeFromString<JWTPayload>(decodedPayload)
    }
}

@Serializable
data class UserData(
    val userId: String,
    val email: String
)