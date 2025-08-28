package com.github.nanaki_93.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.nanaki_93.config.ai.AiConfig
import com.github.nanaki_93.repository.HiraganaQuestionRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus

import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import kotlin.text.get

@Service
@Profile("online","default")
class AIOnlineService(
    private val restTemplate: RestTemplate = RestTemplate(),
    private val aiConfig: AiConfig,
    hiraganaQuestionRepository: HiraganaQuestionRepository
): AIQuestionService(
    hiraganaQuestionRepository
) {
    private val logger = LoggerFactory.getLogger(AIOnlineService::class.java)

    override fun callAI(prompt: String): String {
        return try {
            aiConfig.callApi(prompt)
        } catch (e: Exception) {
            // Fallback to predefined content if AI call fails
            logger.error("Failed to call OpenAI API: ${e.message}")
            "Failed to call OpenAI API"
        }
    }












}