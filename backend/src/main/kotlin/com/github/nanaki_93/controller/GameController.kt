package com.github.nanaki_93.controller

import com.github.nanaki_93.models.SelectRequest
import com.github.nanaki_93.models.GameStateReq
import com.github.nanaki_93.models.Level
import com.github.nanaki_93.models.QuestionRequest
import com.github.nanaki_93.repository.Question
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
    fun selectGameMode(@RequestBody request: SelectRequest): ResponseEntity<List<Level>> = ResponseEntity.ok(gameService.getAvailableLevels(request))

    @PostMapping("/next-question")
    fun selectLeve(@RequestBody request: SelectRequest): ResponseEntity<Question> = ResponseEntity.ok(gameService.nextQuestion(request))

    @PostMapping("/process-answer")
    fun processAnswer(@RequestBody request: QuestionRequest): GameStateReq = gameService.processAnswer(request)

}
