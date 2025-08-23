package com.github.nanaki_93.api


import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.queryForObject
import org.springframework.stereotype.Repository
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/**
 * NOTE: For this code to work, you must configure a PostgreSQL database connection
 * and a JdbcTemplate bean in your Kobweb JVM application's Spring context.
 * For example, in a @Configuration class:
 *
 * @Bean
 * fun dataSource(): DataSource {
 * val config = HikariConfig()
 * config.jdbcUrl = "jdbc:postgresql://localhost:5432/your_database"
 * config.username = "your_user"
 * config.password = "your_password"
 * return HikariDataSource(config)
 * }
 *
 * @Bean
 * fun jdbcTemplate(dataSource: DataSource): JdbcTemplate {
 * return JdbcTemplate(dataSource)
 * }
 */

// Data model for the database table
data class HiraganaContent(
    val id: Long? = null,
    val hiragana: String,
    val romaji: String,
    val topic: String,
    val contentType: String,
    val timestamp: OffsetDateTime
)

// A RowMapper to convert a ResultSet row into a HiraganaContent object.
class HiraganaContentRowMapper : RowMapper<HiraganaContent> {
    override fun mapRow(rs: ResultSet, rowNum: Int): HiraganaContent {
        return HiraganaContent(
            id = rs.getLong("id"),
            hiragana = rs.getString("hiragana"),
            romaji = rs.getString("romaji"),
            topic = rs.getString("topic"),
            contentType = rs.getString("content_type"),
            timestamp = rs.getObject("timestamp", OffsetDateTime::class.java)
        )
    }
}

/**
 * Repository interface for data access operations.
 */
interface HiraganaContentRepository {
    fun save(content: HiraganaContent): HiraganaContent
    fun findRecentByTopicAndType(topic: String, contentType: String): List<HiraganaContent>
}

/**
 * Implementation of HiraganaContentRepository using Spring's JdbcTemplate.
 */
@Repository
class JdbcHiraganaContentRepository(private val jdbcTemplate: JdbcTemplate) : HiraganaContentRepository {
    private val rowMapper = HiraganaContentRowMapper()

    override fun save(content: HiraganaContent): HiraganaContent {
        // Insert new content into the database
        val sql = "INSERT INTO hiragana_content (hiragana, romaji, topic, content_type, timestamp) VALUES (?, ?, ?, ?, ?)"
        jdbcTemplate.update(sql, content.hiragana, content.romaji, content.topic, content.contentType, content.timestamp)
        return content // For simplicity, we just return the input object
    }

    override fun findRecentByTopicAndType(topic: String, contentType: String): List<HiraganaContent> {
        val freshnessThresholdDays = 7
        val sql = """
            SELECT id, hiragana, romaji, topic, content_type, timestamp 
            FROM hiragana_content 
            WHERE topic = ? AND content_type = ? AND timestamp > NOW() - INTERVAL '$freshnessThresholdDays days'
            ORDER BY timestamp DESC
            LIMIT 10
        """
        return jdbcTemplate.query(sql, rowMapper, topic, contentType)
    }
}

// A serializable class to represent the API response
@Serializable
data class GenerationResponse(
    val content: List<SerializedHiraganaContent>,
    val source: String // "cache" or "ai"
)

// A serializable version of the data class for the API response
@Serializable
data class SerializedHiraganaContent(
    val hiragana: String,
    val romaji: String
)

// --- Backend API Endpoint ---

