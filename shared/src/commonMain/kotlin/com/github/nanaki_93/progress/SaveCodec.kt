package com.github.nanaki_93.progress

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random

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

/** Compatibility fixture only: this precursor was not previously shipped as a learner save. */
@Serializable
private data class VersionZeroSave(
    @SerialName("schemaVersion") val schemaVersion: Int,
    @SerialName("savedAtEpochMs") val savedAtEpochMs: Long,
    @SerialName("preferences") val preferences: VersionZeroPreferences,
    @SerialName("lessonProgress") val lessonProgress: List<LessonProgress>,
    @SerialName("reviewItems") val reviewItems: List<ReviewItemProgress>,
)

@Serializable
private data class VersionZeroPreferences(
    @SerialName("colorMode") val colorMode: SavedColorMode,
    @SerialName("showReadings") val showReadings: Boolean = true,
    @SerialName("showTranslation") val showTranslation: Boolean = true,
    @SerialName("showRomaji") val showRomaji: Boolean = false,
) {
    fun toCurrent() = SavePreferences(colorMode, showReadings, showTranslation, showRomaji)
}

private fun newMigrationIdentity(): String =
    "migration_${Random.nextInt().toUInt().toString(16)}_${Random.nextInt().toUInt().toString(16)}"

object SaveCodec {
    private val wire = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        encodeDefaults = true
    }

    /** The identity supplier is called only for a structurally valid version-0 candidate. */
    fun decodeSave(
        raw: String,
        newSnapshotId: () -> String = ::newMigrationIdentity,
    ): SaveDecodeResult {
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
        // Never default an absent, quoted, fractional, negative or future version.
        if (version.isString) return SaveDecodeResult.Protected(SaveProblem.UNSUPPORTED_VERSION)
        return when (version.content) {
            "0" -> decodeVersionZero(raw, root, newSnapshotId)
            SaveBounds.SCHEMA_VERSION.toString() -> decodeVersionOne(raw, root)
            else -> SaveDecodeResult.Protected(SaveProblem.UNSUPPORTED_VERSION)
        }
    }

    private fun decodeVersionOne(raw: String, root: JsonObject): SaveDecodeResult {
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

    private fun decodeVersionZero(
        raw: String,
        root: JsonObject,
        newSnapshotId: () -> String,
    ): SaveDecodeResult {
        val precursor = try {
            wire.decodeFromString<VersionZeroSave>(raw)
        } catch (_: IllegalArgumentException) {
            return SaveDecodeResult.Protected(SaveProblem.INVALID_SNAPSHOT)
        }
        // Compare against the precursor, not the migrated envelope: only its documented
        // optional preference fields may be absent. Do not let JS coerce wire numbers.
        val canonical = wire.parseToJsonElement(wire.encodeToString(precursor))
        if (!matchesWireTypes(root, canonical)) return SaveDecodeResult.Protected(SaveProblem.INVALID_SNAPSHOT)
        // Validate the precursor's records and dates before requesting a new identity.
        val candidate = SaveEnvelope(
            savedAtEpochMs = precursor.savedAtEpochMs,
            snapshotId = "migration_placeholder",
            revision = 0,
            preferences = precursor.preferences.toCurrent(),
            lessonProgress = precursor.lessonProgress,
            reviewItems = precursor.reviewItems,
        )
        try {
            validateSave(candidate)
        } catch (_: IllegalArgumentException) {
            return SaveDecodeResult.Protected(SaveProblem.INVALID_SNAPSHOT)
        }
        // The injected supplier is outside the codec's control; a failed identity must not
        // escape decoding or turn the original save into a usable partial migration.
        val identity = try {
            newSnapshotId()
        } catch (_: Throwable) {
            return SaveDecodeResult.Protected(SaveProblem.INVALID_SNAPSHOT)
        }
        val migrated = try {
            candidate.copy(snapshotId = identity).also(::validateSave)
        } catch (_: IllegalArgumentException) {
            return SaveDecodeResult.Protected(SaveProblem.INVALID_SNAPSHOT)
        }
        // Apply the version-1 byte budget as well: never expose a snapshot that cannot be saved.
        try {
            encodeSave(migrated)
        } catch (failure: SaveEncodeException) {
            return SaveDecodeResult.Protected(failure.reason)
        }
        return SaveDecodeResult.Valid(migrated, migratedFrom = 0)
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

fun decodeSave(raw: String, newSnapshotId: () -> String = ::newMigrationIdentity): SaveDecodeResult =
    SaveCodec.decodeSave(raw, newSnapshotId)
fun encodeSave(snapshot: SaveEnvelope): String = SaveCodec.encodeSave(snapshot)
