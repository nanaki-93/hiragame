package com.github.nanaki_93.progress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SaveCodecTest {
    private fun snapshot() = SaveEnvelope(
        savedAtEpochMs = SaveBounds.MAX_EPOCH_MS,
        snapshotId = "write_1",
        revision = SaveBounds.MAX_SAFE_INTEGER,
        preferences = SavePreferences(SavedColorMode.DARK, false, false, true),
        lessonProgress = listOf(LessonProgress("unknown_lesson", 2, 10, LessonStage.ROLE_PLAY, "checkpoint_1", 11)),
        practiceProgress = listOf(PracticeCheckpoint(
            "unknown_set", 2, 12, "run_1", "transition_1", listOf("ex_1", "ex_2"),
            listOf(CompactOutcome.CORRECT, CompactOutcome.SELF_NEEDS_PRACTICE),
            2, CheckpointView.COMPLETE, 13,
            listOf(CheckpointExerciseType.CHOICE, CheckpointExerciseType.PRODUCTION),
        )),
        reviewItems = listOf(ReviewItemProgress(
            "unknown_review", "unknown_document", ReviewOutcome.HARD, 14,
            dueAtEpochMs = 15, intervalMs = 16, step = 1, repetitions = 2, lapses = 3,
            lastActionToken = "review_1",
        )),
    )

    private fun protected(raw: String, reason: SaveProblem) {
        assertEquals(SaveDecodeResult.Protected(reason), decodeSave(raw))
    }

    @Test fun supportedSnapshotRoundTripsWithoutAuthoredOrLearnerText() {
        val original = snapshot()
        val raw = encodeSave(original)
        assertEquals(SaveDecodeResult.Valid(original), decodeSave(raw))
        listOf("answer", "response", "feedback", "transcript", "history").forEach {
            assertFalse(raw.contains("\"$it\""))
        }
        assertTrue(raw.contains("\"schemaVersion\":1"))
        val empty = SaveEnvelope(savedAtEpochMs = 0, snapshotId = "empty", revision = 0)
        assertEquals(SaveDecodeResult.Valid(empty), decodeSave(encodeSave(empty)))
    }

    @Test fun rejectsMalformedAndDuplicateKeysIncludingEscapedAndNestedNames() {
        protected("", SaveProblem.MALFORMED_JSON)
        protected("{", SaveProblem.MALFORMED_JSON)
        protected("{\"schemaVersion\":1,}", SaveProblem.MALFORMED_JSON)
        protected("{\"schemaVersion\":1,\"schemaVersion\":1}", SaveProblem.DUPLICATE_KEY)
        protected("{\"schemaVersion\":1,\"schema\\u0056ersion\":1}", SaveProblem.DUPLICATE_KEY)
        val raw = encodeSave(snapshot())
        protected(raw.replace("\"colorMode\":\"dark\"", "\"colorMode\":\"dark\",\"colorMode\":\"light\""), SaveProblem.DUPLICATE_KEY)
        protected(raw.replace("\"exerciseIds\":[\"ex_1\",\"ex_2\"]", "\"exerciseIds\":[{\"a\":1,\"a\":2}]"), SaveProblem.DUPLICATE_KEY)
    }

    @Test fun lexicalPreflightKeepsEscapedKeysAndMalformedStringsDistinct() {
        val duplicate = "{\"outer\":{\"name\":1,\"na\\u006de\":2}}"
        assertEquals(SaveProblem.DUPLICATE_KEY, inspectJsonLexically(duplicate, 32))
        assertEquals(SaveProblem.DUPLICATE_KEY, inspectJsonLexically(duplicate, 34))
        assertEquals(SaveProblem.MALFORMED_JSON, inspectJsonLexically("{\"bad\\uZZZZ\":1}", 32))
        assertEquals(SaveProblem.MALFORMED_JSON, inspectJsonLexically("{\"key\":\"unterminated", 32))
        protected("{\"bad\\uZZZZ\":1}", SaveProblem.MALFORMED_JSON)
        protected("{\"key\":\"unterminated", SaveProblem.MALFORMED_JSON)
    }

    @Test fun lexicalDepthIsIndependentlyBoundedAndIgnoresBracketsInStrings() {
        val nested = "[".repeat(33) + "\"[{}]\"" + "]".repeat(33)
        assertEquals(SaveProblem.TOO_DEEP, inspectJsonLexically(nested, 32))
        assertEquals(null, inspectJsonLexically(nested, 33))
        assertEquals(null, inspectJsonLexically(nested, 34))
        assertEquals(null, inspectJsonLexically("[".repeat(32) + "0" + "]".repeat(32), 32))
        protected(nested, SaveProblem.TOO_DEEP)
    }

    @Test fun rejectsUnknownFieldsWrongTypesInvalidEnumsAndImpossibleShapes() {
        val raw = encodeSave(snapshot())
        protected(raw.replace("\"snapshotId\":\"write_1\"", "\"snapshotId\":\"write_1\",\"secret\":true"), SaveProblem.INVALID_SNAPSHOT)
        protected(raw.replace("\"stage\":\"rolePlay\"", "\"stage\":\"rolePlay\",\"secret\":true"), SaveProblem.INVALID_SNAPSHOT)
        protected(raw.replace("\"reviewItems\":[", "\"reviewItems\":[{\"extra\":1},"), SaveProblem.INVALID_SNAPSHOT)
        protected(raw.replace("\"revision\":9007199254740991", "\"revision\":\"1\""), SaveProblem.INVALID_SNAPSHOT)
        protected(raw.replace("\"revision\":9007199254740991", "\"revision\":1.5"), SaveProblem.INVALID_SNAPSHOT)
        protected(raw.replace("\"revision\":9007199254740991", "\"revision\":1e0"), SaveProblem.INVALID_SNAPSHOT)
        protected(raw.replace("\"revision\":9007199254740991", "\"revision\":9007199254740992"), SaveProblem.INVALID_SNAPSHOT)
        protected(raw.replace("\"colorMode\":\"dark\"", "\"colorMode\":\"sepia\""), SaveProblem.INVALID_SNAPSHOT)
        protected(raw.replace("\"view\":\"complete\"", "\"view\":\"prompt\""), SaveProblem.INVALID_SNAPSHOT)
        protected(raw.replace("\"lessonId\":\"unknown_lesson\"", "\"lessonId\":\"../escape\""), SaveProblem.INVALID_SNAPSHOT)
        protected(raw.replace("\"savedAtEpochMs\":253402300799999", "\"savedAtEpochMs\":-1"), SaveProblem.INVALID_SNAPSHOT)
        protected(raw.replace("\"revision\":9007199254740991", "\"revision\":NaN"), SaveProblem.MALFORMED_JSON)
        protected(raw.replace("\"revision\":9007199254740991", "\"revision\":Infinity"), SaveProblem.MALFORMED_JSON)
        protected("null", SaveProblem.INVALID_SNAPSHOT)
        protected("[]", SaveProblem.INVALID_SNAPSHOT)
    }

    @Test fun unsupportedAndMissingVersionsStayProtected() {
        val raw = encodeSave(snapshot())
        listOf("2", "-1", "1.0", "\"1\"", "null").forEach {
            protected(raw.replace("\"schemaVersion\":1", "\"schemaVersion\":$it"), SaveProblem.UNSUPPORTED_VERSION)
        }
        // A version-1 envelope mislabeled as the precursor contains forbidden fields.
        protected(raw.replace("\"schemaVersion\":1", "\"schemaVersion\":0"), SaveProblem.INVALID_SNAPSHOT)
        protected(raw.replace("\"schemaVersion\":1,", ""), SaveProblem.UNSUPPORTED_VERSION)
    }

    @Test fun byteLimitAndDepthAreCheckedBeforeRecursiveParsing() {
        // A huge malformed original is still protected as oversized, never truncated to parse.
        protected("{" + "x".repeat(SaveBounds.MAX_JSON_BYTES), SaveProblem.OVERSIZED)
        // Fewer UTF-16 characters than the limit, but more UTF-8 bytes.
        protected("\"" + "界".repeat(SaveBounds.MAX_JSON_BYTES / 2) + "\"", SaveProblem.OVERSIZED)
        protected("[".repeat(33), SaveProblem.TOO_DEEP)
        protected("[".repeat(32) + "0" + "]".repeat(32), SaveProblem.INVALID_SNAPSHOT)
        protected("{\"schemaVersion\":1,\"extra\":" + "[".repeat(32), SaveProblem.TOO_DEEP)
    }

    @Test fun encoderChecksValidationAndSerializedByteBudget() {
        val invalid = snapshot().copy(practiceProgress = listOf(snapshot().practiceProgress.single().copy(frontier = -1)))
        assertEquals(SaveProblem.INVALID_SNAPSHOT, assertFailsWith<SaveEncodeException> { encodeSave(invalid) }.reason)
        assertEquals(SaveProblem.INVALID_SNAPSHOT, assertFailsWith<SaveEncodeException> { encodeSave(snapshot().copy(schemaVersion = 2)) }.reason)
        val longId = "x".repeat(128)
        val large = snapshot().copy(reviewItems = List(5000) { index ->
            snapshot().reviewItems.single().copy(itemId = "r$index", documentId = longId, lastActionToken = longId)
        })
        assertEquals(SaveProblem.OVERSIZED, assertFailsWith<SaveEncodeException> { encodeSave(large) }.reason)
    }
}
