package com.github.nanaki_93.content

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Strict, single-document wire decoding. References, review evidence and paths are checked by the CLI. */
object ContentCodec {
    private val wire = Json { ignoreUnknownKeys = false; isLenient = false; coerceInputValues = false }

    fun decodeCatalog(json: String): ContentCatalog = decode(json)
    fun decodeLesson(json: String): Lesson = decode(json)
    fun decodePracticeSet(json: String): PracticeSet = decode(json)

    private inline fun <reified T> decode(source: String): T {
        // kotlinx.serialization's JSON tree overwrites repeated object keys. Check the original
        // tokens first so repeated keys (including differently escaped spellings) cannot hide data.
        wire.parseToJsonElement(source) // syntax check before the token walk
        rejectDuplicateKeys(source)
        return wire.decodeFromString(source)
    }

    private fun rejectDuplicateKeys(source: String) {
        var position = 0
        fun space() { while (position < source.length && source[position].isWhitespace()) position++ }
        fun stringToken(): String {
            val start = position++ // opening quote
            while (position < source.length) {
                when (source[position++]) {
                    '\\' -> position++ // skip escaped quote (and all other escaped characters)
                    '"' -> return wire.decodeFromString(source.substring(start, position))
                }
            }
            error("Invalid JSON string") // unreachable after syntax check
        }
        fun value() {
            space()
            when (source[position]) {
                '{' -> {
                    position++
                    space()
                    val keys = mutableSetOf<String>()
                    while (source[position] != '}') {
                        val key = stringToken()
                        require(keys.add(key)) { "Duplicate JSON key: $key" }
                        space()
                        position++ // colon
                        value()
                        space()
                        if (source[position] != ',') break
                        position++
                        space()
                    }
                    position++ // closing brace
                }
                '[' -> {
                    position++
                    space()
                    while (source[position] != ']') {
                        value()
                        space()
                        if (source[position] != ',') break
                        position++
                    }
                    position++
                }
                '"' -> { stringToken() }
                else -> while (position < source.length && source[position] !in ",]} \t\r\n") position++
            }
        }
        value()
    }
}
