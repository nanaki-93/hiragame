package com.github.nanaki_93.service

import com.github.nanaki_93.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlin.text.get


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
        return authenticatedRequest(
            refreshToken = { refreshToken() }
        ) {
            // Make a call to a protected endpoint to check if user is authenticated
            val response = client.get("${baseUrl}/is-authenticated")
            if (response.status != HttpStatusCode.OK) {
                false
            } else {
                response.body<Boolean>()
            }
        }

    }

    suspend fun getCurrentUser(): UserData {
        return authenticatedRequest(
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