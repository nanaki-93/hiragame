package com.github.nanaki_93.service


import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.beans.factory.annotation.Value
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.nanaki_93.models.AIQuestion
import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.repository.HiraganaQuestion
import com.github.nanaki_93.repository.HiraganaQuestionRepository
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.ChatOptions

import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.ollama.OllamaChatModel

import org.springframework.http.HttpStatus

@Service
class AIQuestionService(
    private val ollamaChatModel: OllamaChatModel,
    private val restTemplate: RestTemplate = RestTemplate(),
    private val objectMapper: ObjectMapper = ObjectMapper(),
    private val hiragamaRepository: HiraganaQuestionRepository
) {

    private val logger = LoggerFactory.getLogger(AIQuestionService::class.java)
    private val chatClient = ChatClient.create(ollamaChatModel)

    @Value("\${ai.service.provider:gemini}")
    private lateinit var aiProvider: String

    // OpenAI Configuration (backup)
    @Value("\${openai.api.key:}")
    private lateinit var openAiApiKey: String

    @Value("\${openai.api.url:https://api.openai.com/v1/chat/completions}")
    private lateinit var openAiApiUrl: String

    // Gemini Configuration
    @Value("\${gemini.api.key:}")
    private lateinit var geminiApiKey: String

    @Value("\${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent}")
    private lateinit var geminiApiUrl: String

    @Value("\${ollama.api.url:http://localhost:11434/api/generate}")
    private lateinit var ollamaApiUrl: String

    @Value("\${ollama.model:llama3.2:1b}")
    private lateinit var ollamaModel: String


    fun generateAndStoreWordQuestion(topic: String, difficulty: Int, nQuestions: Int): List<AIQuestion> {
        val response = generateWordQuestion(topic, difficulty, nQuestions)
        val toDB = response.map { el ->
            HiraganaQuestion(
                hiragana = el.hiragana,
                romanization = el.romanization,
                translation = el.translation,
                topic = el.topic,
                difficulty = el.level
            )
        }
        hiragamaRepository.saveAll(toDB)
        return response
    }

    fun generateAndStoreSentenceQuestion(topic: String, difficulty: Int, nQuestions: Int): List<AIQuestion> {
        val response = generateSentenceQuestion(topic, difficulty, nQuestions)
        val toDB = response.map { el ->
            HiraganaQuestion(
                hiragana = el.hiragana,
                romanization = el.romanization,
                translation = el.translation,
                topic = el.topic,
                difficulty = el.level
            )
        }
        hiragamaRepository.saveAll(toDB)
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
        5. Each line must follow this exact CSV format: hiragana,romanization,translation,topic,level
        
        EXAMPLES (do not copy these):
        ひらがな,hiragana,hiragana script,basics,1
        さかな,sakana,fish,food,1
        
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
        5. Each line must follow this exact CSV format: hiragana,romanization,translation,topic,level
        
        EXAMPLES (do not copy these):
        きょうはあついです,kyou wa atsui desu,Today is hot,weather,$difficulty
        わたしはがくせいです,watashi wa gakusei desu,I am a student,school,$difficulty
        
        Generate exactly $nQuestions DIFFERENT sentences about $topic now:
    """.trimIndent()

        val response = callAI(prompt)
        return removeDuplicatesAndParse(response, topic, GameMode.SENTENCE, nQuestions)
    }


    private fun removeDuplicatesAndParse(csvResponse: String, topic: String, gameMode: GameMode, requestedCount: Int): List<AIQuestion> {
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


    private fun parseCsvResponse(csvResponse: String, topic: String, gameMode: GameMode): List<AIQuestion> {
        try {
            logger.info("Parsing CSV response: $csvResponse")

            val cleanedResponse = cleanCsvFromMarkdown(csvResponse.trim())
            val questions = mutableListOf<AIQuestion>()

            cleanedResponse.lines().forEach { line ->
                if (line.isNotBlank() && line.contains(",")) {
                    try {
                        val parts = line.split(",").map { it.trim().removeSurrounding("\"") }
                        if (parts.size >= 4) {
                            questions.add(
                                AIQuestion(
                                    hiragana = parts[0],
                                    romanization = parts[1],
                                    translation = parts[2],
                                    topic = parts.getOrNull(3) ?: topic,
                                    level = parts.getOrNull(4)?.toIntOrNull() ?: 1
                                )
                            )
                        }
                    } catch (e: Exception) {
                        logger.warn("Failed to parse CSV line: $line", e)
                        // Continue with next line instead of failing completely
                    }
                }
            }

            logger.info("Successfully parsed ${questions.size} questions from CSV")
            return questions.ifEmpty { getFallback(topic, gameMode) }

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

    private fun callAI(prompt: String): String {
        return when (aiProvider.lowercase()) {
            "gemini" -> callGemini(prompt)
            "openai" -> callOpenAI(prompt)
            "ollama" -> callOllamaWithSpringAI(prompt)
            else -> {
                logger.warn("Unknown AI provider: $aiProvider, falling back to Local Ollama")
                callOllama(prompt)
            }

        }
    }

    private fun callOllamaWithSpringAI(prompt: String): String {
        return try {
            logger.info("Calling Ollama via Spring AI with anti-repetition settings")

            val response = chatClient.prompt()
                .user(prompt)
                .options(ChatOptions.builder()
                    .temperature(0.8)
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
            callOllama(prompt)
        }
    }


    private fun callOllama(prompt: String): String {
        return try {
            val requestBody = mapOf(
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

            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
            }

            val request = HttpEntity(requestBody, headers)

            logger.info("Calling Ollama API with anti-repetition settings")
            val response = restTemplate.postForEntity(ollamaApiUrl, request, String::class.java)

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


    private fun callGemini(prompt: String): String {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }

        val requestBody = mapOf(
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

        val request = HttpEntity(requestBody, headers)
        val urlWithKey = "$geminiApiUrl?key=$geminiApiKey"

        try {
            val response = restTemplate.postForObject(urlWithKey, request, String::class.java)
            return extractGeminiContent(response ?: "")
        } catch (e: Exception) {
            logger.error("Failed to call Gemini API: ${e.message}")
            Thread.sleep(5000)
            throw e
        }
    }


    private fun extractGeminiContent(response: String): String {
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

    private fun cleanJsonFromMarkdown(text: String): String {
        // Remove markdown code block markers if present
        return text
            .replace("```json", "")
            .replace("```", "")
            .trim()
    }


    private fun callOpenAI(prompt: String): String {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(openAiApiKey)
        }

        val requestBody = mapOf(
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

        val request = HttpEntity(requestBody, headers)

        try {
            val response = restTemplate.postForObject(openAiApiUrl, request, String::class.java)
            return extractContentFromResponse(response ?: "")
        } catch (e: Exception) {
            // Fallback to predefined content if AI call fails
            logger.error("Failed to call OpenAI API: ${e.message}")
            return "Failed to call OpenAI API"
        }
    }

    private fun extractContentFromResponse(response: String): String {
        try {
            val jsonNode: JsonNode = objectMapper.readTree(response)
            return jsonNode.path("choices").get(0).path("message").path("content").asText()
        } catch (e: Exception) {
            logger.error("Failed to parse AI response: ${e.message}")
            return "Failed to parse AI response"
        }
    }


    private fun parseResponse(aiResponse: String, topic: String, gameMode: GameMode): List<AIQuestion> {
        try {
            logger.info("Parsing sentence response: $aiResponse")

            // Clean the response first
            val cleanedResponse = cleanJsonFromMarkdown(aiResponse.trim())
            logger.info("Cleaned sentence response: $cleanedResponse")

            val jsonNode: JsonNode = objectMapper.readTree(cleanedResponse)
            return jsonNode.map { node ->
                AIQuestion(
                    hiragana = node.path("hiragana").asText(),
                    romanization = node.path("romanization").asText(),
                    translation = node.path("translation").asText(),
                    topic = topic,
                    level = node.path("level").asInt()
                )
            }

        } catch (e: Exception) {
            logger.error("Failed to parse sentence response: ${e.message}")
            // Fallback sentence based on topic
            return getFallback(topic, gameMode)
        }
    }


    private fun getFallback(topic: String, gameMode: GameMode): List<AIQuestion> {
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