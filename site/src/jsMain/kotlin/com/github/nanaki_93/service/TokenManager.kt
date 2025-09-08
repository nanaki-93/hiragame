package com.github.nanaki_93.service

import com.github.nanaki_93.models.JWTPayload
import com.github.nanaki_93.models.UserData
import kotlinx.browser.localStorage
import kotlinx.serialization.json.Json
import kotlin.js.Date.Companion.now
import kotlinx.serialization.encodeToString



object TokenManager {
    private const val TOKEN_KEY = "jwt_token"
    private const val USER_KEY = "user_data"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun saveToken(token: String, userId: String, name: String) {
        localStorage.setItem(TOKEN_KEY, token)
        localStorage.setItem(USER_KEY, Json.encodeToString(UserData(userId, name)))
    }

    fun getToken(): String? {
        return localStorage.getItem(TOKEN_KEY)
    }

    fun getCurrentUserId(): String? {
        return try {
            val token = getToken() ?: return null
            if (!isTokenValid()) return null
            val payload = decodeJWTPayload(token)
            payload.userId
        } catch (e: Exception) {
            null
        }
    }

    fun getCurrentUserName(): String? {
        return try {
            val token = getToken() ?: return null
            if (!isTokenValid()) return null
            val payload = decodeJWTPayload(token)
            payload.name
        } catch (e: Exception) {
            null
        }
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
            val currentTime = now()/1000
            payload.exp > currentTime
        } catch (_: Exception) {
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
        // Fix Base64URL decoding by adding proper padding and replacing URL-safe characters
        val paddedPayload = payload + "=".repeat((4 - payload.length % 4) % 4)
        val base64Payload = paddedPayload.replace('-', '+').replace('_', '/')

        val decodedPayload = kotlinx.browser.window.atob(base64Payload)

        return json.decodeFromString<JWTPayload>(decodedPayload)
    }

}

