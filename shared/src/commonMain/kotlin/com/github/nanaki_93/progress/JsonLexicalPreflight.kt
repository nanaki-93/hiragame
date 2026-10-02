package com.github.nanaki_93.progress

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Bounded lexical inspection before building a JSON tree; the caller still parses full grammar. */
internal fun inspectJsonLexically(raw: String, maxDepth: Int): SaveProblem? {
    require(maxDepth > 0)
    // A frame holds object keys (null for an array) and whether its next string is a key.
    data class Frame(val keys: MutableSet<String>?, var expectingKey: Boolean)
    val stack = ArrayList<Frame>()
    var i = 0
    while (i < raw.length) {
        when (raw[i]) {
            '{', '[' -> {
                if (stack.size == maxDepth) return SaveProblem.TOO_DEEP
                stack.add(Frame(if (raw[i] == '{') HashSet() else null, raw[i] == '{'))
                i++
            }
            '}', ']' -> {
                if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                i++
            }
            ',' -> {
                stack.lastOrNull()?.let { if (it.keys != null) it.expectingKey = true }
                i++
            }
            '"' -> {
                val start = i++
                var closed = false
                while (i < raw.length) {
                    when (raw[i++]) {
                        '\\' -> if (i < raw.length) i++
                        '"' -> { closed = true; break }
                    }
                }
                if (!closed) return SaveProblem.MALFORMED_JSON
                val frame = stack.lastOrNull()
                if (frame?.expectingKey == true && frame.keys != null) {
                    // Decode escaped key spellings before comparing keys, as the JSON parser
                    // otherwise collapses duplicates when it constructs an object.
                    val key = try {
                        Json.decodeFromString<String>(raw.substring(start, i))
                    } catch (_: IllegalArgumentException) {
                        return SaveProblem.MALFORMED_JSON
                    }
                    if (!frame.keys.add(key)) return SaveProblem.DUPLICATE_KEY
                    frame.expectingKey = false
                }
            }
            else -> i++
        }
    }
    return null
}
