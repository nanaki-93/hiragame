package com.github.nanaki_93.controller

import com.github.nanaki_93.models.SelectRequest
import com.github.nanaki_93.models.GameStateUi
import com.github.nanaki_93.models.Level
import com.github.nanaki_93.models.LevelListRequest
import com.github.nanaki_93.models.QuestionUi
import com.github.nanaki_93.models.UserQuestionDto
import com.github.nanaki_93.repository.toUi
import com.github.nanaki_93.service.GameService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = ["http://localhost:8081"])
class GameController(
    private val gameService: GameService
) {

    @PostMapping("/select-game-mode")
    fun selectGameMode(@RequestBody req: LevelListRequest): List<Level> = gameService.getAvailableLevels(req)

    @PostMapping("/next-question")
    fun selectLevel(@RequestBody request: SelectRequest): QuestionUi? = gameService.nextQuestion(request)?.toUi()

    @PostMapping("/process-answer")
    fun processAnswer(@RequestBody request: UserQuestionDto): GameStateUi = gameService.processAnswer(request)

    @PostMapping("/get-game-state")
      fun getGameState(@RequestBody userId: String): GameStateUi = gameService.getGameState(userId).toUi()

}
