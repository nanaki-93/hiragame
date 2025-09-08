package com.github.nanaki_93.service

import com.github.nanaki_93.models.AuthResponse
import com.github.nanaki_93.models.ErrorResponse
import com.github.nanaki_93.models.LoginRequest
import com.github.nanaki_93.models.RegisterRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json


class AuthService {
    private val client = HttpClient(Js) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val baseUrl = "http://localhost:8080"

    suspend fun login(name: String, password: String): Result<AuthResponse> {
        return try {
            val response = client.post("$baseUrl/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(name, password))
            }

            if (response.status.isSuccess()) {
                val authResponse: AuthResponse = response.body()
                Result.success(authResponse)
            } else {
                val errorResponse: ErrorResponse = response.body()
                Result.failure(Exception(errorResponse.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(name: String, password: String): Result<AuthResponse> {
        return try {
            val response = client.post("$baseUrl/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(name, password))
            }

            if (response.status.isSuccess()) {
                val authResponse: AuthResponse = response.body()
                Result.success(authResponse)
            } else {
                val errorResponse: ErrorResponse = response.body()
                Result.failure(Exception(errorResponse.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}