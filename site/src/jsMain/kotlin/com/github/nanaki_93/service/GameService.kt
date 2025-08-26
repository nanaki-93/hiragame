package com.github.nanaki_93.service

import com.github.nanaki_93.models.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
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

    suspend fun processAnswer(
        gameState: GameState,
        userInput: String,
        level: Int,
    ): GameState {
        val req = ProcessAnswerRequest(gameState, userInput, level)
        return client.post("${baseUrl}/api/process-answer") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()
    }

    suspend fun getNextCharacterAndClearFeedback(gameState: GameState): GameState {
        val req = GameStateRequest(gameState)
        return client.post("${baseUrl}/api/next-character") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()
    }

    suspend fun selectGameMode(gameMode: GameMode): GameState {
        val req = GameModeRequest(gameMode)
        return client.post("${baseUrl}/api/game-mode") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()
    }
}