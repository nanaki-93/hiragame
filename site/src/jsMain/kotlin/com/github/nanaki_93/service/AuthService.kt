package com.github.nanaki_93.service

import com.github.nanaki_93.models.LoginRegisterRequest
import com.github.nanaki_93.models.UserData
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json


class AuthService {
    private val client = HttpClientProvider.client

    private val baseUrl = "http://localhost:8080/api/auth"

    suspend fun login(name: String, password: String): HttpStatusCode {

        val response = client.post("$baseUrl/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRegisterRequest(name, password))
        }

        return response.status
    }

    suspend fun register(name: String, password: String): String {

        val response = client.post("$baseUrl/register") {
            contentType(ContentType.Application.Json)
            setBody(LoginRegisterRequest(name, password))
        }
        return response.body()

    }

    suspend fun refreshToken(): String {
        println("refreshToken")
        val response = client.get("${baseUrl}/refresh-token")
        println("refreshToken responseStatus: ${response.status}")
        if (response.status != HttpStatusCode.OK) {
            throw SessionExpiredException("Session expired")
        }
        return response.body<String>()

    }


    suspend fun isAuthenticated(): Boolean {
        return authenticatedRequest(
            name = "isAuthenticated",
            refreshToken = { refreshToken() }
        ) {

            try {

                // Make a call to a protected endpoint to check if user is authenticated
                val response = client.get("${baseUrl}/is-authenticated") {
                    contentType(ContentType.Application.Json)
                }
                println("isAuthenticated responseStatus: ${response.status}")
                if (response.status == Unauthorized) {
                    throw ClientRequestException(response, "isAuthenticated: ${response.status}")
                }
                if (response.status != HttpStatusCode.OK) {
                    println("isAuthenticated responseStatus: ${response.status}")
                    false
                } else {
                    val result = response.body<Boolean>()
                    println("isAuthenticated result: $result")
                    if (!result) {
                        throw ClientRequestException(response, "isAuthenticated: ${response.status}")
                    }
                    result
                }
            }catch (e: Exception ){
                println("isAuthenticated: ${e.message}")
                false
            }

        }
    }

    suspend fun getCurrentUser(): UserData {
        return authenticatedRequest(
            name = "getCurrentUser",
            refreshToken = { refreshToken() }
        ) {
            val response = client.get("$baseUrl/me")
            response.body<UserData>()
        }
    }

    suspend fun logout() {
        client.get("${baseUrl}/logout")
    }


}