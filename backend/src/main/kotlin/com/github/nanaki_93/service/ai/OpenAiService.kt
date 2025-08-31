package com.github.nanaki_93.service.ai

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate


@Service
@Profile("openai")
class OpenAiService(
    private val objectMapper: ObjectMapper = ObjectMapper(),
    private val restTemplate: RestTemplate = RestTemplate()
) : AiService {

    private val logger = org.slf4j.LoggerFactory.getLogger(OpenAiService::class.java)

    @Value("\${openai.api.key:}")
    private lateinit var openAiApiKey: String

    @Value("\${openai.api.url:https://api.openai.com/v1/chat/completions}")
    lateinit var openAiApiUrl: String


    override fun callApi(prompt: String): String =
        runCatching {
            restTemplate.postForObject(openAiApiUrl, getRequest(prompt), String::class.java)
        }
            .map { it ?: "" }
            .map(::extractContentFromResponse)
            .onFailure { logger.error("Failed to call OpenAI API: ${it.message}") }
            .getOrDefault("Failed to call OpenAI API")

    fun getRequest(prompt: String): HttpEntity<Map<String, Any>> =
        HttpEntity(openAIReqBody(prompt), openAIHeaders(openAiApiKey))

    private fun openAIHeaders(openAiApiKey: String) = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        setBearerAuth(openAiApiKey)
    }

    private fun openAIReqBody(prompt: String) = mapOf(
        "model" to "gpt-3.5-turbo",
        "messages" to listOf(
            mapOf(
                "role" to "user",
                "content" to prompt
            )
        ),
        "max_tokens" to 150,
        "temperature" to 0.7
    )


    private fun extractContentFromResponse(response: String): String =
        runCatching {
            objectMapper.readTree(response)
                .path("choices")
                .get(0).path("message").path("content").asText()
        }
            .onFailure { logger.error("Failed to parse AI response: ${it.message}") }
            .getOrElse { "Failed to parse AI response" }

}