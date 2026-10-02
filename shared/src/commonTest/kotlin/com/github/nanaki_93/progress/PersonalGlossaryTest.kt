package com.github.nanaki_93.progress

import kotlin.test.*

class PersonalGlossaryTest {
    private val initial = SaveEnvelope(savedAtEpochMs = 1, snapshotId = "personal", revision = 0)
    private val entry = GlossaryEntry("term_1", "要件", "ようけん", "requirement; clarify who needs the feature")
    private fun applied(value: ProgressUpdate) = assertIs<ProgressUpdate.Applied>(value).snapshot

    @Test fun addEditDeleteRejectStaleEditsWithoutChangingProgress() {
        val added = applied(saveGlossaryEntry(initial, entry))
        assertEquals(initial.lessonProgress, added.lessonProgress)
        assertEquals(initial.reviewItems, added.reviewItems)
        val edited = entry.copy(meaning = "requirement")
        val changed = applied(saveGlossaryEntry(added, edited, entry))
        assertIs<ProgressUpdate.Rejected>(saveGlossaryEntry(changed, entry, entry))
        assertIs<ProgressUpdate.Rejected>(removeGlossaryEntry(changed, entry))
        assertIs<ProgressUpdate.Unchanged>(saveGlossaryEntry(changed, edited, edited))
        assertEquals(initial, applied(removeGlossaryEntry(changed, edited)))
    }

    @Test fun oldSavesLoadAndGlossarySurvivesSaveAndBackupRoundTrips() {
        val old = """{"schemaVersion":1,"savedAtEpochMs":1,"snapshotId":"personal","revision":0}"""
        assertEquals(initial, assertIs<SaveDecodeResult.Valid>(decodeSave(old)).snapshot)
        val saved = applied(saveGlossaryEntry(initial, entry.copy(meaning = "<script> is literal personal text")))
        assertEquals(saved, assertIs<SaveDecodeResult.Valid>(decodeSave(encodeSave(saved))).snapshot)
        assertEquals(saved, assertIs<BackupDecodeResult.Valid>(decodeBackup(encodeBackup(saved, "1.0", 2))).backup.snapshot)
    }

    @Test fun malformedAndOverLimitGlossariesAreProtected() {
        val saved = initial.copy(personalGlossary = listOf(entry))
        val raw = encodeSave(saved)
        assertIs<SaveDecodeResult.Protected>(decodeSave(raw.replace("\"term\":\"要件\"", "\"term\":12")))
        for (invalid in listOf(entry.copy(id = "../bad"), entry.copy(term = " "), entry.copy(term = "x".repeat(257)),
            entry.copy(reading = "x".repeat(257)), entry.copy(meaning = "x".repeat(501)), entry.copy(meaning = "bad\u0000"))) {
            assertIs<ProgressUpdate.Rejected>(saveGlossaryEntry(initial, invalid))
        }
        assertFailsWith<SaveEncodeException> { encodeSave(saved.copy(personalGlossary = listOf(entry, entry))) }
        val full = initial.copy(personalGlossary = (1..100).map { entry.copy(id = "entry_$it") })
        assertIs<ProgressUpdate.Rejected>(saveGlossaryEntry(full, entry))
        assertIs<ProgressUpdate.Applied>(saveGlossaryEntry(full, full.personalGlossary[0].copy(meaning = "changed"), full.personalGlossary[0]))
    }

    @Test fun progressResetKeepsPersonalWorkAndFullResetClearsIt() {
        val saved = applied(saveGlossaryEntry(initial, entry))
        assertEquals(listOf(entry), resetLearnerState(saved, ResetScope.PROGRESS_ONLY).personalGlossary)
        assertTrue(resetLearnerState(saved, ResetScope.FULL_LEARNER_STATE).personalGlossary.isEmpty())
    }
}
