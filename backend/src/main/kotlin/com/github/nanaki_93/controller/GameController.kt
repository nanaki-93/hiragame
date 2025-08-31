package com.github.nanaki_93.controller

import com.github.nanaki_93.models.GameModeRequest
import com.github.nanaki_93.models.GameState
import com.github.nanaki_93.models.GameStateRequest
import com.github.nanaki_93.models.ProcessAnswerRequest
import com.github.nanaki_93.service.GameService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = ["http://localhost:8081"])
class GameController(
    private val gameService: GameService
) {

    @PostMapping("/process-answer")
    fun processAnswer(@RequestBody request: ProcessAnswerRequest): GameState = gameService.processAnswer(request.gameState, request.userInput, request.level)

    @PostMapping("/next-character")
    fun nextCharacter(@RequestBody request: GameStateRequest): GameState = gameService.getNextCharacterAndClearFeedback(request.gameState)

    @PostMapping("/game-mode")
    fun selectGameMode(@RequestBody request: GameModeRequest): GameState = gameService.selectGameMode(request.gameMode)
}
