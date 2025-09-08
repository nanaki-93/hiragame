package com.github.nanaki_93.ai

import com.github.nanaki_93.ai.generation.BatchQuestionGenerationService
import com.github.nanaki_93.ai.model.Batch
import com.github.nanaki_93.dto.ai.AiQuestionDto
import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.models.Level
import com.github.nanaki_93.ai.generation.AIQuestionService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CompletableFuture

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = ["http://localhost:8081"])
class AIController(
    private val aiQuestionService: AIQuestionService,
    private val batchService: BatchQuestionGenerationService
) {

    @GetMapping("/words")
    fun generateWord(@RequestParam level: String, @RequestParam nQuestions: Int): ResponseEntity<Int> =
        ResponseEntity.ok(aiQuestionService.generateAndStoreQuestions(AiQuestionDto(level.let { Level.valueOf(it) }, nQuestions, GameMode.WORD)))

    @GetMapping("/sentences")
    fun generateSentence(@RequestParam level: String, @RequestParam nQuestions: Int): ResponseEntity<Int> = ResponseEntity.ok(
        aiQuestionService.generateAndStoreQuestions(
            AiQuestionDto(
                level.let { Level.valueOf(it) },
                nQuestions,
                GameMode.SENTENCE
            )
        )
    )


    @PostMapping("/batch-generate")
    fun startBulkGeneration(
        @RequestParam(defaultValue = "100") totalQuestions: Int,
        @RequestParam(defaultValue = "5") batchSize: Int,
        @RequestParam(defaultValue = "WORD") gameMode: String,
        @RequestParam(defaultValue = "3000") delayMs: Long,
    ): CompletableFuture<String> =
        batchService.generateBulkQuestions(Batch.toGenContext(totalQuestions, batchSize, gameMode.let { GameMode.valueOf(it) }), delayMs)

    @GetMapping("/batch-status")
    fun getStatus(): Map<String, Any> = batchService.getGenerationStatus()
}