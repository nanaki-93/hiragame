package com.github.nanaki_93.service

import com.github.nanaki_93.models.*

class GameService {


    fun calculateLevel(correctAnswers: Int): Int {
        return minOf(5, (correctAnswers / 10) + 1)
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

    // Add a separate method to get next character
    fun getNextCharacterAndClearFeedback(gameState: GameState): GameState {
        return gameState.getNextCharacter().copy(
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