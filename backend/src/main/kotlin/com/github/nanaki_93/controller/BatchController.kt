package com.github.nanaki_93.controller

import com.github.nanaki_93.service.BatchQuestionGenerationService
import org.springframework.web.bind.annotation.*
import java.util.concurrent.CompletableFuture

@RestController
@RequestMapping("/api/batch")
@CrossOrigin(origins = ["http://localhost:8081"])
class BatchController(
    private val batchService: BatchQuestionGenerationService
) {

    @PostMapping("/generate")
    fun startBulkGeneration(
        @RequestParam(defaultValue = "100") totalQuestions: Int,
        @RequestParam(defaultValue = "5") batchSize: Int,
        @RequestParam(defaultValue = "3000") delayMs: Long
    ): CompletableFuture<String> {
        return batchService.generateBulkQuestions(totalQuestions, batchSize, delayMs)
    }

    @GetMapping("/status")
    fun getStatus(): Map<String, Any> {
        return batchService.getGenerationStatus()
    }
}