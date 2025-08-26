package com.github.nanaki_93.controller

import com.github.nanaki_93.models.GameState
import com.github.nanaki_93.models.GameStateRequest
import com.github.nanaki_93.models.ProcessAnswerRequest
import com.github.nanaki_93.service.GameService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class GameController(
    private val gameService: GameService = GameService()
) {

    @PostMapping("/process-answer")
    fun processAnswer(@RequestBody request: ProcessAnswerRequest): GameState {
        return gameService.processAnswer(request.gameState, request.userInput, request.level)
    }

    @PostMapping("/next-character")
    fun nextCharacter(@RequestBody request: GameStateRequest): GameState {
        return gameService.getNextCharacterAndClearFeedback(request.gameState)
    }
}
