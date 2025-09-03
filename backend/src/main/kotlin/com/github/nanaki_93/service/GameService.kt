package com.github.nanaki_93.service

import com.github.nanaki_93.models.SelectRequest
import com.github.nanaki_93.models.GameStateReq
import com.github.nanaki_93.models.QuestionRequest
import com.github.nanaki_93.models.Level
import com.github.nanaki_93.models.nextLevel
import com.github.nanaki_93.repository.UserAnsweredQuestionRepository
import com.github.nanaki_93.repository.UserGameStateRepository

import com.github.nanaki_93.repository.UserLevelRepository
import com.github.nanaki_93.util.toUUID
import org.springframework.stereotype.Service
import java.time.LocalDateTime.now
import java.util.UUID

@Service
class GameService(
    private val levelRepo: UserLevelRepository,
    private val userQuestionRepo: UserAnsweredQuestionRepository,
    private val userGameStateRepo: UserGameStateRepository
) {

    fun nextQuestion(selectReq: SelectRequest) = userQuestionRepo.findRandomBySelect(selectReq)


    fun getAvailableLevels(selectRequest: SelectRequest): List<Level> =
        levelRepo.findByUserId(UUID.fromString(selectRequest.userId))
            .filter { it.isCompleted }
            .map { it.level }
            .toList()


    //todo it could be a good start to a chat with ai
//    private fun sentenceToHiraganaQuestions(questions: List<AIQuestion>): List<QuestionRequest> {
//        return questions.map { QuestionRequest(it.japanese, it.romanization, it.translation) }
//    }

    fun processAnswer(question: QuestionRequest ): GameStateReq {

        val currentState = userGameStateRepo.findByUserId(question.userId.toUUID())

        if(question.userInput.equals(question.japanese)){

            userQuestionRepo.findById(question.userQuestionId.toUUID()).ifPresent { userQuestion ->
                userQuestionRepo.save(userQuestion.copy(isCorrect = true, answeredAt = now()))
            }

            val currLevelState = levelRepo.findByUserIdAndLevelAndGameMode(question.userId.toUUID(),question.level.name,question.gameMode.name)
            //todo fix NextLevelCap
            if((currLevelState.correctCount +1) > 100){
                levelRepo.save(currLevelState.copy(isAvailable = false, isCompleted = true, correctCount = 100))
                levelRepo.save(currLevelState.copy(level = currLevelState.level.nextLevel(), isAvailable = true, isCompleted = false, correctCount = 0))
            }

            val newStreak = currentState.streak + 1
            val newScore = currentState.score + (10 * newStreak)
            val feedback = generateFeedback(true, newStreak, question.japanese)

            val gameState =userGameStateRepo.save(
                currentState.copy(
                    level = currLevelState.level,
                    score = newScore,
                    streak = newStreak,
                    totalAnswered = currentState.totalAnswered + 1,
                    correctAnswers = currentState.correctAnswers + 1,
                    lastAnswerCorrect = true,
                ))


            return GameStateReq(
                userId = currentState.userId.toString(),
                score = gameState.score,
                streak = gameState.streak,
                totalAnswered = gameState.totalAnswered,
                correctAnswers = gameState.correctAnswers,
                feedback = generateFeedback(true, gameState.streak, question.japanese),
                isCorrect = true
            )
        } else {
            userQuestionRepo.findById(question.userQuestionId.toUUID()).ifPresent { userQuestion ->
                userQuestionRepo.save(userQuestion.copy(isCorrect = false, answeredAt = now()))
            }

            userGameStateRepo.save(
                currentState.copy(
                    streak = 0,
                    totalAnswered = currentState.totalAnswered + 1,
                    lastAnswerCorrect = false
                )
            )

            val gameState = userGameStateRepo.findByUserId(question.userId.toUUID())
            return GameStateReq(
                userId = gameState.userId.toString(),
                score = gameState.score,
                streak = gameState.streak,
                totalAnswered = gameState.totalAnswered,
                correctAnswers = gameState.correctAnswers,
                feedback = generateFeedback(false, 0, question.japanese),
                isCorrect = false
            )
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