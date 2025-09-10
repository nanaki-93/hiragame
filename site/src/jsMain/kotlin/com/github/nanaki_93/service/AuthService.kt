package com.github.nanaki_93.service

import com.github.nanaki_93.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.*
import kotlinx.serialization.json.Json


class AuthService {
    private val client = HttpClient(Js) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        // This ensures cookies are sent with requests
        install(HttpCookies) {
            storage = AcceptAllCookiesStorage()
        }

        defaultRequest {
            // Enable credentials (cookies) for all requests
            attributes.put(AttributeKey("credentials"), "include")
        }


    }

    private val baseUrl = "http://localhost:8080/api/auth"

    suspend fun login(name: String, password: String): Result<AuthResponse> {
        return try {
            val response = client.post("$baseUrl/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(name, password))
            }

            response.headers.forEach { name, values ->
                println("  $name: $values")
            }
            println()

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
            val response = client.post("$baseUrl/register") {
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

    suspend fun isAuthenticated(): Boolean {
        return try {
            // Make a call to a protected endpoint to check if user is authenticated
            val response = client.get("${baseUrl}/is-authenticated").body<Result<Boolean>>()
            response.getOrNull() ?: false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getCurrentUser(): UserData? {
        return try {
            val response = client.get("${baseUrl}/me").body<Result<UserData>>()
            response.getOrNull()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun logout() {
        client.get("${baseUrl}/logout")
    }


}