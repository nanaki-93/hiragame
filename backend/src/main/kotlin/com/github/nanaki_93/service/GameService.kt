package com.github.nanaki_93.service

import com.github.nanaki_93.models.SelectRequest
import com.github.nanaki_93.models.GameStateReq
import com.github.nanaki_93.models.QuestionRequest
import com.github.nanaki_93.models.Level
import com.github.nanaki_93.repository.UserAnsweredQuestionRepository
import com.github.nanaki_93.repository.UserGameStateRepository

import com.github.nanaki_93.repository.UserLevelRepository
import com.github.nanaki_93.util.toUUID
import org.springframework.stereotype.Service
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
            .map { Level.valueOf(it.level) }
            .toList()


    //todo it could be a good start to a chat with ai
//    private fun sentenceToHiraganaQuestions(questions: List<AIQuestion>): List<QuestionRequest> {
//        return questions.map { QuestionRequest(it.japanese, it.romanization, it.translation) }
//    }

    fun processAnswer(question: QuestionRequest ): GameStateReq {

        val currentState = userGameStateRepo.findByUserId(question.userId.toUUID())

        if(question.userInput.equals(question.japanese)){

            userQuestionRepo.findById(question.userQuestionId.toUUID()).ifPresent { userQuestion ->
                userQuestionRepo.save(userQuestion.copy(isCorrect = true))
            }

            val curLevel = levelRepo.findByUserIdAndLevelAndGameMode(question.userId.toUUID(),question.level.name,question.gameMode.name)


            //check if new level
            //get new question
            //update game state
        }
        else{
            //update question with attemps
            //get new question
            //update game state
        }
        return GameStateReq(
            userId = question.userId,
            score = 0,
            streak = 0,
            totalAnswered = 0,
            correctAnswers = 0,
            feedback = "",
            isCorrect = null
        )
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