package com.github.nanaki_93.progress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupCodecTest {
    private val snapshot = SaveEnvelope(
        savedAtEpochMs = 123, snapshotId = "write_1", revision = 2,
        preferences = SavePreferences(SavedColorMode.DARK, false, true, true),
        lessonProgress = listOf(LessonProgress("unavailable_lesson", 3, 111, LessonStage.SUMMARY)),
        reviewItems = listOf(ReviewItemProgress("unavailable_item", "unavailable_doc", ReviewOutcome.GOOD, 120,
            lastActionToken = "token_1")),
    )
    private val bare get() = encodeSave(snapshot)
    private val wrapped get() = encodeBackup(snapshot, "1.0-SNAPSHOT", SaveBounds.MAX_EPOCH_MS)

    private fun rejects(raw: String, reason: BackupProblem) {
        var called = false
        assertEquals(BackupDecodeResult.Rejected(reason), decodeBackup(raw) { called = true; "new_id" })
        assertFalse(called, "invalid input cannot request a migration identity")
    }

    @Test fun wrapperAndBareSaveKeepMetadataAndUnknownValidIds() {
        assertEquals(BackupDecodeResult.Valid(ValidatedBackup(snapshot, 1, null, "1.0-SNAPSHOT", SaveBounds.MAX_EPOCH_MS)),
            decodeBackup(wrapped))
        assertEquals(BackupDecodeResult.Valid(ValidatedBackup(snapshot, 1, null, null, null)), decodeBackup(bare))
        assertTrue(wrapped.contains("\"save\":$bare"))
        assertEquals(snapshot, (decodeSave(bare) as SaveDecodeResult.Valid).snapshot)
        assertEquals(0L, (decodeBackup(encodeBackup(snapshot, "v+1", 0)) as BackupDecodeResult.Valid).backup.exportedAtEpochMs)
    }

    @Test fun schemaZeroMigratesInsideWrapperAndBareWithoutPersisting() {
        val precursor = """{"schemaVersion":0,"savedAtEpochMs":12,"preferences":{"colorMode":"dark"},"lessonProgress":[],"reviewItems":[]}"""
        val expected = SaveEnvelope(savedAtEpochMs = 12, snapshotId = "new_id", revision = 0,
            preferences = SavePreferences(SavedColorMode.DARK))
        val wrapper = wrapped.replace(bare, precursor)
        assertEquals(BackupDecodeResult.Valid(ValidatedBackup(expected, 0, 0, "1.0-SNAPSHOT", SaveBounds.MAX_EPOCH_MS)),
            decodeBackup(wrapper) { "new_id" })
        assertEquals(BackupDecodeResult.Valid(ValidatedBackup(expected, 0, 0, null, null)),
            decodeBackup(precursor) { "new_id" })
        assertTrue(wrapper.contains("\"schemaVersion\":0"), "migration never rewrites the input")
        rejects(wrapper.replace("\"colorMode\":\"dark\"", "\"colorMode\":\"invalid\""), BackupProblem.INVALID_SAVE)
        rejects(wrapper.replace(precursor, precursor.replace("\"schemaVersion\":0", "\"schemaVersion\":2")), BackupProblem.UNSUPPORTED_SAVE_VERSION)
    }

    @Test fun wrapperRequiresExactFieldsIdentityVersionAndWireTypes() {
        rejects(wrapped.replace("\"appId\":\"hiragame\"", "\"appId\":\"other\""), BackupProblem.WRONG_APP)
        listOf("2", "0", "1.0", "1e0", "\"1\"", "null", "true").forEach {
            rejects(wrapped.replace("\"backupFormatVersion\":1", "\"backupFormatVersion\":$it"), BackupProblem.UNSUPPORTED_FORMAT)
        }
        rejects(wrapped.replace("\"backupFormatVersion\":1,", ""), BackupProblem.INVALID_METADATA)
        rejects(wrapped.replace("\"appVersion\":\"1.0-SNAPSHOT\",", ""), BackupProblem.INVALID_METADATA)
        rejects(wrapped.replace("\"exportedAtEpochMs\":${SaveBounds.MAX_EPOCH_MS},", ""), BackupProblem.INVALID_METADATA)
        rejects(wrapped.replace("\"appId\":\"hiragame\"", "\"appId\":7"), BackupProblem.WRONG_APP)
        rejects(wrapped.replace("\"appVersion\":\"1.0-SNAPSHOT\"", "\"appVersion\":7"), BackupProblem.INVALID_METADATA)
        rejects(wrapped.replace("\"save\":$bare", "\"save\":null"), BackupProblem.INVALID_SAVE)
        rejects(wrapped.replace("\"save\":$bare", "\"save\":[]"), BackupProblem.INVALID_SAVE)
        rejects(wrapped.replace("\"save\":$bare", "\"save\":\"$bare\""), BackupProblem.MALFORMED_JSON)
        rejects(wrapped.replace("\"save\":$bare", "\"save\":{}"), BackupProblem.UNSUPPORTED_SAVE_VERSION)
        rejects(wrapped.replace("\"save\":$bare", "\"save\":$bare,\"__proto__\":{}"), BackupProblem.INVALID_METADATA)
        rejects(wrapped.replace("\"appVersion\":\"1.0-SNAPSHOT\"", "\"appVersion\":\"bad space\""), BackupProblem.INVALID_METADATA)
        rejects(wrapped.replace("\"appVersion\":\"1.0-SNAPSHOT\"", "\"appVersion\":\"é\""), BackupProblem.INVALID_METADATA)
        rejects(wrapped.replace("\"appVersion\":\"1.0-SNAPSHOT\"", "\"appVersion\":\"${"a".repeat(65)}\""), BackupProblem.INVALID_METADATA)
        listOf("-1", "1.0", "1e0", "\"1\"", "null", "253402300800000", "9007199254740992").forEach {
            rejects(wrapped.replace("\"exportedAtEpochMs\":${SaveBounds.MAX_EPOCH_MS}", "\"exportedAtEpochMs\":$it"), BackupProblem.INVALID_METADATA)
        }
    }

    @Test fun duplicateFieldsAndMalformedRootsNeverFallBackToBareSave() {
        rejects(wrapped.replace("\"appId\":\"hiragame\"", "\"appId\":\"hiragame\",\"app\\u0049d\":\"other\""), BackupProblem.DUPLICATE_KEY)
        rejects(wrapped.replace("\"snapshotId\":\"write_1\"", "\"snapshotId\":\"write_1\",\"snapshotId\":\"other\""), BackupProblem.DUPLICATE_KEY)
        rejects(wrapped.replace("\"save\":$bare", "\"save\":${bare.replace("\"snapshotId\":\"write_1\"", "\"snapshotId\":\"../escape\"")}"), BackupProblem.INVALID_SAVE)
        rejects(wrapped.replace("\"save\":$bare", "\"save\":${bare.replace("\"revision\":2", "\"revision\":\"2\"")}"), BackupProblem.INVALID_SAVE)
        rejects(wrapped.replace("\"save\":$bare", "\"save\":${bare.replace("\"revision\":2", "\"revision\":2e0")}"), BackupProblem.INVALID_SAVE)
        rejects(wrapped.dropLast(1), BackupProblem.MALFORMED_JSON)
        rejects("null", BackupProblem.INVALID_ROOT)
        rejects("[]", BackupProblem.INVALID_ROOT)
        rejects("{}", BackupProblem.UNSUPPORTED_SAVE_VERSION)
        rejects(bare.replace("\"schemaVersion\":1", "\"schemaVersion\":2"), BackupProblem.UNSUPPORTED_SAVE_VERSION)
        rejects("{" + "\"appId\":\"hiragame\",\"schemaVersion\":1}" , BackupProblem.INVALID_METADATA)
    }

    @Test fun byteBudgetsAndDepthAreIndependentEvenWithWhitespaceOrMultibyteText() {
        rejects("{" + "x".repeat(BackupBounds.MAX_JSON_BYTES), BackupProblem.OVERSIZED)
        rejects("\"" + "界".repeat(BackupBounds.MAX_JSON_BYTES / 2) + "\"", BackupProblem.OVERSIZED)
        rejects("[".repeat(35), BackupProblem.TOO_DEEP)
        rejects(wrapped.replace("\"save\":$bare", "\"save\":${"[".repeat(34)}0${"]".repeat(34)}"), BackupProblem.TOO_DEEP)
        rejects(wrapped.replace("\"save\":$bare", "\"save\":{\"schemaVersion\":1,\"extra\":${"[".repeat(32)}0${"]".repeat(32)}}"), BackupProblem.TOO_DEEP)
        // Outer file fits, but the original nested save exceeds its own budget through whitespace.
        val paddedSave = bare.dropLast(1) + " ".repeat(SaveBounds.MAX_JSON_BYTES - bare.encodeToByteArray().size + 1) + "}"
        val paddedWrapper = wrapped.replace(bare, paddedSave)
        assertTrue(paddedWrapper.encodeToByteArray().size <= BackupBounds.MAX_JSON_BYTES)
        rejects(paddedWrapper, BackupProblem.OVERSIZED)
        val exactSave = bare.dropLast(1) + " ".repeat(SaveBounds.MAX_JSON_BYTES - bare.encodeToByteArray().size) + "}"
        assertEquals(SaveBounds.MAX_JSON_BYTES, exactSave.encodeToByteArray().size)
        assertTrue(decodeBackup(wrapped.replace(bare, exactSave)) is BackupDecodeResult.Valid)
        val exactWrapper = wrapped + " ".repeat(BackupBounds.MAX_JSON_BYTES - wrapped.encodeToByteArray().size)
        assertEquals(BackupBounds.MAX_JSON_BYTES, exactWrapper.encodeToByteArray().size)
        assertTrue(decodeBackup(exactWrapper) is BackupDecodeResult.Valid)
        rejects(exactWrapper + " ", BackupProblem.OVERSIZED)
        // The wrapper limit counts UTF-8 bytes, not UTF-16 characters.
        rejects(wrapped.replace("\"appVersion\":\"1.0-SNAPSHOT\"", "\"appVersion\":\"${"界".repeat(BackupBounds.MAX_JSON_BYTES / 2)}\""), BackupProblem.OVERSIZED)
    }

    @Test fun encodingRejectsInvalidSnapshotsMetadataAndSize() {
        assertEquals(BackupProblem.INVALID_SAVE, assertFailsWith<BackupEncodeException> {
            encodeBackup(snapshot.copy(snapshotId = "bad id"), "v1", 1)
        }.reason)
        listOf("", "has spaces", "界", "x".repeat(65)).forEach {
            assertEquals(BackupProblem.INVALID_METADATA, assertFailsWith<BackupEncodeException> {
                encodeBackup(snapshot, it, 1)
            }.reason)
        }
        listOf(-1L, SaveBounds.MAX_EPOCH_MS + 1).forEach {
            assertEquals(BackupProblem.INVALID_METADATA, assertFailsWith<BackupEncodeException> {
                encodeBackup(snapshot, "v1", it)
            }.reason)
        }
    }
}
