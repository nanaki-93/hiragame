package com.github.nanaki_93.service


import com.github.nanaki_93.models.AIWordQuestion
import com.github.nanaki_93.models.AISentenceQuestion
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.beans.factory.annotation.Value
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory

@Service
class AIQuestionService(
    private val restTemplate: RestTemplate = RestTemplate(),
    private val objectMapper: ObjectMapper = ObjectMapper()
) {

    private val logger = LoggerFactory.getLogger(AIQuestionService::class.java)
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


    fun generateWordQuestion(topic: String): List<AIWordQuestion> {
        val prompt = """
            Generate exactly 10 Japanese words related to the topic "$topic" using ONLY hiragana characters.
            The words should be appropriate for Japanese learners and use common vocabulary.
            
            IMPORTANT: Respond with ONLY valid JSON, no markdown formatting or code blocks.
            Use this exact JSON format:
            [
                {
                    "hiraganaWord": "ひらがな",
                    "romanization": "hiragana",
                    "englishWord": "english translation",
                    "topic": "$topic"
                },
                {
                    "hiraganaWord": "たべもの",
                    "romanization": "tabemono",
                    "englishWord": "food",
                    "topic": "$topic"
                }
            ]
            
            Generate 10 complete entries following this exact pattern.
        """.trimIndent()

        val response = callAI(prompt)
        return parseWordResponse(response, topic)
    }


    fun generateSentenceQuestion(topic: String): List<AISentenceQuestion> {
        val prompt = """
            Generate exactly 10 simple Japanese sentences related to the topic "$topic" using ONLY hiragana characters.
            The sentences should be appropriate for Japanese learners, grammatically correct, and easy to understand.
            
            IMPORTANT: Respond with ONLY valid JSON, no markdown formatting or code blocks.
            Use this exact JSON format:
            [
                {
                    "hiraganaSentence": "きょうはいいてんきです",
                    "romanization": "kyou wa ii tenki desu",
                    "englishSentence": "It's a nice day today",
                    "topic": "$topic"
                },
                {
                    "hiraganaSentence": "あさごはんをたべます",
                    "romanization": "asagohan wo tabemasu",
                    "englishSentence": "I eat breakfast",
                    "topic": "$topic"
                }
            ]
            
            Generate 10 complete entries following this exact pattern.
        """.trimIndent()

        val response = callAI(prompt)
        logger.info("AI Response: $response")
        return parseSentenceResponse(response, topic)
    }


    private fun callAI(prompt: String): String {
        return when (aiProvider.lowercase()) {
            "gemini" -> callGemini(prompt)
            "openai" -> callOpenAI(prompt)
            else -> callGemini(prompt) // default to free Gemini
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
                "temperature" to 0.7,
                "topK" to 64,
                "topP" to 0.95,
                "maxOutputTokens" to 800, // Increased from 150 to 800
                "responseMimeType" to "text/plain"
            )
        )

        val request = HttpEntity(requestBody, headers)
        val urlWithKey = "$geminiApiUrl?key=$geminiApiKey"

        try {
            val response = restTemplate.postForObject(urlWithKey, request, String::class.java)
            logger.info("Gemini Response: $response")
            return extractGeminiContent(response ?: "")
        } catch (e: Exception) {
            logger.error("Failed to call Gemini API: ${e.message}")
            return "Failed to call Gemini API"
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

    private fun parseWordResponse(aiResponse: String, topic: String): List<AIWordQuestion> {
        try {

            // Clean the response first
            val cleanedResponse = cleanJsonFromMarkdown(aiResponse.trim())
            val jsonNode: JsonNode = objectMapper.readTree(cleanedResponse)

            return jsonNode.map { node ->
                AIWordQuestion(
                    hiraganaWord = node.path("hiraganaWord").asText(),
                    romanization = node.path("romanization").asText(),
                    englishWord = node.path("englishWord").asText(),
                    topic = topic
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to parse word response: ${e.message}")
            // Fallback word based on topic
            return getFallbackWord(topic)
        }
    }


    private fun parseSentenceResponse(aiResponse: String, topic: String): List<AISentenceQuestion> {
        try {
            logger.info("Parsing sentence response: $aiResponse")

            // Clean the response first
            val cleanedResponse = cleanJsonFromMarkdown(aiResponse.trim())
            logger.info("Cleaned sentence response: $cleanedResponse")

            val jsonNode: JsonNode = objectMapper.readTree(cleanedResponse)
            return jsonNode.map { node ->
                AISentenceQuestion(
                    hiraganaSentence = node.path("hiraganaSentence").asText(),
                    romanization = node.path("romanization").asText(),
                    englishSentence = node.path("englishSentence").asText(),
                    topic = topic
                )
            }

        } catch (e: Exception) {
            logger.error("Failed to parse sentence response: ${e.message}")
            // Fallback sentence based on topic
            return getFallbackSentence(topic)
        }
    }


    private fun getFallbackWord(topic: String): List<AIWordQuestion> {
        val fallbacks = mapOf(
            "food" to listOf(AIWordQuestion("たべもの", "tabemono", " ",topic)),
            "animals" to listOf(AIWordQuestion("いぬ", "inu", " ",topic)),
            "colors" to listOf(AIWordQuestion("あか", "aka", " ",topic)),
            "family" to listOf(AIWordQuestion("かぞく", "kazoku", " ",topic)),
            "school" to listOf(AIWordQuestion("がっこう", "gakkou", " ",topic)),
            "weather" to listOf(AIWordQuestion("てんき", "tenki", " ",topic)),
            "time" to listOf(AIWordQuestion("じかん", "jikan", " ",topic)),
            "body" to listOf(AIWordQuestion("からだ", "karada", " ",topic)),
            "house" to listOf(AIWordQuestion("いえ", "ie", " ",topic)),
            "nature" to listOf(AIWordQuestion("しぜん", "shizen", " ",topic))
        )

        return fallbacks[topic] ?: listOf(AIWordQuestion("にほん", "nihon", "",topic))
    }

    private fun getFallbackSentence(topic: String): List<AISentenceQuestion> {
        val fallbacks = mapOf(
            "daily life" to listOf(AISentenceQuestion("きょうはいいてんきです", "kyou wa ii tenki desu", "daily life", topic)),
            "food" to listOf(AISentenceQuestion("ばんごはんをたべます", "bangohan wo tabemasu", " food", topic)),
            "animals" to listOf(AISentenceQuestion("いぬがすきです", "inu ga suki desu", " animals", topic)),
            "school" to listOf(AISentenceQuestion("がっこうにいきます", "gakkou ni ikimasu", " school", topic)),
            "family" to listOf(AISentenceQuestion("かぞくとすんでいます", "kazoku to sunde imasu", " family", topic)),
            "weather" to listOf(AISentenceQuestion("あめがふっています", "ame ga futte imasu", " weather", topic)),
            "time" to listOf(AISentenceQuestion("いまなんじですか", "ima nanji desu ka", " time", topic)),
            "greeting" to listOf(AISentenceQuestion("おはようございます", "ohayou gozaimasu", " greeting", topic)),
            "shopping" to listOf(AISentenceQuestion("みせでかいものをします", "mise de kaimono wo shimasu", " shopping", topic)),
            "travel" to listOf(AISentenceQuestion("りょこうにいきたいです", "ryokou ni ikitai desu", " travel", topic))
        )

        return fallbacks[topic] ?:listOf( AISentenceQuestion("にほんごをべんきょうします", "nihongo wo benkyou shimasu","", topic))
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