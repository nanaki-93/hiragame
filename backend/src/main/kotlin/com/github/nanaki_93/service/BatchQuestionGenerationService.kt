package com.github.nanaki_93.service


import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.repository.HiraganaQuestion
import com.github.nanaki_93.repository.HiraganaQuestionRepository
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
        difficulty: Int = 1,
    ): CompletableFuture<String> {
        return CompletableFuture.supplyAsync {
            try {
                val topics = aiQuestionService.getAvailableTopics()

                val totalSaved = AtomicInteger(0)
                val totalBatches = (totalQuestions / batchSize) + if (totalQuestions % batchSize > 0) 1 else 0

                logger.info("Starting CSV bulk generation: $totalQuestions questions in $totalBatches batches")

                for (batch in 1..totalBatches) {
                    try {
                        val questionsInThisBatch = minOf(batchSize, totalQuestions - ((batch - 1) * batchSize))
                        val topic = topics[(batch - 1) % topics.size]

                        val questions = if (gameMode == GameMode.WORD) {
                            aiQuestionService.generateWordQuestion(topic, difficulty, questionsInThisBatch)
                        } else {
                            aiQuestionService.generateSentenceQuestion(topic, difficulty, questionsInThisBatch)
                        }

                        if (questions.isNotEmpty()) {
                            val dbEntities = questions.map {
                                HiraganaQuestion(
                                    hiragana = it.hiragana,
                                    romanization = it.romanization,
                                    translation = it.translation,
                                    topic = it.topic,
                                    difficulty = it.level,
                                    gameMode = gameMode.name
                                )
                            }

                            //filter duplicates
                            val dbEntitiesFiltered = dbEntities.distinctBy { it.hiragana }
                            hiraganaRepository.saveAll(dbEntitiesFiltered)
                            val saved = totalSaved.addAndGet(questions.size)

                            logger.info("Batch $batch/$totalBatches completed. Got ${questions.size} questions, filtered : ${dbEntitiesFiltered}. Topic: $topic, Difficulty: $difficulty, Type: $gameMode. Total saved: $saved")
                        } else {
                            logger.warn("Batch $batch returned no questions")
                        }

                        if (batch < totalBatches) {
                            Thread.sleep(delayBetweenBatches) // Use Thread.sleep instead of delay
                        }

                    } catch (e: Exception) {
                        logger.error("Error in batch $batch: ${e.message}", e)
                    }
                }

                val finalCount = totalSaved.get()
                logger.info("CSV bulk generation completed. Total questions saved: $finalCount")
                "CSV bulk generation completed. Generated $finalCount questions."
            } catch (e: Exception) {
                logger.error("Bulk generation failed", e)
                "Bulk generation failed: ${e.message}"
            }
        }
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