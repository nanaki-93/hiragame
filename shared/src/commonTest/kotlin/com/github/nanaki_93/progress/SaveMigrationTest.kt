package com.github.nanaki_93.progress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SaveMigrationTest {
    private val precursor = """{
        "schemaVersion":0,
        "savedAtEpochMs":1234,
        "preferences":{"colorMode":"dark"},
        "lessonProgress":[{
            "lessonId":"unknown_lesson","contentVersion":2,"updatedAtEpochMs":1100,
            "stage":"guidedPractice","checkpointId":"checkpoint_2","completedAtEpochMs":1200
        }],
        "reviewItems":[{
            "itemId":"unknown_item","documentId":"unknown_document","outcome":"hard",
            "lastReviewedAtEpochMs":1000,"dueAtEpochMs":2000,"intervalMs":3000,
            "step":1,"repetitions":2,"lapses":3,"lastActionToken":"review_7"
        }]
    }""".trimIndent()

    private fun protected(raw: String, reason: SaveProblem) {
        var called = false
        assertEquals(SaveDecodeResult.Protected(reason), decodeSave(raw) {
            called = true
            "new_id"
        })
        assertFalse(called, "invalid precursors must not consume an identity")
    }

    @Test fun precursorMigratesInMemoryWithOnlyDocumentedDefaults() {
        val expected = SaveEnvelope(
            savedAtEpochMs = 1234,
            snapshotId = "generated_id",
            revision = 0,
            preferences = SavePreferences(SavedColorMode.DARK, true, true, false),
            lessonProgress = listOf(LessonProgress(
                "unknown_lesson", 2, 1100, LessonStage.GUIDED_PRACTICE, "checkpoint_2", 1200,
            )),
            reviewItems = listOf(ReviewItemProgress(
                "unknown_item", "unknown_document", ReviewOutcome.HARD, 1000,
                dueAtEpochMs = 2000, intervalMs = 3000, step = 1, repetitions = 2,
                lapses = 3, lastActionToken = "review_7",
            )),
        )
        var calls = 0
        val decoded = decodeSave(precursor) { calls++; "generated_id" }
        assertEquals(1, calls)
        assertEquals(SaveDecodeResult.Valid(expected, migratedFrom = 0), decoded)
        assertEquals(emptyList(), expected.practiceProgress)
        // Decoding has no store reference or write side effect; the original text remains intact.
        assertTrue(precursor.contains("\"schemaVersion\":0"))
        assertFalse(precursor.contains("snapshotId"))
        assertEquals(SaveDecodeResult.Valid(expected), decodeSave(encodeSave(expected)))
        assertEquals(SaveDecodeResult.Valid(expected.copy(snapshotId = "another_id"), migratedFrom = 0),
            decodeSave(precursor) { "another_id" })
    }

    @Test fun explicitPreferenceValuesAndNullableRecordFieldsSurviveMigration() {
        val raw = precursor.replace("\"colorMode\":\"dark\"",
            "\"colorMode\":\"system\",\"showReadings\":false,\"showTranslation\":false,\"showRomaji\":true")
            .replace(",\"checkpointId\":\"checkpoint_2\",\"completedAtEpochMs\":1200", "")
            .replace(",\"dueAtEpochMs\":2000,\"intervalMs\":3000,\n            \"step\":1,\"repetitions\":2,\"lapses\":3", "")
        val migrated = (decodeSave(raw) { "id_1" } as SaveDecodeResult.Valid).snapshot
        assertEquals(SavePreferences(SavedColorMode.SYSTEM, false, false, true), migrated.preferences)
        assertEquals(null, migrated.lessonProgress.single().checkpointId)
        assertEquals(null, migrated.lessonProgress.single().completedAtEpochMs)
        assertEquals(null, migrated.reviewItems.single().dueAtEpochMs)
        assertEquals(null, migrated.reviewItems.single().intervalMs)
        assertEquals(null, migrated.reviewItems.single().step)
        assertEquals(null, migrated.reviewItems.single().repetitions)
        assertEquals(null, migrated.reviewItems.single().lapses)
    }

    @Test fun missingMalformedNegativeAndFutureVersionsAreProtected() {
        protected(precursor.replace("\"schemaVersion\":0,", ""), SaveProblem.UNSUPPORTED_VERSION)
        listOf("-1", "2", "99", "0.0", "0e0", "\"0\"", "null", "true")
            .forEach { protected(precursor.replace("\"schemaVersion\":0", "\"schemaVersion\":$it"), SaveProblem.UNSUPPORTED_VERSION) }
        protected(precursor.replace("\"schemaVersion\":0", "\"schemaVersion\":0,\"schemaVersion\":0"), SaveProblem.DUPLICATE_KEY)
        protected(precursor.dropLast(1), SaveProblem.MALFORMED_JSON)
    }

    @Test fun rejectsNonPrecursorFieldsMissingRequiredValuesAndBadRecords() {
        protected(precursor.replace("\"schemaVersion\":0", "\"schemaVersion\":0,\"revision\":0"), SaveProblem.INVALID_SNAPSHOT)
        protected(precursor.replace("\"schemaVersion\":0", "\"schemaVersion\":0,\"practiceProgress\":[]"), SaveProblem.INVALID_SNAPSHOT)
        protected(precursor.replace("\"colorMode\":\"dark\"", "\"colorMode\":\"dark\",\"future\":true"), SaveProblem.INVALID_SNAPSHOT)
        protected(precursor.replace("\"colorMode\":\"dark\"", "\"showReadings\":true"), SaveProblem.INVALID_SNAPSHOT)
        protected(precursor.replace("\"colorMode\":\"dark\"", "\"colorMode\":\"light\",\"showReadings\":\"false\""), SaveProblem.INVALID_SNAPSHOT)
        protected(precursor.replace("\"preferences\":{\"colorMode\":\"dark\"},", ""), SaveProblem.INVALID_SNAPSHOT)
        protected(precursor.replace("\"lessonProgress\":[", "\"otherProgress\":["), SaveProblem.INVALID_SNAPSHOT)
        protected(precursor.replace("\"reviewItems\":[", "\"otherItems\":["), SaveProblem.INVALID_SNAPSHOT)
        protected(precursor.replace("\"savedAtEpochMs\":1234", "\"savedAtEpochMs\":\"1234\""), SaveProblem.INVALID_SNAPSHOT)
        protected(precursor.replace("\"savedAtEpochMs\":1234", "\"savedAtEpochMs\":1234.5"), SaveProblem.INVALID_SNAPSHOT)
        protected(precursor.replace("\"savedAtEpochMs\":1234", "\"savedAtEpochMs\":-1"), SaveProblem.INVALID_SNAPSHOT)
        protected(precursor.replace("\"updatedAtEpochMs\":1100", "\"updatedAtEpochMs\":-1"), SaveProblem.INVALID_SNAPSHOT)
        protected(precursor.replace("\"lessonId\":\"unknown_lesson\"", "\"lessonId\":\"../bad\""), SaveProblem.INVALID_SNAPSHOT)
        protected(precursor.replace("\"outcome\":\"hard\"", "\"outcome\":\"future\""), SaveProblem.INVALID_SNAPSHOT)
        protected(precursor.replace("\"lastActionToken\":\"review_7\"", "\"lastActionToken\":\"bad token\""), SaveProblem.INVALID_SNAPSHOT)
    }

    @Test fun throwingIdentitySupplierProtectsOriginalWithoutLeakingFailure() {
        val original = precursor
        var calls = 0
        val result = decodeSave(original) {
            calls++
            error("private generator failure: $original")
        }
        assertEquals(1, calls)
        assertEquals(SaveDecodeResult.Protected(SaveProblem.INVALID_SNAPSHOT), result)
        assertEquals(precursor, original, "failed migration must leave the original text intact")
        val recovered = decodeSave(original) { "recovered_id" } as SaveDecodeResult.Valid
        assertEquals(0, recovered.migratedFrom)
        assertEquals("recovered_id", recovered.snapshot.snapshotId)
        assertEquals(SaveDecodeResult.Valid(recovered.snapshot), decodeSave(encodeSave(recovered.snapshot)))
    }

    @Test fun invalidInjectedIdentityCannotExposeMigratedSnapshot() {
        assertEquals(SaveDecodeResult.Protected(SaveProblem.INVALID_SNAPSHOT), decodeSave(precursor) { "bad id" })
        val generated = decodeSave(precursor) as SaveDecodeResult.Valid
        assertEquals(0, generated.migratedFrom)
        assertTrue(generated.snapshot.snapshotId.startsWith("migration_"))
        assertEquals(SaveDecodeResult.Valid(generated.snapshot), decodeSave(encodeSave(generated.snapshot)))
    }
}
