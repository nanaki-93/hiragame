package com.github.nanaki_93.controller

import com.github.nanaki_93.models.AIWordQuestion
import com.github.nanaki_93.models.AISentenceQuestion
import com.github.nanaki_93.service.AIQuestionService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = ["http://localhost:8081"])
class AIController(
    private val aiQuestionService: AIQuestionService
) {

    @GetMapping("/word")
    fun generateWord(@RequestParam topic: String): AIWordQuestion {
        return aiQuestionService.generateWordQuestion(topic)
    }

    @GetMapping("/sentence")
    fun generateSentence(@RequestParam topic: String): AISentenceQuestion {
        return aiQuestionService.generateSentenceQuestion(topic)
    }

    @GetMapping("/topics")
    fun getAvailableTopics(): List<String> {
        return aiQuestionService.getAvailableTopics()
    }
}