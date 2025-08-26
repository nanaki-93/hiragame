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

@Service
class AIQuestionService(
    private val restTemplate: RestTemplate = RestTemplate(),
    private val objectMapper: ObjectMapper = ObjectMapper()
) {

    @Value("\${openai.api.key:}")
    private lateinit var openAiApiKey: String

    @Value("\${openai.api.url:https://api.openai.com/v1/chat/completions}")
    private lateinit var openAiApiUrl: String

    fun generateWordQuestion(topic: String): AIWordQuestion {
        val prompt = """
            Generate a Japanese word related to the topic "$topic" using ONLY hiragana characters.
            The word should be appropriate for Japanese learners and use common vocabulary.
            
            Respond in this exact JSON format:
            {
                "hiraganaWord": "ひらがなword",
                "romanization": "romanized version",
                "topic": "$topic"
            }
            
            Example for topic "food":
            {
                "hiraganaWord": "たべもの",
                "romanization": "tabemono",
                "topic": "food"
            }
        """.trimIndent()

        val response = callOpenAI(prompt)
        return parseWordResponse(response, topic)
    }

    fun generateSentenceQuestion(topic: String): AISentenceQuestion {
        val prompt = """
            Generate a simple Japanese sentence related to the topic "$topic" using ONLY hiragana characters.
            The sentence should be appropriate for Japanese learners, grammatically correct, and easy to understand.
            
            Respond in this exact JSON format:
            {
                "hiraganaSentence": "ひらがなsentence",
                "romanization": "romanized version",
                "topic": "$topic"
            }
            
            Example for topic "daily life":
            {
                "hiraganaSentence": "きょうはいいてんきです",
                "romanization": "kyou wa ii tenki desu",
                "topic": "daily life"
            }
        """.trimIndent()

        val response = callOpenAI(prompt)
        return parseSentenceResponse(response, topic)
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
            throw RuntimeException("Failed to generate AI content: ${e.message}")
        }
    }

    private fun extractContentFromResponse(response: String): String {
        try {
            val jsonNode: JsonNode = objectMapper.readTree(response)
            return jsonNode.path("choices").get(0).path("message").path("content").asText()
        } catch (e: Exception) {
            throw RuntimeException("Failed to parse AI response: ${e.message}")
        }
    }

    private fun parseWordResponse(aiResponse: String, topic: String): AIWordQuestion {
        try {
            val jsonNode: JsonNode = objectMapper.readTree(aiResponse.trim())
            return AIWordQuestion(
                hiraganaWord = jsonNode.path("hiraganaWord").asText(),
                romanization = jsonNode.path("romanization").asText(),
                topic = topic
            )
        } catch (e: Exception) {
            // Fallback word based on topic
            return getFallbackWord(topic)
        }
    }

    private fun parseSentenceResponse(aiResponse: String, topic: String): AISentenceQuestion {
        try {
            val jsonNode: JsonNode = objectMapper.readTree(aiResponse.trim())
            return AISentenceQuestion(
                hiraganaSentence = jsonNode.path("hiraganaSentence").asText(),
                romanization = jsonNode.path("romanization").asText(),
                topic = topic
            )
        } catch (e: Exception) {
            // Fallback sentence based on topic
            return getFallbackSentence(topic)
        }
    }

    private fun getFallbackWord(topic: String): AIWordQuestion {
        val fallbacks = mapOf(
            "food" to AIWordQuestion("たべもの", "tabemono", topic),
            "animals" to AIWordQuestion("いぬ", "inu", topic),
            "colors" to AIWordQuestion("あか", "aka", topic),
            "family" to AIWordQuestion("かぞく", "kazoku", topic),
            "school" to AIWordQuestion("がっこう", "gakkou", topic),
            "weather" to AIWordQuestion("てんき", "tenki", topic),
            "time" to AIWordQuestion("じかん", "jikan", topic),
            "body" to AIWordQuestion("からだ", "karada", topic),
            "house" to AIWordQuestion("いえ", "ie", topic),
            "nature" to AIWordQuestion("しぜん", "shizen", topic)
        )

        return fallbacks[topic] ?: AIWordQuestion("にほん", "nihon", topic)
    }

    private fun getFallbackSentence(topic: String): AISentenceQuestion {
        val fallbacks = mapOf(
            "daily life" to AISentenceQuestion("きょうはいいてんきです", "kyou wa ii tenki desu", topic),
            "food" to AISentenceQuestion("ばんごはんをたべます", "bangohan wo tabemasu", topic),
            "animals" to AISentenceQuestion("いぬがすきです", "inu ga suki desu", topic),
            "school" to AISentenceQuestion("がっこうにいきます", "gakkou ni ikimasu", topic),
            "family" to AISentenceQuestion("かぞくとすんでいます", "kazoku to sunde imasu", topic),
            "weather" to AISentenceQuestion("あめがふっています", "ame ga futte imasu", topic),
            "time" to AISentenceQuestion("いまなんじですか", "ima nanji desu ka", topic),
            "greeting" to AISentenceQuestion("おはようございます", "ohayou gozaimasu", topic),
            "shopping" to AISentenceQuestion("みせでかいものをします", "mise de kaimono wo shimasu", topic),
            "travel" to AISentenceQuestion("りょこうにいきたいです", "ryokou ni ikitai desu", topic)
        )

        return fallbacks[topic] ?: AISentenceQuestion("にほんごをべんきょうします", "nihongo wo benkyou shimasu", topic)
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