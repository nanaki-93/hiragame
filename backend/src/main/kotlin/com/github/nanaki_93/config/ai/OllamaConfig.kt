package com.github.nanaki_93.config.ai

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType

class OllamaConfig : AiConfig {

    @Value("\${ollama.api.url:http://localhost:11434/api/generate}")
    private lateinit var ollamaApiUrl: String

    @Value("\${ollama.model:llama3.2:1b}")
    private lateinit var ollamaModel: String


    override fun getApiUrl(): String = ollamaApiUrl

    override fun getRequest(prompt: String): HttpEntity<Map<String, Any>> = HttpEntity(getRequestBody(prompt),
        HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        })


    fun getRequestBody(prompt: String) = mapOf(
        "model" to ollamaModel,
        "prompt" to prompt,
        "stream" to false,
        "options" to mapOf(
            "temperature" to 0.8,      // Higher creativity
            "top_p" to 0.9,           // Nucleus sampling
            "top_k" to 40,            // Limit vocabulary choices
            "repeat_penalty" to 1.1,   // Penalize repetition
            "num_predict" to 500       // Limit response length
        )
    )


}