package com.github.nanaki_93.service.ai

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
@Profile("ollama")
class OllamaService(
    private val objectMapper: ObjectMapper = ObjectMapper(),
    private val restTemplate: RestTemplate = RestTemplate()
) : AiService {

    private val logger = org.slf4j.LoggerFactory.getLogger(OllamaService::class.java)
    @Value("\${ollama.api.url:http://localhost:11434/api/generate}")
    private lateinit var ollamaApiUrl: String

    @Value("\${ollama.model:llama3.2:1b}")
    private lateinit var ollamaModel: String


    fun getRequest(prompt: String): HttpEntity<Map<String, Any>> = HttpEntity(getRequestBody(prompt),
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

    override fun callApi(prompt: String): String {
        return try {

            logger.info("Calling Ollama API with anti-repetition settings")
            val response = restTemplate.postForEntity(ollamaApiUrl, getRequest(prompt), String::class.java)

            if (response.statusCode == HttpStatus.OK) {
                extractOllamaContent(response.body ?: "")
            } else {
                logger.error("Ollama API call failed with status: ${response.statusCode}")
                ""
            }
        } catch (e: Exception) {
            logger.error("Error calling Ollama API: ${e.message}", e)
            ""
        }
    }
    private fun extractOllamaContent(response: String): String {
        return try {
            val jsonNode = objectMapper.readTree(response)
            jsonNode.get("response")?.asText() ?: ""
        } catch (e: Exception) {
            logger.error("Error extracting content from Ollama response: ${e.message}", e)
            ""
        }
    }

}