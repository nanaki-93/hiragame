package com.github.nanaki_93.service

import com.github.nanaki_93.models.AISentenceQuestion
import com.github.nanaki_93.models.AIWordQuestion
import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.models.GameState
import com.github.nanaki_93.models.HiraganaQuestion
import com.github.nanaki_93.models.getNextQuestion
import com.github.nanaki_93.models.hiraganaLvMap
import org.springframework.stereotype.Service

@Service
class GameService(private val aiQuestionService: AIQuestionService) {
    fun calculateLevel(correctAnswers: Int): Int {
        return minOf(5, (correctAnswers / 10) + 1)
    }

    fun selectGameMode(gameMode: GameMode): GameState {
        return when (gameMode) {
            GameMode.SIGN -> GameState(hiraganaList = hiraganaLvMap[1] ?: emptyList()).getNextQuestion()
            GameMode.WORD -> GameState(hiraganaList = hiraganaWordQuestions("food")).getNextQuestion()
            GameMode.SENTENCE -> GameState(hiraganaList = hiraganaSentenceQuestions("food")).getNextQuestion()
        }

    }

    private fun hiraganaWordQuestions(topic: String): List<HiraganaQuestion> {
        return wordToHiraganaQuestions(aiQuestionService.generateWordQuestion(topic))
    }

    private fun hiraganaSentenceQuestions(topic: String): List<HiraganaQuestion> {
        return sentenceToHiraganaQuestions(aiQuestionService.generateSentenceQuestion(topic))
    }

    private fun wordToHiraganaQuestions(questions: List<AIWordQuestion>): List<HiraganaQuestion> {
        return questions.map { HiraganaQuestion(it.hiraganaWord, it.romanization,it.englishWord) }
    }
    private fun sentenceToHiraganaQuestions(questions: List<AISentenceQuestion>): List<HiraganaQuestion> {
        return questions.map { HiraganaQuestion(it.hiraganaSentence, it.romanization,it.englishSentence) }
    }

    fun processAnswer(
        gameState: GameState,
        userInput: String,
        level: Int,
    ): GameState {
        val currentChar = gameState.currentChar ?: return gameState
        val isCorrect = userInput.lowercase().trim() == currentChar.romanization.lowercase()

        val newCorrectAnswers = if (isCorrect) gameState.correctAnswers + 1 else gameState.correctAnswers
        val newStreak = if (isCorrect) gameState.streak + 1 else 0
        val newScore = gameState.score + if (isCorrect) (10 + newStreak * 2) else 0
        val newLevel = calculateLevel(newCorrectAnswers)
        var newList = gameState.hiraganaList

        if (isCorrect) {
            newList = gameState.hiraganaList.filter { it.char != currentChar.char }
        }
        if (newLevel > level) {
            newList = hiraganaLvMap[newLevel] ?: emptyList()
        }
        return gameState.copy(
            score = newScore,
            streak = newStreak,
            totalAnswered = gameState.totalAnswered + 1,
            hiraganaList = newList,
            correctAnswers = newCorrectAnswers,
            level = newLevel,
            feedback = generateFeedback(isCorrect, newStreak, currentChar.romanization),
            isCorrect = isCorrect
        )
    }

    fun getNextCharacterAndClearFeedback(gameState: GameState): GameState {
        return gameState.getNextQuestion().copy(
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