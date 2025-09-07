package com.github.nanaki_93.ai.generation

import com.github.nanaki_93.dto.AiQuestionDto
import com.github.nanaki_93.models.AIQuestion
import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.repository.Question
import com.github.nanaki_93.repository.QuestionRepository
import com.github.nanaki_93.util.cleanCsvFromMarkdown
import com.github.nanaki_93.util.fromCsvLineToQuestion
import com.github.nanaki_93.util.toHiraganaQuestion
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AIQuestionService(
    private val hiraganaRepository: QuestionRepository,
    private val aiService: AIService
) {
    companion object {
        private const val MAX_QUESTIONS_PER_BATCH = 100
    }

    private val logger = LoggerFactory.getLogger(AIQuestionService::class.java)


    fun generateAndStoreQuestions(
        questionReq: AiQuestionDto
    ): Int = generateQuestionsInBatches(questionReq)
        .also { logger.info("Generated ${it.size} questions for level ${questionReq.level}") }
        .distinctBy { it.japanese }
        .also { logger.info("Filtered double in the list:  ${it.size} questions for level ${questionReq.level}") }
        .let { storeQuestions(it, questionReq.gameMode) }


    private fun generateQuestionsInBatches(questionReq: AiQuestionDto): List<AIQuestion> =
        calculateBatchSizes(questionReq.nQuestions)
            .map { batchSize ->
                Thread.sleep(3000)
                generateQuestions(questionReq.copy(nQuestions = batchSize))
            }
            .flatten()


    fun generateQuestions(questionReq: AiQuestionDto): List<AIQuestion> =
        aiService.getPrompt(questionReq)
            .let(::callAI)
            .let(::parseFromCsvToAiQuestions)

    fun callAI(prompt: String): String =
        runCatching { aiService.callApi(prompt) }
            .onFailure { logger.error("Failed to call AI API: ${it.message}") }
            .getOrDefault("Failed to call AI API")


    fun storeQuestions(response: List<AIQuestion>, gameMode: GameMode): Int =
        response.map { it.toHiraganaQuestion(gameMode) }
            .let { toDB ->
                toDB.filterNot { it.japanese in findExistingQuestion(toDB) }
            }.takeIf { it.isNotEmpty() }
            ?.let {
                logger.info("DB => Saved ${it.size} new questions out of ${response.size} generated")
                hiraganaRepository.saveAll(it)
                it.size
            } ?: run {
            logger.info("No new questions to save, all ${response.size} questions already exist")
            return 0
        }

    private fun findExistingQuestion(toDB: List<Question>): Set<String> =
        hiraganaRepository.findAllByJapaneseIn(toDB.map { it.japanese })
            .map { it.japanese }
            .toSet()

    private fun parseFromCsvToAiQuestions(csvResponse: String): List<AIQuestion> =
        runCatching {
            logger.info("Parsing CSV response to questions, ${csvResponse.lines().size} lines")
            csvResponse.trim().cleanCsvFromMarkdown()
                .lineSequence()
                .filter { it.isNotBlank() && it.contains(";") }
                .mapNotNull { it.fromCsvLineToQuestion() }
                .toList()
        }.onFailure { logger.error("Failed to parse CSV response: ${it.message}") }
            .getOrDefault(emptyList())

    private fun calculateBatchSizes(totalQuestions: Int): List<Int> =
        generateSequence(totalQuestions) { remaining ->
            if (remaining > MAX_QUESTIONS_PER_BATCH) remaining - MAX_QUESTIONS_PER_BATCH else null
        }.map { remaining ->
            minOf(remaining, MAX_QUESTIONS_PER_BATCH)
        }.toList()


}