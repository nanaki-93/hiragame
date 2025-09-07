package com.github.nanaki_93.service

import com.github.nanaki_93.models.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HeadersBuilder
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class GameService {
    private val client = HttpClient(Js) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val baseUrl: String = "http://localhost:8080" // TODO: externalize


    private fun buildAuthHeaders(): HeadersBuilder = HeadersBuilder().apply {
        val token = TokenManager.getToken()
        if (token != null) {
            append("Authorization", "Bearer $token")
        }
    }


    suspend fun processAnswer(user: UserQuestionDto ): GameStateUi {

        return client.post("${baseUrl}/api/process-answer") {
            contentType(ContentType.Application.Json)
            headers{
                buildAuthHeaders()
            }
            setBody(user)
        }.body()
    }

    suspend fun getNextQuestion(selectRequest: SelectRequest): QuestionUi {
        return client.post("${baseUrl}/api/next-question") {
            contentType(ContentType.Application.Json)
            headers{
                buildAuthHeaders()
            }
            setBody(selectRequest)
        }.body()
    }

    suspend fun selectGameMode(req: LevelListRequest): List<Level> {
        return client.post("${baseUrl}/api/select-game-mode") {
            contentType(ContentType.Application.Json)
            headers{
                buildAuthHeaders()
            }
            setBody(req)
        }.body()
    }

    suspend fun getGameState(userId: String): GameStateUi {
        return client.post("${baseUrl}/api/get-game-state") {
            contentType(ContentType.Application.Json)
            headers{
                buildAuthHeaders()
            }
            setBody(userId)
        }.body()
    }


}