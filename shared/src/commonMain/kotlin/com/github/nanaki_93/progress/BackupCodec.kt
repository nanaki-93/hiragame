package com.github.nanaki_93.progress

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object BackupBounds {
    const val FORMAT_VERSION = 1
    const val APP_ID = "hiragame"
    const val MAX_JSON_BYTES = SaveBounds.MAX_JSON_BYTES + 4 * 1024
    const val MAX_JSON_DEPTH = SaveBounds.MAX_JSON_DEPTH + 2
}

/** Fixed categories; raw input and parser messages never cross this boundary. */
enum class BackupProblem {
    OVERSIZED, TOO_DEEP, MALFORMED_JSON, DUPLICATE_KEY, INVALID_ROOT,
    UNSUPPORTED_FORMAT, UNSUPPORTED_SAVE_VERSION, WRONG_APP, INVALID_METADATA, INVALID_SAVE,
}

data class ValidatedBackup(
    val snapshot: SaveEnvelope,
    val sourceSchemaVersion: Int,
    val migratedFrom: Int?,
    val appVersion: String?,
    val exportedAtEpochMs: Long?,
)

sealed interface BackupDecodeResult {
    data class Valid(val backup: ValidatedBackup) : BackupDecodeResult
    data class Rejected(val reason: BackupProblem) : BackupDecodeResult
}

class BackupEncodeException(val reason: BackupProblem) : IllegalArgumentException("Cannot encode backup: ${reason.name}")

object BackupCodec {
    private val json = Json { encodeDefaults = true }
    private val versionPattern = Regex("[A-Za-z0-9._+\\-]{1,64}")
    private val integerPattern = Regex("0|[1-9][0-9]*")
    private val wrapperKeys = setOf("backupFormatVersion", "appId", "appVersion", "exportedAtEpochMs", "save")

    fun encodeBackup(snapshot: SaveEnvelope, appVersion: String, exportedAtEpochMs: Long): String {
        if (!versionPattern.matches(appVersion) || exportedAtEpochMs !in 0..SaveBounds.MAX_EPOCH_MS) {
            throw BackupEncodeException(BackupProblem.INVALID_METADATA)
        }
        val save = try {
            SaveCodec.encodeSave(snapshot)
        } catch (failure: SaveEncodeException) {
            throw BackupEncodeException(if (failure.reason == SaveProblem.OVERSIZED) BackupProblem.OVERSIZED else BackupProblem.INVALID_SAVE)
        }
        val raw = "{\"backupFormatVersion\":1,\"appId\":\"hiragame\",\"appVersion\":" +
            json.encodeToString(kotlinx.serialization.serializer<String>(), appVersion) +
            ",\"exportedAtEpochMs\":$exportedAtEpochMs,\"save\":$save}"
        if (raw.encodeToByteArray().size > BackupBounds.MAX_JSON_BYTES) throw BackupEncodeException(BackupProblem.OVERSIZED)
        if (inspectJsonLexically(raw, BackupBounds.MAX_JSON_DEPTH) != null) throw BackupEncodeException(BackupProblem.TOO_DEEP)
        return raw
    }

    fun decodeBackup(raw: String, newSnapshotId: () -> String = { "migration_${kotlin.random.Random.nextInt().toUInt().toString(16)}_${kotlin.random.Random.nextInt().toUInt().toString(16)}" }): BackupDecodeResult {
        if (raw.length > BackupBounds.MAX_JSON_BYTES || raw.encodeToByteArray().size > BackupBounds.MAX_JSON_BYTES) {
            return BackupDecodeResult.Rejected(BackupProblem.OVERSIZED)
        }
        inspectJsonLexically(raw, BackupBounds.MAX_JSON_DEPTH)?.let {
            return BackupDecodeResult.Rejected(when (it) {
                SaveProblem.TOO_DEEP -> BackupProblem.TOO_DEEP
                SaveProblem.DUPLICATE_KEY -> BackupProblem.DUPLICATE_KEY
                else -> BackupProblem.MALFORMED_JSON
            })
        }
        val root = try { json.parseToJsonElement(raw) } catch (_: IllegalArgumentException) {
            return BackupDecodeResult.Rejected(BackupProblem.MALFORMED_JSON)
        }
        val fields = root as? JsonObject ?: return BackupDecodeResult.Rejected(BackupProblem.INVALID_ROOT)
        // Any wrapper marker commits to wrapper validation; never downgrade a broken wrapper.
        if (fields.keys.any { it in wrapperKeys }) {
            if (fields.keys != wrapperKeys) return BackupDecodeResult.Rejected(BackupProblem.INVALID_METADATA)
            if (fields["backupFormatVersion"]?.integerToken() != "1") {
                return BackupDecodeResult.Rejected(BackupProblem.UNSUPPORTED_FORMAT)
            }
            if (fields["appId"]?.stringToken() != BackupBounds.APP_ID) {
                return BackupDecodeResult.Rejected(BackupProblem.WRONG_APP)
            }
            val appVersion = fields["appVersion"]?.stringToken()
            val exportedAt = fields["exportedAtEpochMs"]?.integerToken()?.takeIf(integerPattern::matches)?.toLongOrNull()
            if (appVersion == null || !versionPattern.matches(appVersion) || exportedAt == null || exportedAt !in 0..SaveBounds.MAX_EPOCH_MS) {
                return BackupDecodeResult.Rejected(BackupProblem.INVALID_METADATA)
            }
            if (fields["save"] !is JsonObject) return BackupDecodeResult.Rejected(BackupProblem.INVALID_SAVE)
            // Pass the original nested bytes to SaveCodec: whitespace and escape sequences also
            // count toward its independent byte budget, not only a normalized JSON tree.
            val saveRaw = extractSaveValue(raw) ?: return BackupDecodeResult.Rejected(BackupProblem.MALFORMED_JSON)
            return decodeCandidate(saveRaw, newSnapshotId, appVersion, exportedAt)
        }
        return decodeCandidate(raw, newSnapshotId, null, null)
    }

