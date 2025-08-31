package com.github.nanaki_93.service


import org.springframework.stereotype.Service
import com.github.nanaki_93.service.ai.AiService
import com.github.nanaki_93.models.AIQuestion
import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.models.Level
import com.github.nanaki_93.repository.HiraganaQuestion
import com.github.nanaki_93.repository.HiraganaQuestionRepository
import com.github.nanaki_93.util.CleanUtil
import com.github.nanaki_93.util.CleanUtil.cleanCsvFromMarkdown
import com.github.nanaki_93.util.toHiraganaQuestion
import org.slf4j.LoggerFactory
import kotlin.sequences.filter
import kotlin.sequences.toList

@Service
class AIQuestionService(
    private val hiraganaRepository: HiraganaQuestionRepository,
    private val aiService: AiService
) {
    companion object {
        private const val MAX_QUESTIONS_PER_BATCH = 50
    }

    private val logger = LoggerFactory.getLogger(AIQuestionService::class.java)

    fun callAI(prompt: String): String {
        return try {
            aiService.callApi(prompt)
        } catch (e: Exception) {
            logger.error("Failed to call AI API: ${e.message}")
            "Failed to call AI API"
        }
    }

    fun generateAndStoreQuestions(
        topic: String = "food",
        level: Level = Level.N5,
        nQuestions: Int = 5,
        gameMode: GameMode = GameMode.WORD,
    ): List<AIQuestion> {
        val allQuestions = generateQuestionsBatch(topic, level, nQuestions, gameMode)
        val filteredQuestions = filterQuestions(allQuestions)
        return storeQuestions(filteredQuestions, GameMode.WORD)

    }

    private fun generateQuestionsBatch(topic: String, level: Level, nQuestions: Int,gameMode: GameMode): List<AIQuestion> {
        val responseList = mutableListOf<AIQuestion>()
        var remainingQuestions = nQuestions

        repeat((nQuestions / MAX_QUESTIONS_PER_BATCH) + 1) {
            val questionsToMake = minOf(remainingQuestions, MAX_QUESTIONS_PER_BATCH)
            if (questionsToMake > 0) {
                responseList.addAll(generateQuestion(topic, level, questionsToMake,gameMode))
                remainingQuestions -= questionsToMake
            }
            Thread.sleep(3000)
        }
        return responseList
    }

    private fun filterQuestions(questions: List<AIQuestion>): List<AIQuestion> {
        val seenHiragana = mutableSetOf<String>()
        return questions.filter { question ->
            // Additional validation for hiragana-only content at N5 level
            if (question.level == Level.N5) {
                CleanUtil.isOnlyHiragana(question.hiragana)
            } else {
                true
            }
        }.filter { question ->
            seenHiragana.add(question.hiragana) // more performant than distinctBy
        }
    }

    fun generateQuestion(topic: String, level: Level, nQuestions: Int, gameMode: GameMode): List<AIQuestion> {
        val prompt = aiService.getPrompt(topic, level, nQuestions, gameMode)
        val response = callAI(prompt)
        return removeDuplicatesAndParse(response, topic)
    }

    fun storeQuestions(response: List<AIQuestion>, gameMode: GameMode): List<AIQuestion> {
        response.map { it.toHiraganaQuestion(gameMode) }
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
            val cleanedCsv = cleanCsvFromMarkdown(csvResponse.trim())

            // For N5 level, filter out non-hiragana content
            val filteredCsv = if (topic.contains("N5") || csvResponse.contains("N5")) {
                CleanUtil.filterHiraganaOnlyLines(cleanedCsv)
            } else {
                cleanedCsv
            }

            return filteredCsv
                .lineSequence()
                .filter { it.isNotBlank() && it.contains(";") }
                .mapNotNull { fromCsvLineToQuestion(it, topic) }
                .filter { question ->
                    // Additional validation for hiragana-only content at N5 level
                    if (question.level == Level.N5) {
                        CleanUtil.isOnlyHiragana(question.hiragana)
                    } else {
                        true
                    }
                }

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
            AIQuestion(parts[0], parts[1], parts[2], topic, Level.valueOf(parts[3]))
        } catch (e: Exception) {
            logger.warn("Failed to parse CSV line: $line - ${e.message}")
            null
        }
    }


    // Method to get available topics
    // TODO GET MORE TOPICS
    fun getAvailableTopics(): List<String> {
        return listOf(
            "food", "animals", "colors", "family", "school",
            "weather", "time", "body", "house", "nature",
            "daily life", "greeting", "shopping", "travel"
        )
    }
}