package com.github.nanaki_93.controller

import com.github.nanaki_93.models.AIQuestion

import com.github.nanaki_93.service.AIQuestionService
import com.github.nanaki_93.service.BatchQuestionGenerationService
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
    fun generateWord(@RequestParam topic: String, @RequestParam difficulty: Int, @RequestParam nQuestions: Int): List<AIQuestion> {
        return aiQuestionService.generateAndStoreWordQuestion(topic, difficulty, nQuestions)
    }

    @GetMapping("/sentences")
    fun generateSentence(@RequestParam topic: String, @RequestParam difficulty: Int, @RequestParam nQuestions: Int): List<AIQuestion> {
        return aiQuestionService.generateAndStoreSentenceQuestion(topic,difficulty,nQuestions)
    }

    @GetMapping("/topics")
    fun getAvailableTopics(): List<String> {
        return aiQuestionService.getAvailableTopics()
    }


    @PostMapping("/batch-generate")
    fun startBulkGeneration(
        @RequestParam(defaultValue = "100") totalQuestions: Int,
        @RequestParam(defaultValue = "5") batchSize: Int,
        @RequestParam(defaultValue = "3000") delayMs: Long
    ): CompletableFuture<String> {
        return batchService.generateBulkQuestions(totalQuestions, batchSize, delayMs)
    }

    @GetMapping("/batch-status")
    fun getStatus(): Map<String, Any> {
        return batchService.getGenerationStatus()
    }
}