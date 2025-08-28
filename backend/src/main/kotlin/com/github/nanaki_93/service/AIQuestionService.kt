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

import org.springframework.http.HttpStatus

@Service
abstract class AIQuestionService(
    private val hiragamaRepository: HiraganaQuestionRepository,
) {
    @Value("\${ai.service.provider:gemini}")
    private lateinit var aiProvider: String

    private val logger = LoggerFactory.getLogger(AIQuestionService::class.java)


    abstract fun callAI(prompt: String): String

    fun generateAndStoreWordQuestion(topic: String, difficulty: Int, nQuestions: Int): List<AIQuestion> {
        val response = generateWordQuestion(topic, difficulty, nQuestions)
        return storeQuestions(response)
    }

    fun generateAndStoreSentenceQuestion(topic: String, difficulty: Int, nQuestions: Int): List<AIQuestion> {
        val response = generateSentenceQuestion(topic, difficulty, nQuestions)
        return storeQuestions(response)
    }

    private fun storeQuestions(response: List<AIQuestion>): List<AIQuestion> {
        val toDB = response.map { el ->
            HiraganaQuestion(
                hiragana = el.hiragana,
                romanization = el.romanization,
                translation = el.translation,
                topic = el.topic,
                difficulty = el.level
            )
        }
        val toSave =
            toDB.filter { question -> hiragamaRepository.findHiraganaQuestionByHiragana(question.hiragana) == null }

        hiragamaRepository.saveAll(toSave)
        return response
    }

    fun generateWordQuestion(topic: String, level: Int, nQuestions: Int): List<AIQuestion> {
        val prompt = """
        You are a Japanese language teacher. Generate exactly $nQuestions UNIQUE Japanese words related to "$topic".
        
        STRICT REQUIREMENTS:
        1. Each word must be DIFFERENT (no duplicates)
        2. Use ONLY hiragana characters
        3. Words must be common and appropriate for Japanese learners
        4. Difficulty level: $level (1=beginner, 5=advanced)
        5. Each line must follow this exact CSV format: hiragana;romanization;translation;topic;level
        
        EXAMPLES (do not copy these):
        ひらがな;hiragana;hiragana script;basics;1
        さかな;sakana;fish;food;1
        
        Generate exactly $nQuestions DIFFERENT words about $topic now:
    """.trimIndent()

        val response = callAI(prompt)
        return removeDuplicatesAndParse(response, topic, GameMode.WORD, nQuestions)
    }

    fun generateSentenceQuestion(topic: String, difficulty: Int, nQuestions: Int): List<AIQuestion> {
        val prompt = """
        You are a Japanese language teacher. Generate exactly $nQuestions UNIQUE Japanese sentences about "$topic".
        
        STRICT REQUIREMENTS:
        1. Each sentence must be DIFFERENT (no duplicates)
        2. Use ONLY hiragana characters
        3. Sentences must be simple and grammatically correct
        4. Appropriate for Japanese learners at difficulty level $difficulty
        5. Each line must follow this exact CSV format: hiragana;romanization;translation;topic;level
        
        EXAMPLES (do not copy these):
        きょうはあついです;kyou wa atsui desu;Today is hot;weather;$difficulty
        わたしはがくせいです;watashi wa gakusei desu;I am a student;school;$difficulty
        
        Generate exactly $nQuestions DIFFERENT sentences about $topic now:
    """.trimIndent()

        val response = callAI(prompt)
        return removeDuplicatesAndParse(response, topic, GameMode.SENTENCE, nQuestions)
    }


    private fun removeDuplicatesAndParse(
        csvResponse: String,
        topic: String,
        gameMode: GameMode,
        requestedCount: Int
    ): List<AIQuestion> {
        try {
            logger.info("Parsing CSV response and removing duplicates")

            val cleanedResponse = cleanCsvFromMarkdown(csvResponse.trim())
            val questions = mutableListOf<AIQuestion>()
            val seenHiragana = mutableSetOf<String>()

            cleanedResponse.lines().forEach { line ->
                if (line.isNotBlank() && line.contains(",")) {
                    try {
                        val parts = line.split(",").map { it.trim().removeSurrounding("\"") }
                        if (parts.size >= 4) {
                            val hiragana = parts[0]

                            // Skip if we've already seen this hiragana
                            if (!seenHiragana.contains(hiragana)) {
                                seenHiragana.add(hiragana)
                                questions.add(
                                    AIQuestion(
                                        hiragana = hiragana,
                                        romanization = parts[1],
                                        translation = parts[2],
                                        topic = parts.getOrNull(3) ?: topic,
                                        level = parts.getOrNull(4)?.toIntOrNull() ?: 1
                                    )
                                )
                            } else {
                                logger.debug("Skipping duplicate hiragana: $hiragana")
                            }
                        }
                    } catch (e: Exception) {
                        logger.warn("Failed to parse CSV line: $line", e)
                    }
                }
            }

            logger.info("Successfully parsed ${questions.size} unique questions from ${cleanedResponse.lines().size} total lines")

            // If we don't have enough unique questions, generate more or use fallback
            return if (questions.size >= requestedCount) {
                questions.take(requestedCount)
            } else {
                logger.warn("Only got ${questions.size} unique questions, requested $requestedCount")
                if (questions.isEmpty()) {
                    getFallback(topic, gameMode)
                } else {
                    questions
                }
            }

        } catch (e: Exception) {
            logger.error("Failed to parse CSV response: ${e.message}")
            return getFallback(topic, gameMode)
        }
    }

    private fun cleanCsvFromMarkdown(text: String): String {
        return text
            .replace("```csv", "")
            .replace("```", "")
            .replace("CSV:", "")
            .trim()
            .lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .joinToString("\n")
    }

    protected fun getFallback(topic: String, gameMode: GameMode): List<AIQuestion> {
        return when (gameMode) {
            GameMode.WORD -> listOf(AIQuestion("たべもの", "tabemono", " ", topic))
            GameMode.SENTENCE -> listOf(
                AIQuestion(
                    "きょうはいいてんきです",
                    "kyou wa ii tenki desu",
                    "daily life",
                    topic
                )
            )

            else -> listOf(AIQuestion("きょうはいいてんきです", "kyou wa ii tenki desu", "daily life", topic))
        }
    }

    // Method to get available topics
    fun getAvailableTopics(): List<String> {
        return listOf(
            "food", "animals", "colors", "family", "school",
            "weather", "time", "body", "house", "nature",
            "daily life", "greeting", "shopping", "travel"
        )
    }
}