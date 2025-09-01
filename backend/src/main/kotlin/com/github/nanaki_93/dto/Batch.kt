package com.github.nanaki_93.dto

import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.models.Level
import com.github.nanaki_93.models.topicsByJLPTLevel
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

data class BatchParams(
    val batchNumber: Int,
    val questionsInThisBatch: Int,
    val gameMode: GameMode,
    val level: Level,
    val topic : String
)

data class GenContext(
    val totalQuestions: Int = 10,
    val batchSize: Int = 10,
    val totalBatches: Int = 1,
    val gameMode: GameMode = GameMode.WORD,
    val questionsGenerated: AtomicInteger = AtomicInteger(0),
    val availableTopics: MutableMap<Level,MutableList<String>> = mutableMapOf()

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
            questionsGenerated = AtomicInteger(0),
            availableTopics = topicsByJLPTLevel.mapValues { it.value.toMutableList() }.toMutableMap()
        )

    fun toBatchParams(genContext: GenContext, batchNumber: Int): BatchParams {
        val level = calculateDifficultyForBatch(batchNumber)
        return BatchParams(
            batchNumber = batchNumber,
            questionsInThisBatch = calculateQuestionsInBatch(genContext, batchNumber),
            gameMode = genContext.gameMode,
            level = level,
            topic = selectAndRemoveTopic(genContext, level)
        )
    }
}

    private fun selectAndRemoveTopic(genContext: GenContext, level: Level): String {
        // Get or initialize topics for this level
        LoggerFactory.getLogger(Batch::class.java).info("selectAndRemoveTopic: level=$level")

        val topicsForLevel = genContext.availableTopics.getOrPut(level) {
            topicsByJLPTLevel[level]!!.toMutableList()
        }

        LoggerFactory.getLogger(Batch::class.java).info("selectAndRemoveTopic: ${topicsForLevel.size} actualTopicList=$topicsForLevel")
        // If no topics available for this level, reinitialize
        if (topicsForLevel.isEmpty()) {
            topicsForLevel.addAll(topicsByJLPTLevel[level]!!)
        }

        // Remove and return the first available topic
        return if (topicsForLevel.isNotEmpty()) {
            topicsForLevel.shuffled().toMutableList().removeAt(0)
        } else {
            "general" // fallback topic
        }
    }




private fun calculateTotalBatches(totalQuestions: Int, batchSize: Int): Int =
    (totalQuestions / batchSize) + if (totalQuestions % batchSize > 0) 1 else 0


private fun calculateQuestionsInBatch(context: GenContext, batchNumber: Int): Int =
    minOf(context.batchSize, context.totalQuestions - ((batchNumber - 1) * context.batchSize))

private fun calculateDifficultyForBatch(batchNumber: Int): Level = Level.entries[(batchNumber - 1) % 5]