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
        level: Level = Level.N5,
        nQuestions: Int = 5,
        gameMode: GameMode = GameMode.WORD,
    ): Int {
        val allQuestions = generateQuestionsBatch(level, nQuestions, gameMode)
        logger.info("Generated ${allQuestions.size} questions for level $level")
        val filteredQuestions = filterQuestions(allQuestions)
        logger.info("Filtered double in the list and only hiragana chars for N5 Level:  ${filteredQuestions.size} questions for level $level")
        return storeQuestions(filteredQuestions, gameMode)
    }

    private fun generateQuestionsBatch(level: Level, nQuestions: Int, gameMode: GameMode): List<AIQuestion> {
        val responseList = mutableListOf<AIQuestion>()
        var remainingQuestions = nQuestions

        repeat((nQuestions / MAX_QUESTIONS_PER_BATCH) + 1) {
            val questionsToMake = minOf(remainingQuestions, MAX_QUESTIONS_PER_BATCH)
            if (questionsToMake > 0) {
                responseList.addAll(generateQuestions(level, questionsToMake, gameMode))
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
                if (CleanUtil.isOnlyHiragana(question.hiragana)) {
                    true
                } else {
                    logger.warn("Skipping question with non-hiragana content: $question")
                    false
                }
            } else {
                true
            }
        }.filter { question ->
            seenHiragana.add(question.hiragana) // more performant than distinctBy
        }
    }

    fun generateQuestions(level: Level, nQuestions: Int, gameMode: GameMode): List<AIQuestion> {
        val prompt = aiService.getPrompt(level, nQuestions, gameMode)
        val response = callAI(prompt)
        return parseFromCsvToAiQuestions(response)
    }

    fun storeQuestions(response: List<AIQuestion>, gameMode: GameMode): Int {
        return response.map { it.toHiraganaQuestion(gameMode) }
            .let { toDB ->
                toDB.filterNot {
                    val alreadyInDb = findExistingHiragana(toDB)
                    if (it.hiragana in alreadyInDb) {
                        logger.info("Question already exists in DB: ${it.hiragana}")
                        true
                    } else {
                        false
                    }
                }
            }
            .takeIf { it.isNotEmpty() }
            ?.let {
                logger.info("DB => Saved ${it.size} new questions out of ${response.size} generated")
                hiraganaRepository.saveAll(it)
                it.size
            } ?: run {
            logger.info("No new questions to save, all ${response.size} questions already exist")
            return 0
        }

    }

    private fun findExistingHiragana(toDB: List<HiraganaQuestion>): Set<String> =
        hiraganaRepository
            .findAllByHiraganaIn(toDB.map { it.hiragana })
            .map { it.hiragana }
            .toSet()

    private fun parseFromCsvToAiQuestions(csvResponse: String): List<AIQuestion> {
        try {
            logger.info("Parsing CSV response to questions")
            logger.info("CSV response lines: ${csvResponse.lines().size}")
            val result = cleanCsvFromMarkdown(csvResponse.trim())
                .lineSequence()
                .filter { it.isNotBlank() && it.contains(";") }
                .mapNotNull { fromCsvLineToQuestion(it) }
                .toList()
            return result
        } catch (e: Exception) {
            logger.error("Failed to parse CSV response: ${e.message}")
            return emptyList()
        }
    }


    private fun fromCsvLineToQuestion(line: String): AIQuestion? {
        return try {
            val parts = line.split(";").map { it.trim().removeSurrounding("\"") }
            AIQuestion(parts[0], parts[1], parts[2], parts[3], Level.valueOf(parts[4]))
        } catch (e: Exception) {
            logger.warn("Failed to parse CSV line: $line - ${e.message}")
            null
        }
    }


}