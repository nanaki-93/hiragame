package com.github.nanaki_93.storage

import com.github.nanaki_93.progress.SaveCodec
import com.github.nanaki_93.progress.SaveDecodeResult
import com.github.nanaki_93.progress.SaveEnvelope
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
        assertIs<DownloadResult.Downloaded>(exporter.exportCurrent()).also {
            assertEquals("hiragame-state-2024-01-01.json", it.filename)
            assertTrue(it.label.contains("not confirmed saved"))
        }
        assertNull(backing.raw)
        store.writeFailure = StoreFailure.QUOTA
        dark(subject)
        assertEquals(PersistenceStatus.MemoryOnly(StoreFailure.QUOTA), subject.state.value.status)
        assertIs<DownloadResult.Downloaded>(exporter.exportCurrent()).also {
            assertTrue(it.label.contains("not confirmed saved"))
        }
        assertEquals(subject.state.value.snapshot,
            (SaveCodec.decodeSave(sink.calls.last().text) as SaveDecodeResult.Valid).snapshot)
        assertEquals(SavedColorMode.DARK, subject.state.value.snapshot.preferences.colorMode)
        assertNull(backing.raw)
        store.writeFailure = null
        backing.externalChange("external unknown save")
        assertEquals(PersistenceStatus.Conflict, subject.state.value.status)
        assertIs<DownloadResult.Downloaded>(exporter.exportCurrent()).also {
            assertTrue(it.label.contains("not confirmed saved"))
        }
        assertEquals(subject.state.value.snapshot,
            (SaveCodec.decodeSave(sink.calls.last().text) as SaveDecodeResult.Valid).snapshot)
        assertEquals("external unknown save", backing.raw)
        assertTrue(sink.calls.all { it.filename == "hiragame-state-2024-01-01.json" && it.type.startsWith("application/json") })
        assertEquals(3, sink.calls.size)
    }

    @Test fun savedExportIsMarkedValidatedAndRawRecoveryIsSeparateExactUnvalidatedText() {
        val backing = MemoryProgressBacking()
        val subject = owner(MemoryProgressStore(backing))
        dark(subject)
        val savedRaw = backing.raw
        val sink = RecordingSink()
        assertEquals("Validated progress backup", assertIs<DownloadResult.Downloaded>(downloads(subject, sink).exportCurrent()).label)
        assertEquals(savedRaw, sink.calls.single().text)
        assertEquals(DownloadResult.OriginalUnavailable, downloads(subject, sink).downloadProtectedOriginal())

        for (raw in listOf(" \n{\"schemaVersion\":99,\"note\":\"日本語\"} \n", "{not valid\u0000")) {
            val protectedBacking = MemoryProgressBacking(raw)
            val protectedOwner = owner(MemoryProgressStore(protectedBacking))
            assertIs<PersistenceStatus.Protected>(protectedOwner.state.value.status)
            dark(protectedOwner) // current work can still be exported; original must remain untouched
            val protectedSink = RecordingSink()
            val protectedDownloads = downloads(protectedOwner, protectedSink)
            assertIs<DownloadResult.Downloaded>(protectedDownloads.downloadProtectedOriginal()).also {
                assertEquals("hiragame-unvalidated-recovery-2024-01-01.txt", it.filename)
                assertTrue(it.label.contains("Unvalidated"))
                assertTrue(it.label.contains("not a validated/importable backup"))
            }
            assertEquals(Capture("hiragame-unvalidated-recovery-2024-01-01.txt", "text/plain;charset=utf-8", raw), protectedSink.calls.single())
            assertIs<DownloadResult.Downloaded>(protectedDownloads.exportCurrent())
            assertFalse(protectedSink.calls.last().text == raw)
            assertEquals(protectedOwner.state.value.snapshot,
                (SaveCodec.decodeSave(protectedSink.calls.last().text) as SaveDecodeResult.Valid).snapshot)
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
        assertEquals(subject.state.value.snapshot,
            (SaveCodec.decodeSave(sink.calls.single().text) as SaveDecodeResult.Valid).snapshot)
        assertEquals("unknown original", backing.raw)
    }

    @Test fun failingSinkCannotChangeMemoryStatusOrStorage() {
        for (raw in listOf<String?>(null, "{invalid")) {
            val backing = MemoryProgressBacking(raw)
            val subject = owner(MemoryProgressStore(backing))
            dark(subject)
            val before = subject.state.value
            val baseline = subject.savedBaseline
            val sink = RecordingSink().apply { fail = true }
            val exporter = downloads(subject, sink)
            assertEquals(DownloadResult.Failed, exporter.exportCurrent())
            if (raw == null) assertEquals(DownloadResult.OriginalUnavailable, exporter.downloadProtectedOriginal())
            else assertEquals(DownloadResult.Failed, exporter.downloadProtectedOriginal())
            assertEquals(before, subject.state.value)
            assertEquals(baseline, subject.savedBaseline)
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
