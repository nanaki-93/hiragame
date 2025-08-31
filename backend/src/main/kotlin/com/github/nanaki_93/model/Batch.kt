package com.github.nanaki_93.model

import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.models.Level
import java.util.concurrent.atomic.AtomicInteger

data class BatchParameters(
    val batchNumber: Int,
    val questionsInThisBatch: Int,
    val gameMode: GameMode,
    val level: Level,
)

data class GenerationContext(
    val totalQuestions: Int,
    val batchSize: Int,
    val totalBatches: Int,
    val gameMode: GameMode,
    val questionsGenerated: AtomicInteger
)

data class BatchResult(
    val questionsRequested: Int,
    val questionsInserted: Int,
    val level: Level
)