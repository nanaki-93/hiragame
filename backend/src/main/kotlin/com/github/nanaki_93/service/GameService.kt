package com.github.nanaki_93.service

import com.github.nanaki_93.dto.game.toModel
import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.models.SelectRequest
import com.github.nanaki_93.models.GameStateUi
import com.github.nanaki_93.models.GameStatisticsUi
import com.github.nanaki_93.models.UserQuestionDto
import com.github.nanaki_93.models.Level
import com.github.nanaki_93.models.LevelListRequest
import com.github.nanaki_93.models.nextLevel
import com.github.nanaki_93.repository.Question
import com.github.nanaki_93.repository.QuestionRepository
import com.github.nanaki_93.repository.UserAnsweredQuestion
import com.github.nanaki_93.repository.UserAnsweredQuestionBulkRepository
import com.github.nanaki_93.repository.UserAnsweredQuestionRepository
import com.github.nanaki_93.repository.UserGameState
import com.github.nanaki_93.repository.UserGameStateRepository
import com.github.nanaki_93.repository.UserLevel

import com.github.nanaki_93.repository.UserLevelRepository
import com.github.nanaki_93.repository.toDto
import com.github.nanaki_93.util.toUUID
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime.now
import java.util.Arrays
import java.util.UUID
import kotlin.math.log

