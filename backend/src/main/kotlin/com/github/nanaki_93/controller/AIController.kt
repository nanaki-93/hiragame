package com.github.nanaki_93.controller


import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.models.Level

import com.github.nanaki_93.service.AIQuestionService
import com.github.nanaki_93.service.BatchQuestionGenerationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.concurrent.CompletableFuture

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = ["http://localhost:8081"])
class AIController(
    private val aiQuestionService: AIQuestionService,
    private val batchService: BatchQuestionGenerationService
) {

    @GetMapping("/words")
    fun generateWord( @RequestParam level: String, @RequestParam nQuestions: Int): ResponseEntity<Int> {
        return ResponseEntity.ok(aiQuestionService.generateAndStoreQuestions( level.let { Level.valueOf(it) }, nQuestions, GameMode.WORD))
    }

    @GetMapping("/sentences")
    fun generateSentence( @RequestParam level: String, @RequestParam nQuestions: Int): ResponseEntity<Int> {
        return ResponseEntity.ok(aiQuestionService.generateAndStoreQuestions(level.let { Level.valueOf(it) },nQuestions, GameMode.SENTENCE))
    }



    @PostMapping("/batch-generate")
    fun startBulkGeneration(
        @RequestParam(defaultValue = "100") totalQuestions: Int,
        @RequestParam(defaultValue = "5") batchSize: Int,
        @RequestParam(defaultValue = "3000") delayMs: Long,
        @RequestParam(defaultValue = "WORD") gameMode: String,
    ): CompletableFuture<String> {
        return batchService.generateBulkQuestions(totalQuestions, batchSize, delayMs, gameMode.let { GameMode.valueOf(it) })
    }

    @GetMapping("/batch-status")
    fun getStatus(): Map<String, Any> {
        return batchService.getGenerationStatus()
    }
}