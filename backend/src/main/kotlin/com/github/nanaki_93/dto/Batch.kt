package com.github.nanaki_93.dto

import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.models.Level
import java.util.concurrent.atomic.AtomicInteger

data class BatchParams(
    val batchNumber: Int,
    val questionsInThisBatch: Int,
    val gameMode: GameMode,
    val level: Level,
)

data class GenContext(
    val totalQuestions: Int = 10,
    val batchSize: Int = 10,
    val totalBatches: Int = 1,
    val gameMode: GameMode = GameMode.WORD,
    val questionsGenerated: AtomicInteger = AtomicInteger(0)
)

data class BatchResult(
    val questionsRequested: Int,
    val questionsInserted: Int,
    val level: Level
)


object Batch {
    fun toGenContext(totalQuestions: Int, batchSize: Int, gameMode: GameMode): GenContext =
        GenContext(
            totalQuestions = totalQuestions,
            batchSize = batchSize,
            totalBatches = calculateTotalBatches(totalQuestions, batchSize),
            gameMode = gameMode,
            questionsGenerated = AtomicInteger(0)
        )

    fun toBatchParams(genContext: GenContext, batchNumber: Int): BatchParams =
        BatchParams(
            batchNumber = batchNumber,
            questionsInThisBatch = calculateQuestionsInBatch(genContext, batchNumber),
            gameMode = genContext.gameMode,
            level = calculateDifficultyForBatch(batchNumber)
        )
}


private fun calculateTotalBatches(totalQuestions: Int, batchSize: Int): Int =
    (totalQuestions / batchSize) + if (totalQuestions % batchSize > 0) 1 else 0


private fun calculateQuestionsInBatch(context: GenContext, batchNumber: Int): Int =
    minOf(context.batchSize, context.totalQuestions - ((batchNumber - 1) * context.batchSize))

private fun calculateDifficultyForBatch(batchNumber: Int): Level = Level.entries[(batchNumber - 1) % 5]