@Service
class GameService(
    private val levelRepo: UserLevelRepository,
    private val userQuestionRepo: UserAnsweredQuestionRepository,
    private val userGameStateRepo: UserGameStateRepository,
    private val questionRepo: QuestionRepository,
    private val bulkRepository: UserAnsweredQuestionBulkRepository
) {

    private val logger = LoggerFactory.getLogger(GameService::class.java)
    fun nextQuestion(selectReq: SelectRequest): Question? =
        userQuestionRepo.findRandomBySelect(selectReq)?.let {
            questionRepo.findById(it)
        }?.orElseThrow { RuntimeException("Question not found") }


    fun getAvailableLevels(req: LevelListRequest): List<Level> =
        levelRepo.findByUserIdAndGameModeAndIsAvailable(UUID.fromString(req.userId), req.gameMode, true)
            .map { it.level }
            .toList()


    fun getGameState(userId: String): UserGameState = userGameStateRepo.findByUserId(UUID.fromString(userId)) ?: userGameStateRepo.save(
        userGameStateRepo.save(
            UserGameState(
                id = UUID.randomUUID(),
                userId = userId.toUUID(),
                gameMode = GameMode.SIGN,
                level = Level.N5,
                score = 0,
                streak = 0,
                totalAnswered = 0,
                correctAnswers = 0,
                lastAnswerCorrect = null,
                createdAt = now(),
                updatedAt = now(),
            )
        )
    )


    //todo it could be a good start to a chat with ai
//    private fun sentenceToHiraganaQuestions(questions: List<AIQuestion>): List<QuestionRequest> {
//        return questions.map { QuestionRequest(it.japanese, it.romanization, it.translation) }
//    }

    fun processAnswer(answer: UserQuestionDto): GameStateUi {

        val currentState = userGameStateRepo.findByUserId(answer.userId.toUUID()) ?: throw RuntimeException("Game State not found")
        val question = questionRepo.findById(answer.questionId.toUUID()).orElseThrow { RuntimeException("Question not found") }

        if (answer.userInput.equals(question.romanization, ignoreCase = true)) {

            userQuestionRepo.findByUserIdAndQuestionId(answer.userId.toUUID(),answer.questionId.toUUID()).ifPresent { userQuestion ->
                userQuestionRepo.save(userQuestion.toDto().copy(isCorrect = true, answeredAt = now()).toModel())
            }

            val currLevelState = levelRepo.findByUserIdAndLevelAndGameMode(answer.userId.toUUID(), question.level, question.gameMode)
            //todo fix NextLevelCap
            if ((currLevelState.correctCount + 1) == 100) {
                levelRepo.save(currLevelState.toDto().copy(isAvailable = false, isCompleted = true, correctCount = 100).toModel())
                levelRepo.save(
                    currLevelState.toDto().copy(level = currLevelState.level.nextLevel(), isAvailable = true, isCompleted = false, correctCount = 0)
                        .toModel()
                )
            }

            val newStreak = currentState.streak + 1
            val newScore = currentState.score + (10 * newStreak)

            val gameState = userGameStateRepo.save(
                currentState.toDto().copy(
                    level = currLevelState.level,
                    score = newScore,
                    streak = newStreak,
                    totalAnswered = currentState.totalAnswered + 1,
                    correctAnswers = currentState.correctAnswers + 1,
                    lastAnswerCorrect = true,
                ).toModel()
            )

            return GameStateUi(
                userId = currentState.userId.toString(),
                feedback = generateFeedback(true, gameState.streak, question.japanese),
                isCorrect = true,
                updatedAt = currentState.updatedAt.toString(),
                stats = GameStatisticsUi(
                    score = currentState.score,
                    streak = currentState.streak,
                    totalAnswered = currentState.totalAnswered,
                    correctAnswers = currentState.correctAnswers,
                    lastAnswerCorrect = currentState.lastAnswerCorrect,
                )
            )


        } else {
            userQuestionRepo.findById(answer.questionId.toUUID()).ifPresent { uq ->
                userQuestionRepo.save(uq.toDto().copy(isCorrect = false, answeredAt = now(), attemps = uq.attemps?.inc()).toModel())
            }

            val gameState = userGameStateRepo.save(
                currentState.toDto().copy(
                    streak = 0,
                    totalAnswered = currentState.totalAnswered + 1,
                    lastAnswerCorrect = false
                ).toModel()
            )


            return GameStateUi(
                userId = gameState.userId.toString(),
                feedback = generateFeedback(false, 0, question.japanese),
                isCorrect = false,
                updatedAt = gameState.updatedAt.toString(),
                stats = GameStatisticsUi(
                    score = gameState.score,
                    streak = gameState.streak,
                    totalAnswered = gameState.totalAnswered,
                    correctAnswers = gameState.correctAnswers,
                    lastAnswerCorrect = gameState.lastAnswerCorrect,
                )
            )
        }

    }

    fun initGame(userId: UUID) {
        logger.info("Starting initQuestions")
        initQuestions(userId)
        logger.info("Finished initLevel")
        initLevel(userId)
    }

    private fun initQuestions(userId: UUID) {
        questionRepo.findAll()
            .map { question ->
                UserAnsweredQuestion(
                    userId = userId,
                    questionId = question.id,
                    isCorrect = false,
                    attemps = 0,
                    answeredAt = now(),
                    gameMode = question.gameMode,
                    level = question.level
                )
            }.let {
                logger.info("Questions found : ${it.size}")
                bulkRepository.bulkInsert(it)
            }
    }

    private fun initLevel(userId: UUID) {
        Arrays.stream(Level.entries.toTypedArray()).map { level ->
            Arrays.stream(GameMode.entries.toTypedArray()).map {
                UserLevel(
                    userId = userId,
                    level = level,
                    isAvailable = level == Level.N5,
                    isCompleted = false,
                    correctCount = 0,
                    gameMode = it,
                )
            }
        }.flatMap { it }
            .toList()
            .let {
                logger.info("Level found: ${it.size}")
                levelRepo.saveAll(it)
            }
    }

    private fun generateFeedback(isCorrect: Boolean, streak: Int, correctAnswer: String): String {
        return if (isCorrect) {
            when (streak) {
                in 1..2 -> "Correct! 正解！"
                in 3..5 -> "Great streak! 連続正解！"
                in 6..10 -> "Amazing! 素晴らしい！"
                else -> "Incredible! マスター級！"
            }
        } else {
            "Wrong! It's '$correctAnswer' 間違い！"
        }
    }
}