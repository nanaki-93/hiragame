package com.github.nanaki_93.storage

import com.github.nanaki_93.progress.BackupCodec
import com.github.nanaki_93.progress.BackupDecodeResult
import com.github.nanaki_93.progress.SaveBounds
import com.github.nanaki_93.progress.SavedColorMode
import com.github.nanaki_93.progress.changePreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgressDownloadsTest {
    private data class Capture(val filename: String, val type: String, val text: String)
    private class RecordingSink : ProgressDownloadSink {
        val calls = mutableListOf<Capture>()
        var fail = false
        override fun download(filename: String, contentType: String, text: String) {
            calls += Capture(filename, contentType, text)
            if (fail) throw IllegalStateException("download denied")
        }
    }

    private fun owner(store: ProgressStore) = LocalProgressOwner(store, { 1000L }, { "snapshot_1" })
    private fun downloads(owner: LocalProgressOwner, sink: ProgressDownloadSink) =
        ProgressDownloads(owner, sink, { 1_704_067_200_000L }) // 2024-01-01 UTC

    private fun dark(owner: LocalProgressOwner) = owner.mutate { snapshot ->
        changePreferences(snapshot, snapshot.preferences.copy(colorMode = SavedColorMode.DARK))
    }

    @Test fun exportsValidatedMemoryIncludingFailedWritesAndConflictsWithoutWriting() {
        val backing = MemoryProgressBacking()
        val store = MemoryProgressStore(backing)
        val subject = owner(store)
        val sink = RecordingSink()
        val exporter = downloads(subject, sink)
        val original = subject.state.value
        val generation = subject.generation
        assertIs<DownloadResult.Downloaded>(exporter.exportCurrent()).also {
            assertEquals("hiragame-backup-2024-01-01.json", it.filename)
            assertTrue(it.label.contains("not confirmed saved"))
        }
        assertNull(backing.raw)
        assertEquals(original, subject.state.value)
        assertEquals(generation, subject.generation)
        store.writeFailure = StoreFailure.QUOTA
        dark(subject)
        assertEquals(PersistenceStatus.MemoryOnly(StoreFailure.QUOTA), subject.state.value.status)
        val memoryOnly = subject.state.value
        assertIs<DownloadResult.Downloaded>(exporter.exportCurrent()).also {
            assertTrue(it.label.contains("not confirmed saved"))
        }
        assertBackupMatches(subject, sink.calls.last(), 1_704_067_200_000L)
        assertEquals(SavedColorMode.DARK, subject.state.value.snapshot.preferences.colorMode)
        assertEquals(memoryOnly, subject.state.value)
        assertNull(backing.raw)
        store.writeFailure = null
        backing.externalChange("external unknown save")
        assertEquals(PersistenceStatus.Conflict, subject.state.value.status)
        val conflicted = subject.state.value
        assertIs<DownloadResult.Downloaded>(exporter.exportCurrent()).also {
            assertTrue(it.label.contains("not confirmed saved"))
        }
        assertBackupMatches(subject, sink.calls.last(), 1_704_067_200_000L)
        assertEquals("external unknown save", backing.raw)
        assertEquals(conflicted, subject.state.value)
        assertTrue(sink.calls.all { it.filename == "hiragame-backup-2024-01-01.json" && it.type == "application/json;charset=utf-8" })
        assertEquals(3, sink.calls.size)
        assertEquals(PersistenceStatus.Conflict, subject.state.value.status)
        assertEquals(generation, subject.generation)
    }

    @Test fun savedExportIsMarkedValidatedAndRawRecoveryIsSeparateExactUnvalidatedText() {
        val backing = MemoryProgressBacking()
        val subject = owner(MemoryProgressStore(backing))
        dark(subject)
        val savedRaw = backing.raw
        val savedState = subject.state.value
        val sink = RecordingSink()
        assertEquals("Validated progress backup download requested", assertIs<DownloadResult.Downloaded>(downloads(subject, sink).exportCurrent()).label)
        assertEquals(savedState, subject.state.value)
        assertEquals(savedRaw, backing.raw)
        assertTrue(savedRaw != sink.calls.single().text) // export metadata belongs to the wrapper
        assertBackupMatches(subject, sink.calls.single(), 1_704_067_200_000L)
        assertEquals(DownloadResult.OriginalUnavailable, downloads(subject, sink).downloadProtectedOriginal())

        for (raw in listOf(" \n{\"schemaVersion\":99,\"note\":\"日本語\"} \n", "{not valid\u0000")) {
            val protectedBacking = MemoryProgressBacking(raw)
            val protectedOwner = owner(MemoryProgressStore(protectedBacking))
            assertIs<PersistenceStatus.Protected>(protectedOwner.state.value.status)
            dark(protectedOwner) // current work can still be exported; original must remain untouched
            val protectedSink = RecordingSink()
            val protectedDownloads = downloads(protectedOwner, protectedSink)
            val protectedState = protectedOwner.state.value
            assertIs<DownloadResult.Downloaded>(protectedDownloads.downloadProtectedOriginal()).also {
                assertEquals("hiragame-unvalidated-recovery-2024-01-01.txt", it.filename)
                assertTrue(it.label.contains("Unvalidated"))
                assertTrue(it.label.contains("not a validated/importable backup"))
            }
            assertEquals(Capture("hiragame-unvalidated-recovery-2024-01-01.txt", "text/plain;charset=utf-8", raw), protectedSink.calls.single())
            assertIs<DownloadResult.Downloaded>(protectedDownloads.exportCurrent())
            assertFalse(protectedSink.calls.last().text == raw)
            assertBackupMatches(protectedOwner, protectedSink.calls.last(), 1_704_067_200_000L)
            assertEquals(protectedState, protectedOwner.state.value)
            assertEquals(raw, protectedBacking.raw)
        }
    }

    @Test fun deniedStartupCannotOfferUnobservedOriginalAndDownloadsNeverRereadStorage() {
        val backing = MemoryProgressBacking("unknown original")
        val store = MemoryProgressStore(backing).apply { readFailure = StoreFailure.DENIED }
        val subject = owner(store)
        val sink = RecordingSink()
        val exporter = downloads(subject, sink)
        assertEquals(DownloadResult.OriginalUnavailable, exporter.downloadProtectedOriginal())
        dark(subject)
        assertIs<DownloadResult.Downloaded>(exporter.exportCurrent()).also {
            assertTrue(it.label.contains("not confirmed saved"))
        }
        assertEquals(1, sink.calls.size)
        assertBackupMatches(subject, sink.calls.single(), 1_704_067_200_000L)
        assertEquals("unknown original", backing.raw)
    }

    @Test fun exportUsesOneUtcInstantForFilenameAndWrapper() {
        val subject = owner(MemoryProgressStore(MemoryProgressBacking()))
        val sink = RecordingSink()
        var reads = 0
        val result = ProgressDownloads(subject, sink, {
            reads++
            if (reads == 1) 1_704_153_599_999L else 1_704_153_600_000L
        }).exportCurrent()
        assertEquals(1, reads)
        assertEquals("hiragame-backup-2024-01-01.json", assertIs<DownloadResult.Downloaded>(result).filename)
        assertBackupMatches(subject, sink.calls.single(), 1_704_153_599_999L)
    }

    @Test fun invalidExportTimeDoesNotDownloadOrAlterLearnerState() {
        val backing = MemoryProgressBacking()
        val subject = owner(MemoryProgressStore(backing))
        val before = subject.state.value
        val sink = RecordingSink()
        assertIs<DownloadResult.InvalidSnapshot>(ProgressDownloads(subject, sink, { SaveBounds.MAX_EPOCH_MS + 1 }).exportCurrent())
        assertTrue(sink.calls.isEmpty())
        assertEquals(before, subject.state.value)
        assertNull(backing.raw)
    }

    @Test fun failingSinkCannotChangeMemoryStatusOrStorage() {
        for (raw in listOf<String?>(null, "{invalid")) {
            val backing = MemoryProgressBacking(raw)
            val subject = owner(MemoryProgressStore(backing))
            dark(subject)
            val before = subject.state.value
            val baseline = subject.savedBaseline
            val generation = subject.generation
            val sink = RecordingSink().apply { fail = true }
            val exporter = downloads(subject, sink)
            assertEquals(DownloadResult.Failed, exporter.exportCurrent())
            if (raw == null) assertEquals(DownloadResult.OriginalUnavailable, exporter.downloadProtectedOriginal())
            else assertEquals(DownloadResult.Failed, exporter.downloadProtectedOriginal())
            assertEquals(before, subject.state.value)
            assertEquals(baseline, subject.savedBaseline)
            assertEquals(generation, subject.generation)
            assertEquals(backing.raw, if (raw == null) baseline else raw)
        }
    }

    @Test fun browserSinkReleasesObjectUrlEvenWhenTriggerFails() {
        val platform = FakeDownloadPlatform()
        val sink = BrowserDownloadSink(platform)
        sink.download("file.json", "application/json", "{}")
        assertEquals(listOf("create", "trigger", "release"), platform.calls)
        assertEquals("{}", platform.text)
        assertEquals("application/json", platform.type)
        assertEquals("file.json", platform.filename)
        platform.calls.clear()
        platform.triggerFails = true
        try {
            sink.download("raw.txt", "text/plain", "original")
            kotlin.test.fail("expected trigger failure")
        } catch (_: IllegalStateException) { }
        assertEquals(listOf("create", "trigger", "release"), platform.calls)
        platform.calls.clear()
        platform.createFails = true
        try {
            sink.download("raw.txt", "text/plain", "original")
            kotlin.test.fail("expected create failure")
        } catch (_: IllegalStateException) { }
        assertEquals(listOf("create"), platform.calls) // no URL exists to revoke
        // The default adapter is safe to construct without a DOM in Node.
        BrowserDownloadSink()
    }

    private fun assertBackupMatches(owner: LocalProgressOwner, capture: Capture, exportedAt: Long) {
        val decoded = assertIs<BackupDecodeResult.Valid>(BackupCodec.decodeBackup(capture.text)).backup
        assertEquals(owner.state.value.snapshot, decoded.snapshot)
        assertEquals("1.0-SNAPSHOT", decoded.appVersion) // site/build.gradle.kts version; no UI version literal
        assertEquals(exportedAt, decoded.exportedAtEpochMs)
        assertEquals(1, decoded.sourceSchemaVersion)
        assertNull(decoded.migratedFrom)
        assertEquals("application/json;charset=utf-8", capture.type)
        assertEquals("hiragame-backup-2024-01-01.json", capture.filename)
        assertTrue(decoded.snapshot.savedAtEpochMs != exportedAt) // export time is not write time
    }

    private class FakeDownloadPlatform : BrowserDownloadPlatform {
        val calls = mutableListOf<String>()
        var text = ""
        var type = ""
        var filename = ""
        var triggerFails = false
        var createFails = false
        override fun createUrl(text: String, contentType: String): String {
            calls += "create"
            if (createFails) throw IllegalStateException("create failed")
            this.text = text
            type = contentType
            return "blob:fake"
        }
        override fun trigger(url: String, filename: String) {
            calls += "trigger"
            assertEquals("blob:fake", url)
            this.filename = filename
            if (triggerFails) throw IllegalStateException("click failed")
        }
        override fun release(url: String) {
            calls += "release"
            assertEquals("blob:fake", url)
        }
    }
}
