package com.github.nanaki_93.config.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate


@Service
@Profile("openai")
class OpenAiConfig(
    private val objectMapper: ObjectMapper = ObjectMapper(),
    private val restTemplate: RestTemplate = RestTemplate()
) : AiConfig {

    private val logger = org.slf4j.LoggerFactory.getLogger(OpenAiConfig::class.java)

    @Value("\${openai.api.key:}")
    private lateinit var openAiApiKey: String

    @Value("\${openai.api.url:https://api.openai.com/v1/chat/completions}")
    lateinit var openAiApiUrl: String


    override fun callApi(prompt: String): String {
        val response = restTemplate.postForObject(getApiUrl(), getRequest(prompt), String::class.java) ?: ""
        return extractContentFromResponse(response)
    }

    override fun getApiUrl(): String = openAiApiUrl
    override fun getRequest(prompt: String): HttpEntity<Map<String, Any>> =
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

    private fun extractContentFromResponse(response: String): String {
        try {
            val jsonNode: JsonNode = objectMapper.readTree(response)
            return jsonNode.path("choices").get(0).path("message").path("content").asText()
        } catch (e: Exception) {
            logger.error("Failed to parse AI response: ${e.message}")
            return "Failed to parse AI response"
        }
    }
}