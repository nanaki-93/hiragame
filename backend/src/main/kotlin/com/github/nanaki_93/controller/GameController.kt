package com.github.nanaki_93.controller

import com.github.nanaki_93.models.SelectRequest
import com.github.nanaki_93.models.GameStateUi
import com.github.nanaki_93.models.Level
import com.github.nanaki_93.models.LevelListRequest
import com.github.nanaki_93.models.QuestionDto
import com.github.nanaki_93.models.UserQuestionDto
import com.github.nanaki_93.repository.toUi
import com.github.nanaki_93.service.GameService
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class GameController(
    private val gameService: GameService
) {
    private val logger = LoggerFactory.getLogger(GameController::class.java)

    @PostMapping("/select-game-mode")
    fun selectGameMode(@RequestBody req: LevelListRequest): List<Level> {
        val response = gameService.getAvailableLevels(req)
        logger.info("/select-game-mode req {} , res {}", req, response)
        return response
    }

    @PostMapping("/next-question")
    fun nextQuestion(@RequestBody request: SelectRequest): QuestionDto? {
        val response = gameService.nextQuestion(request)?.toUi()
        logger.info("/next-question req {} , res {}", request, response)
        return response
    }

    @PostMapping("/process-answer")
    fun processAnswer(@RequestBody request: UserQuestionDto): GameStateUi {
        val response = gameService.processAnswer(request)
        logger.info("/process-answer req {} , res {}", request, response)
        return response
    }

    @PostMapping("/get-game-state")
    fun getGameState(@RequestBody userId: String): GameStateUi {
        val response = gameService.getGameState(userId).toUi()
        logger.info("/get-game-state req {} , res {}", userId, response)
        return response
    }

}
