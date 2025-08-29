package com.github.nanaki_93.service


import org.springframework.stereotype.Service
import com.github.nanaki_93.service.ai.AiService
import com.github.nanaki_93.models.AIQuestion
import com.github.nanaki_93.repository.HiraganaQuestion
import com.github.nanaki_93.repository.HiraganaQuestionRepository
import com.github.nanaki_93.util.CleanUtil.cleanCsvFromMarkdown
import com.github.nanaki_93.util.toHiraganaQuestion
import org.slf4j.LoggerFactory

@Service
class AIQuestionService(
    private val hiraganaRepository: HiraganaQuestionRepository,
    private val aiService: AiService
) {
    private val logger = LoggerFactory.getLogger(AIQuestionService::class.java)

    fun callAI(prompt: String): String {
        return try {
            aiService.callApi(prompt)
        } catch (e: Exception) {
            logger.error("Failed to call OpenAI API: ${e.message}")
            "Failed to call OpenAI API"
        }
    }

    fun generateAndStoreWordQuestion(topic: String, difficulty: Int, nQuestions: Int): List<AIQuestion> {
        val response = generateWordQuestion(topic, difficulty, nQuestions)
        return storeQuestions(response)
    }

    fun generateAndStoreSentenceQuestion(topic: String, difficulty: Int, nQuestions: Int): List<AIQuestion> {
        val response = generateSentenceQuestion(topic, difficulty, nQuestions)
        return storeQuestions(response)
    }


    fun generateWordQuestion(topic: String, level: Int, nQuestions: Int): List<AIQuestion> {
        val prompt = aiService.getWordsPrompt(topic, level, nQuestions)
        val response = callAI(prompt)
        return removeDuplicatesAndParse(response, topic)
    }

    fun generateSentenceQuestion(topic: String, difficulty: Int, nQuestions: Int): List<AIQuestion> {
        val prompt = aiService.getSentencesPrompt(topic, difficulty, nQuestions)
        val response = callAI(prompt)
        return removeDuplicatesAndParse(response, topic)
    }


    private fun storeQuestions(response: List<AIQuestion>): List<AIQuestion> {
        response.map { it.toHiraganaQuestion() }
            .let { toDB -> toDB.filterNot { it.hiragana in findExistingHiragana(toDB) } }
            .takeIf { it.isNotEmpty() }
            ?.let {
                hiraganaRepository.saveAll(it)
                logger.info("Saved ${it.size} new questions out of ${response.size} generated")
            }
            ?: logger.info("No new questions to save, all ${response.size} questions already exist")

        return response
    }

    private fun findExistingHiragana(toDB: List<HiraganaQuestion>): Set<String> =
        hiraganaRepository
            .findAllByHiraganaIn(toDB.map { it.hiragana })
            .map { it.hiragana }
            .toSet()

    private fun removeDuplicatesAndParse(csvResponse: String, topic: String): List<AIQuestion> {
        try {
            logger.info("Parsing CSV response and removing duplicates")

            val seenHiragana = mutableSetOf<String>()

            return cleanCsvFromMarkdown(csvResponse.trim())
                .lineSequence()
                .filter { it.isNotBlank() && it.contains(";") }
                .mapNotNull { fromCsvLineToQuestion(it, topic) }
                .filter { question -> seenHiragana.add(question.hiragana) } //more performant than distinctBy
                .toList()

        } catch (e: Exception) {
            logger.error("Failed to parse CSV response: ${e.message}")
            return emptyList()
        }
    }


    private fun fromCsvLineToQuestion(line: String, topic: String): AIQuestion? {
        return try {
            val parts = line.split(";").map { it.trim().removeSurrounding("\"") }
            AIQuestion(parts[0], parts[1], parts[2], topic, parts[3].toInt())
        } catch (e: Exception) {
            logger.warn("Failed to parse CSV line: $line - ${e.message}")
            null
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