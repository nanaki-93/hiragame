package com.github.nanaki_93.config.ai

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service

@Service
class GeminiConfig : AiConfig {

    // Gemini Configuration
    @Value("\${gemini.api.key:}")
    private lateinit var geminiApiKey: String

    @Value("\${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent}")
    private lateinit var geminiApiUrl: String


    override fun getApiUrl(): String = "$geminiApiUrl?key=$geminiApiKey"


    override fun getRequest(prompt: String): HttpEntity<Map<String, Any>> {
        return HttpEntity(getRequestBody(prompt), HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        })
    }


    private fun getRequestBody(prompt: String) = mapOf(
        "contents" to listOf(
            mapOf(
                "parts" to listOf(
                    mapOf("text" to prompt)
                )
            )
        ),
        "generationConfig" to mapOf(
            "temperature" to 0.8,
            "topK" to 40, // Lower for more focused responses
            "topP" to 0.95,
            "maxOutputTokens" to 2048,
            "responseMimeType" to "text/plain",
            "stopSequences" to listOf<String>() // Let it generate full CSV
        )
    )


}