package com.github.nanaki_93.service

import com.github.nanaki_93.models.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.Unauthorized


class GameService(appConfig: AppConfig, private val authService: AuthService) {

    private val client = HttpClientProvider.client

    private val baseUrl: String = appConfig.apiUrl

    suspend fun processAnswer(answer: UserQuestionDto): GameStateUi {

        return authenticatedRequest(
            name = "processAnswer",
            refreshToken = { authService.refreshToken() }
        ) {
            val response = client.post("${baseUrl}/process-answer") {
                contentType(ContentType.Application.Json)
                setBody(answer)
            }
            println("processAnswer: ${response.status}")
            if (response.status == Unauthorized) {
                throw ClientRequestException(response, "isAuthenticated: ${response.status}")
            }

            response.body()
        }
    }

    suspend fun getNextQuestion(selectRequest: SelectRequest): QuestionDto {

        return authenticatedRequest(
            name = "getNextQuestion",
            refreshToken = { authService.refreshToken() }
        ) {
            val response = client.post("${baseUrl}/next-question") {
                contentType(ContentType.Application.Json)
                setBody(selectRequest)
            }
            if (response.status == Unauthorized) {
                throw ClientRequestException(response, "isAuthenticated: ${response.status}")
            }
            response.body()
        }
    }


    suspend fun selectGameMode(req: LevelListRequest): List<Level> {

        return authenticatedRequest(
            name = "selectGameMode",
            refreshToken = { authService.refreshToken() }
        ) {
            val response = client.post("${baseUrl}/select-game-mode") {
                contentType(ContentType.Application.Json)
                setBody(req)
            }
            println("selectGameMode: ${response.status}")
            if (response.status == Unauthorized) {
                throw ClientRequestException(response, "isAuthenticated: ${response.status}")
            }

            response.body()
        }
    }

    suspend fun getGameState(userId: String): GameStateUi {

        return authenticatedRequest(
            name = "getGameState",
            refreshToken = { authService.refreshToken() }
        ) {
            val response = client.post("${baseUrl}/get-game-state") {
                contentType(ContentType.Application.Json)
                setBody(userId)
            }
            println("getGameState: ${response.status}")
            if (response.status == Unauthorized) {
                throw ClientRequestException(response, "isAuthenticated: ${response.status}")
            }
            response.body()
        }
    }

    private suspend fun post(uri: String, reqBody: Any): HttpResponse {
        return authenticatedRequest(
            name = "postGameService: $uri",
            refreshToken = { authService.refreshToken() }
        ) {
            client.post("${baseUrl}${uri}") {
                contentType(ContentType.Application.Json)
                setBody(reqBody)
            }
        }
    }


}