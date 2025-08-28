package com.github.nanaki_93.config.ai

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service


@Service
class OpenAiConfig : AiConfig{


    @Value("\${openai.api.key:}")
    private lateinit var openAiApiKey: String

    @Value("\${openai.api.url:https://api.openai.com/v1/chat/completions}")
    lateinit var openAiApiUrl: String





    override fun getApiUrl():String = openAiApiUrl

    override fun getRequest(prompt: String) : HttpEntity<Map<String, Any>> = HttpEntity(openAIReqBody(prompt), openAIHeaders(openAiApiKey))

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
}