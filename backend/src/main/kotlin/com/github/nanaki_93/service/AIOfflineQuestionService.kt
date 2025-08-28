package com.github.nanaki_93.service


import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.beans.factory.annotation.Value
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.nanaki_93.config.ai.AiConfig
import com.github.nanaki_93.config.ai.OpenAiConfig
import com.github.nanaki_93.models.AIQuestion
import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.repository.HiraganaQuestion
import com.github.nanaki_93.repository.HiraganaQuestionRepository
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.ChatOptions

import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.context.annotation.Profile

import org.springframework.http.HttpStatus

@Service
@Profile("offline")
class AIOfflineQuestionService(
    ollamaChatModel: OllamaChatModel,
    hiragamaRepository: HiraganaQuestionRepository,
) : AIQuestionService(hiragamaRepository){

    private val logger = LoggerFactory.getLogger(AIOfflineQuestionService::class.java)
    private val chatClient = ChatClient.create(ollamaChatModel)

    override fun callAI(prompt: String): String {
        return callOllamaWithSpringAI(prompt)
    }

    private fun callOllamaWithSpringAI(prompt: String): String {
        return try {
            logger.info("Calling Ollama via Spring AI with anti-repetition settings")

            val response = chatClient.prompt()
                .user(prompt)
                .options(ChatOptions.builder()
                    .temperature(0.1)
                    .topK(40)
                    .topP(0.9)
                    .build())
                .call()
                .content()

            logger.info("Received response from Spring AI: ${response?.take(100)}...")
            response ?: ""

        } catch (e: Exception) {
            logger.error("Error calling Ollama via Spring AI: ${e.message}", e)
            // Fallback to direct call
            getFallback("food", GameMode.WORD).joinToString { "" }
        }
    }



}