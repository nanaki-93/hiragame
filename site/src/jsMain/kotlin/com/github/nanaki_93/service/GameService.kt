package com.github.nanaki_93.service

import com.github.nanaki_93.models.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class GameService {
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

    private val baseUrl: String = "http://localhost:8080/api" // TODO: externalize

    suspend fun processAnswer(user: UserQuestionDto): GameStateUi = post("/process-answer", user).body()
    suspend fun getNextQuestion(selectRequest: SelectRequest): QuestionUi = post("/next-question", selectRequest).body()
    suspend fun selectGameMode(req: LevelListRequest): List<Level> = post("/select-game-mode", req).body()
    suspend fun getGameState(userId: String): GameStateUi = post("/get-game-state", userId).body()

    private suspend fun post(uri: String, reqBody: Any): HttpResponse = client.post("${baseUrl}${uri}") {
        contentType(ContentType.Application.Json)
        setBody(reqBody)
    }



}