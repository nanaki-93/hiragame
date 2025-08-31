package com.github.nanaki_93.util

import com.github.nanaki_93.models.AIQuestion
import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.models.Level
import com.github.nanaki_93.repository.Question
import org.slf4j.LoggerFactory


fun AIQuestion.toHiraganaQuestion(gameMode: GameMode): Question =
    Question(
        japanese = japanese,
        romanization = romanization,
        translation = translation,
        topic = topic,
        level = level.name,
        gameMode = gameMode.name,
        hasKatakana = japanese.hasKatakana(),
        hasKanji = japanese.hasKanji(),
    )

 fun String.fromCsvLineToQuestion(): AIQuestion? {
    return try {
        val parts = this.split(";").map { it.trim().removeSurrounding("\"") }
        AIQuestion(parts[0], parts[1], parts[2], parts[3], Level.valueOf(parts[4]))
    } catch (e: Exception) {
        LoggerFactory.getLogger(this.javaClass).warn("Failed to parse CSV line: $this - ${e.message}")
        null
    }
}


fun String.cleanCsvFromMarkdown(): String = this
    .replace("```csv", "")
    .replace("```", "")
    .replace("CSV:", "")
    .trim()
    .lines()
    .filter { it.isNotBlank() && !it.startsWith("#") }
    .joinToString("\n")

fun String.cleanJsonFromMarkdown(): String = this
    .replace("```json", "")
    .replace("```", "")
    .trim()

fun String.hasKatakana(): Boolean = this.any { it in '\u30A0'..'\u30FF' }
fun String.hasKanji(): Boolean = this.any { it in '\u4E00'..'\u9FFF' }
