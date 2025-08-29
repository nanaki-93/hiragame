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
}