@Api
suspend fun generateHiragana(ctx: ApiContext): GenerationResponse {
    val topic = ctx.req.params["topic"] ?: return GenerationResponse(emptyList(), "error")
    val contentType = ctx.req.params["contentType"] ?: return GenerationResponse(emptyList(), "error")

    // Assuming the repository is injected or instantiated here.
    // In a Spring context, this would be auto-wired.
    val jdbcTemplate = ctx.getService<JdbcTemplate>() // Example of getting service
    val repository = JdbcHiraganaContentRepository(jdbcTemplate)

    // 1. Check PostgreSQL cache first
    try {
        val cachedContent = repository.findRecentByTopicAndType(topic, contentType)
        if (cachedContent.isNotEmpty()) {
            val randomizedContent = cachedContent
                .shuffled()
                .map { SerializedHiraganaContent(it.hiragana, it.romaji) }
            return GenerationResponse(randomizedContent, "cache")
        }
    } catch (e: Exception) {
        ctx.logger.error("Error fetching from PostgreSQL: ${e.message}")
        // Continue to AI generation as a fallback
    }

    // 2. Cache miss, call Gemini API
    ctx.logger.info("Cache miss for topic: '$topic'. Calling Gemini API...")
    try {
        val jsonPayload = buildPayload(topic, contentType)
        val apiKey = "" // Canvas will inject this key
        val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-05-20:generateContent?key=$apiKey"

        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() == 200) {
            val responseBody = Json.decodeFromString<GeminiResponse>(response.body())
            val generatedContentList = parseGeminiResponse(responseBody, topic, contentType)

            if (generatedContentList.isNotEmpty()) {
                // 3. Save new content to PostgreSQL
                generatedContentList.forEach { content ->
                    repository.save(content)
                }

                val serializedContent = generatedContentList.map { SerializedHiraganaContent(it.hiragana, it.romaji) }
                ctx.logger.info("Successfully generated and saved content for topic: '$topic'")
                return GenerationResponse(serializedContent, "ai")
            }
        }
    } catch (e: Exception) {
        ctx.logger.error("Error calling Gemini API or saving to PostgreSQL: ${e.message}")
    }

    // Fallback if anything goes wrong
    return GenerationResponse(emptyList(), "error")
}

// --- Helper Functions for Gemini API Calls (unchanged) ---

private fun buildPayload(topic: String, contentType: String): String {
    val prompt = if (contentType == "words") {
        "Generate 10 Japanese words related to the topic \"$topic\". Provide the output as a JSON array of objects. Each object should have two keys: \"hiragana\" for the word in hiragana and \"romaji\" for its romaji transcription. Do not include any other text or formatting outside of the JSON."
    } else { // sentences
        "Generate 5 simple Japanese sentences related to the topic \"$topic\". Provide the output as a JSON array of objects. Each object should have two keys: \"hiragana\" for the sentence in hiragana and \"romaji\" for its romaji transcription. Do not include any other text or formatting outside of the JSON."
    }

    val schema = if (contentType == "words") {
        """
        {
          "type": "ARRAY",
          "items": {
            "type": "OBJECT",
            "properties": {
              "hiragana": { "type": "STRING" },
              "romaji": { "type": "STRING" }
            },
            "propertyOrdering": ["hiragana", "romaji"]
          }
        }
        """
    } else { // sentences
        """
        {
          "type": "ARRAY",
          "items": {
            "type": "OBJECT",
            "properties": {
              "hiragana": { "type": "STRING" },
              "romaji": { "type": "STRING" }
            },
            "propertyOrdering": ["hiragana", "romaji"]
          }
        }
        """
    }

    return """
    {
        "contents": [
            {
                "parts": [
                    { "text": "$prompt" }
                ]
            }
        ],
        "generationConfig": {
            "responseMimeType": "application/json",
            "responseSchema": $schema
        }
    }
    """.trimIndent()
}

private fun parseGeminiResponse(response: GeminiResponse, topic: String, contentType: String): List<HiraganaContent> {
    val jsonString = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: return emptyList()
    val rawContentList = Json.decodeFromString<List<Map<String, String>>>(jsonString)
    val timestamp = OffsetDateTime.now()

    return rawContentList.map {
        HiraganaContent(
            hiragana = it["hiragana"] ?: "",
            romaji = it["romaji"] ?: "",
            topic = topic,
            contentType = contentType,
            timestamp = timestamp
        )
    }
}

// --- Utility Classes for JSON parsing (unchanged) ---
@Serializable
data class GeminiResponse(
    val candidates: List<Candidate>? = null
)

@Serializable
data class Candidate(
    val content: Content? = null
)

@Serializable
data class Content(
    val parts: List<Part>? = null
)

@Serializable
data class Part(
    val text: String? = null
)
