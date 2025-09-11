package com.github.nanaki_93.service

import com.github.nanaki_93.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json


class AuthService {
    private val client = HttpClient(Js) {

        install(HttpCookies) {
            storage = AcceptAllCookiesStorage()
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        engine {
            configureRequest { credentials = "include" }
        }

    }

    private val baseUrl = "http://localhost:8080/api/auth"

    suspend fun login(name: String, password: String): String {

        val response = client.post("$baseUrl/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRegisterRequest(name, password))
        }
        return response.body()
    }

    suspend fun register(name: String, password: String): String {

        val response = client.post("$baseUrl/register") {
            contentType(ContentType.Application.Json)
            setBody(LoginRegisterRequest(name, password))
        }
        return response.body()

    }

    //TODO manage token expiration and refresh
    suspend fun refreshToken(): String {
        val response = client.get("${baseUrl}/refresh-token")
        return response.body<String>()
    }
    suspend fun isAuthenticated(): Boolean {
        // Make a call to a protected endpoint to check if user is authenticated
        val response = client.get("${baseUrl}/is-authenticated")
        return response.body<Boolean>()
    }

    suspend fun getCurrentUser(): UserData {
        val response = client.get("${baseUrl}/me")
        return response.body<UserData>()
    }

    suspend fun logout() {
        client.get("${baseUrl}/logout")
    }


}