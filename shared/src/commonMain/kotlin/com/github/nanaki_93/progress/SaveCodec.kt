package com.github.nanaki_93.progress

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** These are fixed categories: neither exception messages nor saved text cross the codec boundary. */
enum class SaveProblem {
    OVERSIZED, TOO_DEEP, MALFORMED_JSON, DUPLICATE_KEY, UNSUPPORTED_VERSION, INVALID_SNAPSHOT,
}

sealed interface SaveDecodeResult {
    data class Valid(val snapshot: SaveEnvelope, val migratedFrom: Int? = null) : SaveDecodeResult
    data class Protected(val reason: SaveProblem) : SaveDecodeResult
}

/** An invalid in-memory snapshot is never serialized or partially returned. */
class SaveEncodeException(val reason: SaveProblem) : IllegalArgumentException("Cannot encode save: ${reason.name}")

object SaveCodec {
    private val wire = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        encodeDefaults = true
    }

    fun decodeSave(raw: String): SaveDecodeResult {
        // Length is a cheap first guard, including for enormous inputs with no closing bracket.
        if (raw.length > SaveBounds.MAX_JSON_BYTES || raw.encodeToByteArray().size > SaveBounds.MAX_JSON_BYTES) {
            return SaveDecodeResult.Protected(SaveProblem.OVERSIZED)
        }
        val lexical = inspect(raw)
        if (lexical != null) return SaveDecodeResult.Protected(lexical)

        val root = try {
            wire.parseToJsonElement(raw)
        } catch (_: IllegalArgumentException) {
            return SaveDecodeResult.Protected(SaveProblem.MALFORMED_JSON)
        }
        if (!hasJsonLiterals(root)) return SaveDecodeResult.Protected(SaveProblem.MALFORMED_JSON)
        val fields = root as? JsonObject ?: return SaveDecodeResult.Protected(SaveProblem.INVALID_SNAPSHOT)
        val version = fields["schemaVersion"] as? JsonPrimitive
            ?: return SaveDecodeResult.Protected(SaveProblem.UNSUPPORTED_VERSION)
        // Never allow a defaulted schemaVersion to turn an unversioned object into version 1.
        if (version.isString || version.content != SaveBounds.SCHEMA_VERSION.toString()) {
            return SaveDecodeResult.Protected(SaveProblem.UNSUPPORTED_VERSION)
        }
        val snapshot = try {
            wire.decodeFromString<SaveEnvelope>(raw).also(::validateSave)
        } catch (_: IllegalArgumentException) {
            return SaveDecodeResult.Protected(SaveProblem.INVALID_SNAPSHOT)
        }
        val canonical = try {
            wire.parseToJsonElement(encodeSave(snapshot))
        } catch (failure: SaveEncodeException) {
            return SaveDecodeResult.Protected(failure.reason)
        }
        // In JS, kotlinx.serialization can coerce a quoted number to Long. Compare the
        // original wire types against the validated canonical schema instead of trusting it.
        if (!matchesWireTypes(root, canonical)) return SaveDecodeResult.Protected(SaveProblem.INVALID_SNAPSHOT)
        return SaveDecodeResult.Valid(snapshot)
    }

    private val integerToken = Regex("-?(0|[1-9][0-9]*)")
    private val jsonNumber = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

    private fun hasJsonLiterals(value: JsonElement): Boolean = when (value) {
        is JsonObject -> value.values.all(::hasJsonLiterals)
        is JsonArray -> value.all(::hasJsonLiterals)
        JsonNull -> true
        is JsonPrimitive -> value.isString || value.content in setOf("true", "false") || jsonNumber.matches(value.content)
    }

    private fun matchesWireTypes(original: JsonElement, canonical: JsonElement): Boolean = when (original) {
        is JsonObject -> canonical is JsonObject && original.all { (key, value) ->
            canonical[key]?.let { matchesWireTypes(value, it) } == true
        }
        is JsonArray -> canonical is JsonArray && original.size == canonical.size &&
            original.indices.all { matchesWireTypes(original[it], canonical[it]) }
        JsonNull -> canonical == JsonNull
        is JsonPrimitive -> canonical is JsonPrimitive && canonical != JsonNull &&
            original.isString == canonical.isString && (canonical.isString ||
                if (canonical.content == "true" || canonical.content == "false") original.content == canonical.content
                else integerToken.matches(original.content))
    }

    fun encodeSave(snapshot: SaveEnvelope): String {
        try {
            validateSave(snapshot)
        } catch (_: IllegalArgumentException) {
            throw SaveEncodeException(SaveProblem.INVALID_SNAPSHOT)
        }
        val raw = wire.encodeToString(snapshot)
        if (raw.length > SaveBounds.MAX_JSON_BYTES || raw.encodeToByteArray().size > SaveBounds.MAX_JSON_BYTES) {
            throw SaveEncodeException(SaveProblem.OVERSIZED)
        }
        // Generated JSON is shallow, but keep the limit symmetric for future schema changes.
        inspect(raw)?.let { throw SaveEncodeException(it) }
        return raw
    }

    /** Lexical, iterative preflight: no recursive JSON tree construction before the depth check. */
    private fun inspect(raw: String): SaveProblem? {
        // A frame holds the keys of one object (null for an array), plus whether its next
        // string token is a key. The JSON parser below remains responsible for full grammar.
        data class Frame(val keys: MutableSet<String>?, var expectingKey: Boolean)
        val stack = ArrayList<Frame>()
        var i = 0
        while (i < raw.length) {
            when (raw[i]) {
                '{', '[' -> {
                    if (stack.size == SaveBounds.MAX_JSON_DEPTH) return SaveProblem.TOO_DEEP
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
                        val key = try {
                            wire.decodeFromString<String>(raw.substring(start, i))
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
}

fun decodeSave(raw: String): SaveDecodeResult = SaveCodec.decodeSave(raw)
fun encodeSave(snapshot: SaveEnvelope): String = SaveCodec.encodeSave(snapshot)
