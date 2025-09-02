package com.github.nanaki_93.ai.generation

import com.github.nanaki_93.ai.model.Batch
import com.github.nanaki_93.ai.model.BatchParams
import com.github.nanaki_93.ai.model.BatchResult
import com.github.nanaki_93.ai.model.GenContext
import com.github.nanaki_93.dto.QuestionDto
import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.repository.QuestionRepository
import com.github.nanaki_93.ai.generation.AIQuestionService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture

@Service
class BatchQuestionGenerationService(
    private val aiQuestionService: AIQuestionService,
    private val hiraganaRepository: QuestionRepository
) {
    private val logger = LoggerFactory.getLogger(BatchQuestionGenerationService::class.java)

    @Async
    fun generateBulkQuestions(
        genContext: GenContext = GenContext(),
        delayBetweenBatches: Long = 3000,
    ): CompletableFuture<String> = runCatching { processBatches(genContext, delayBetweenBatches) }
        .onSuccess { logger.info("CSV bulk generation completed. Total questions saved: $it") }
        .map { "CSV bulk generation completed. Generated $it questions." }
        .onFailure { logger.error("Bulk generation failed", it) }
        .fold(
            onSuccess = { CompletableFuture.completedFuture(it) },
            onFailure = { CompletableFuture.completedFuture("Bulk generation failed: ${it.message}") }
        )


    private fun processBatches(context: GenContext, delayBetweenBatches: Long): Int {
        return (1..context.totalBatches)
            .map { batchNumber ->
                processSingleBatch(Batch.toBatchParams(context, batchNumber))
                    .also { batchResult ->
                        context.questionsGenerated.addAndGet(batchResult.questionsInserted)
                        logger.info(
                            "Batch $batchNumber/${context.totalBatches} completed. " + "Got ${batchResult.questionsRequested} questions " +
                                    " Difficulty: ${batchResult.level}, " + "Total saved: ${batchResult.questionsInserted}"
                        )
                        if (batchNumber < context.totalBatches) {
                            Thread.sleep(delayBetweenBatches)
                        }
                    }
            }
            .sumOf { it.questionsInserted }
    }

    private fun processSingleBatch(bp: BatchParams): BatchResult =
        BatchResult(
            questionsRequested = bp.questionsInThisBatch,
            questionsInserted = insertQuestionBatch(bp),
            level = bp.level
        )


    private fun insertQuestionBatch(bp: BatchParams): Int =
        runCatching { aiQuestionService.generateAndStoreQuestions(QuestionDto(bp.level, bp.questionsInThisBatch, bp.gameMode, bp.topic)) }
            .also { logger.info("Batch ${bp.batchNumber}: Actually inserted $it new questions into database") }
            .onFailure { logger.error("Error in batch ${bp.batchNumber}: ${it.message}", it) }
            .getOrDefault(0)


    fun getGenerationStatus(): Map<String, Any> =
        mapOf(
            "totalQuestionsInDatabase" to hiraganaRepository.count(),
            "wordQuestions" to hiraganaRepository.countByGameMode(GameMode.WORD.name),
            "sentenceQuestions" to hiraganaRepository.countByGameMode(GameMode.SENTENCE.name)
        )
}