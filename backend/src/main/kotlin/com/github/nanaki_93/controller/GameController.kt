package com.github.nanaki_93.controller

import com.github.nanaki_93.models.SelectRequest
import com.github.nanaki_93.models.GameStateReq
import com.github.nanaki_93.models.Level
import com.github.nanaki_93.models.QuestionDto
import com.github.nanaki_93.models.UserQuestionDto
import com.github.nanaki_93.repository.Question
import com.github.nanaki_93.repository.toDto
import com.github.nanaki_93.service.GameService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = ["http://localhost:8081"])
class GameController(
    private val gameService: GameService
) {

    @PostMapping("/select-game-mode")
    fun selectGameMode(@RequestBody userId: String): List<Level> = gameService.getAvailableLevels(userId)

    @PostMapping("/next-question")
    fun selectLeve(@RequestBody request: SelectRequest): QuestionDto? = gameService.nextQuestion(request)?.toDto()

    @PostMapping("/process-answer")
    fun processAnswer(@RequestBody request: UserQuestionDto): GameStateReq = gameService.processAnswer(request)

}
