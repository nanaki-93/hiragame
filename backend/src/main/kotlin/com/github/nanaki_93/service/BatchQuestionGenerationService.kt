package com.github.nanaki_93.service


import com.github.nanaki_93.models.AIQuestion
import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.repository.HiraganaQuestionRepository
import com.github.nanaki_93.util.toHiraganaQuestion
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CompletableFuture

@Service
class BatchQuestionGenerationService(
    private val aiQuestionService: AIQuestionService,
    private val hiraganaRepository: HiraganaQuestionRepository
) {
    private val logger = LoggerFactory.getLogger(BatchQuestionGenerationService::class.java)

    @Async
    fun generateBulkQuestions(
        totalQuestions: Int = 100,
        batchSize: Int = 20,
        delayBetweenBatches: Long = 2000,
        gameMode: GameMode = GameMode.WORD,
    ): CompletableFuture<String> {
        return CompletableFuture.supplyAsync {
            try {
                val generationContext = createGenerationContext(totalQuestions, batchSize, gameMode)
                val questionsGenerated = processBatches(generationContext, delayBetweenBatches)

                logger.info("CSV bulk generation completed. Total questions saved: $questionsGenerated")
                "CSV bulk generation completed. Generated $questionsGenerated questions."
            } catch (e: Exception) {
                logger.error("Bulk generation failed", e)
                "Bulk generation failed: ${e.message}"
            }
        }
    }

    private fun createGenerationContext(totalQuestions: Int, batchSize: Int, gameMode: GameMode): GenerationContext {
        val topics = aiQuestionService.getAvailableTopics()
        val totalBatches = calculateTotalBatches(totalQuestions, batchSize)

        logger.info("Starting CSV bulk generation: $totalQuestions questions in $totalBatches batches")

        return GenerationContext(
            totalQuestions = totalQuestions,
            batchSize = batchSize,
            totalBatches = totalBatches,
            topics = topics,
            gameMode = gameMode,
            questionsGenerated = AtomicInteger(0)
        )
    }

    private fun calculateTotalBatches(totalQuestions: Int, batchSize: Int): Int =
        (totalQuestions / batchSize) + if (totalQuestions % batchSize > 0) 1 else 0

    private fun processBatches(context: GenerationContext, delayBetweenBatches: Long): Int {
        for (batchNumber in 1..context.totalBatches) {
            val batchResult = processSingleBatch(context, batchNumber)
            context.questionsGenerated.addAndGet(batchResult.questionsInserted)

            logger.info(
                "Batch $batchNumber/${context.totalBatches} completed. " +
                        "Got ${batchResult.questionsRequested} questions " +
                        "Topic: ${batchResult.topic}, Difficulty: ${batchResult.difficulty}, " +
                        "Total saved: ${batchResult.questionsInserted}"
            )

            if (batchNumber < context.totalBatches) {
                Thread.sleep(delayBetweenBatches)
            }
        }
        return context.questionsGenerated.get()
    }

    private fun processSingleBatch(context: GenerationContext, batchNumber: Int): BatchResult {

        val bp = BatchParameters(
            batchNumber = batchNumber,
            questionsInThisBatch = calculateQuestionsInBatch(context, batchNumber),
            topic = selectTopicForBatch(context.topics, batchNumber),
            gameMode = context.gameMode,
            difficulty = calculateDifficultyForBatch(batchNumber)
        )


        return BatchResult(
            questionsRequested = bp.questionsInThisBatch,
            questionsInserted = insertQuestionBatch(bp),
            topic = bp.topic,
            difficulty = bp.difficulty
        )
    }

    private fun calculateQuestionsInBatch(context: GenerationContext, batchNumber: Int): Int =
        minOf(context.batchSize, context.totalQuestions - ((batchNumber - 1) * context.batchSize))

    private fun selectTopicForBatch(topics: List<String>, batchNumber: Int): String =
        topics[(batchNumber - 1) % topics.size]

    private fun calculateDifficultyForBatch(batchNumber: Int): Int = batchNumber % 5 + 1


    private fun insertQuestionBatch(batchParameters: BatchParameters): Int {
        try {
            generateQuestions(batchParameters)
                .map { it.toHiraganaQuestion() }
                .distinctBy { it.hiragana }
                .let { hiraganaRepository.saveAll(it) }
                .size
        } catch (e: Exception) {
            logger.error("Error in batch ${batchParameters.batchNumber}: ${e.message}", e)
        }
        return 0
    }

    private fun generateQuestions(
        bp: BatchParameters
    ): List<AIQuestion> = if (bp.gameMode == GameMode.WORD) {
        aiQuestionService.generateWordQuestion(bp.topic, bp.difficulty, bp.questionsInThisBatch)
    } else {
        aiQuestionService.generateSentenceQuestion(bp.topic, bp.difficulty, bp.questionsInThisBatch)
    }


    fun getGenerationStatus(): Map<String, Any> {
        val totalInDb = hiraganaRepository.count()
        return mapOf(
            "totalQuestionsInDatabase" to totalInDb,
            "wordQuestions" to hiraganaRepository.countByGameMode(GameMode.WORD.name),
            "sentenceQuestions" to hiraganaRepository.countByGameMode(GameMode.SENTENCE.name)
        )
    }
}

data class BatchParameters(
    val batchNumber: Int,
    val questionsInThisBatch: Int,
    val topic: String,
    val gameMode: GameMode,
    val difficulty: Int,
)

private data class GenerationContext(
    val totalQuestions: Int,
    val batchSize: Int,
    val totalBatches: Int,
    val topics: List<String>,
    val gameMode: GameMode,
    val questionsGenerated: AtomicInteger
)

private data class BatchResult(
    val questionsRequested: Int,
    val questionsInserted: Int,
    val topic: String,
    val difficulty: Int
)
