package com.github.nanaki_93.service.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.nanaki_93.util.CleanUtil.cleanJsonFromMarkdown
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
@Profile("gemini", "default")
class GeminiService(
    private val objectMapper: ObjectMapper = ObjectMapper(),
    private val restTemplate: RestTemplate = RestTemplate()
) : AiService {

    private val logger = org.slf4j.LoggerFactory.getLogger(GeminiService::class.java)
    @Value("\${gemini.api.key:}")
    private lateinit var geminiApiKey: String
    @Value("\${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent}")
    private lateinit var geminiApiUrl: String


    override fun callApi(prompt: String): String {
        val response =
            restTemplate.postForObject("$geminiApiUrl?key=$geminiApiKey", getRequest(prompt), String::class.java) ?: ""
        return extractContentFromResponse(response)
    }


    fun getRequest(prompt: String): HttpEntity<Map<String, Any>> =
        HttpEntity(getRequestBody(prompt), HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        })

    private fun extractContentFromResponse(response: String): String {
        try {
            val jsonNode: JsonNode = objectMapper.readTree(response)
            val textContent = jsonNode.path("candidates")
                .get(0)
                .path("content")
                .path("parts")
                .get(0)
                .path("text")
                .asText()

            // Clean up markdown code blocks if present
            return cleanJsonFromMarkdown(textContent)
        } catch (e: Exception) {
            logger.error("Failed to parse Gemini response: ${e.message}")
            return "Failed to parse Gemini response"
        }
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