    private fun decodeCandidate(raw: String, newSnapshotId: () -> String, appVersion: String?, exportedAt: Long?): BackupDecodeResult {
        return when (val decoded = SaveCodec.decodeSave(raw, newSnapshotId)) {
            is SaveDecodeResult.Protected -> BackupDecodeResult.Rejected(when (decoded.reason) {
                SaveProblem.OVERSIZED -> BackupProblem.OVERSIZED
                SaveProblem.TOO_DEEP -> BackupProblem.TOO_DEEP
                SaveProblem.DUPLICATE_KEY -> BackupProblem.DUPLICATE_KEY
                SaveProblem.MALFORMED_JSON -> BackupProblem.MALFORMED_JSON
                SaveProblem.UNSUPPORTED_VERSION -> BackupProblem.UNSUPPORTED_SAVE_VERSION
                else -> BackupProblem.INVALID_SAVE
            })
            is SaveDecodeResult.Valid -> BackupDecodeResult.Valid(ValidatedBackup(
                decoded.snapshot, decoded.migratedFrom ?: SaveBounds.SCHEMA_VERSION,
                decoded.migratedFrom, appVersion, exportedAt,
            ))
        }
    }

    private fun kotlinx.serialization.json.JsonElement.integerToken(): String? =
        (this as? JsonPrimitive)?.takeUnless { it.isString }?.content
    private fun kotlinx.serialization.json.JsonElement.stringToken(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** Called only after grammar and duplicate-key validation; locate the top-level save's raw span. */
    private fun extractSaveValue(raw: String): String? {
        var i = raw.indexOf('{') + 1
        fun skipSpace() { while (i < raw.length && raw[i].isWhitespace()) i++ }
        fun skipString() {
            i++
            while (i < raw.length) {
                when (raw[i++]) {
                    '\\' -> i++
                    '"' -> return
                }
            }
        }
        while (i < raw.length) {
            skipSpace()
            if (raw[i] == '}') return null
            val keyStart = i
            skipString()
            val key = json.decodeFromString<String>(raw.substring(keyStart, i))
            skipSpace(); i++ // colon
            skipSpace()
            val start = i
            if (raw[i] == '{' || raw[i] == '[') {
                var depth = 0
                do {
                    when (raw[i]) {
                        '"' -> skipString()
                        '{', '[' -> { depth++; i++ }
                        '}', ']' -> { depth--; i++ }
                        else -> i++
                    }
                } while (depth > 0)
            } else if (raw[i] == '"') skipString()
            else while (i < raw.length && raw[i] != ',' && raw[i] != '}') i++
            if (key == "save") return raw.substring(start, i).trimEnd()
            skipSpace()
            if (raw[i] == ',') i++ else return null
        }
        return null
    }
}

fun decodeBackup(raw: String, newSnapshotId: () -> String = { "migration_${kotlin.random.Random.nextInt().toUInt().toString(16)}_${kotlin.random.Random.nextInt().toUInt().toString(16)}" }): BackupDecodeResult =
    BackupCodec.decodeBackup(raw, newSnapshotId)
fun encodeBackup(snapshot: SaveEnvelope, appVersion: String, exportedAtEpochMs: Long): String =
    BackupCodec.encodeBackup(snapshot, appVersion, exportedAtEpochMs)
