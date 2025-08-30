
package com.github.nanaki_93.util

object CleanUtil {
    fun cleanCsvFromMarkdown(text: String): String = text
        .replace("```csv", "")
        .replace("```", "")
        .replace("CSV:", "")
        .trim()
        .lines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .joinToString("\n")

    fun cleanJsonFromMarkdown(text: String): String = text
        .replace("```json", "")
        .replace("```", "")
        .trim()

    /**
     * Checks if a string contains only hiragana characters, spaces, and basic punctuation
     */
    fun isOnlyHiragana(text: String): Boolean {
        val hiraganaRange = '\u3041'..'\u3096'  // Hiragana range
        val allowedChars = setOf(' ', '。', '、', 'ー', '！', '？') // Basic punctuation

        return text.all { char ->
            char in hiraganaRange || char in allowedChars
        }
    }

    /**
     * Filters CSV lines to keep only those with hiragana-only text
     */
    fun filterHiraganaOnlyLines(csvText: String): String {
        return csvText.lines()
            .filter { line ->
                if (line.isBlank() || !line.contains(";")) return@filter false
                val parts = line.split(";")
                if (parts.isEmpty()) return@filter false
                isOnlyHiragana(parts[0]) // Check if the hiragana part contains only hiragana
            }
            .joinToString("\n")
    }
}