package com.github.nanaki_93.controller

import com.github.nanaki_93.models.AIQuestion

import com.github.nanaki_93.service.AIQuestionService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = ["http://localhost:8081"])
class AIController(
    private val aiQuestionService: AIQuestionService
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
}