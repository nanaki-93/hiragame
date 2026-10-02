package com.github.nanaki_93.storage

import com.github.nanaki_93.progress.SaveCodec
import com.github.nanaki_93.progress.SaveDecodeResult
import com.github.nanaki_93.progress.SaveEnvelope
import com.github.nanaki_93.progress.SavedColorMode
import com.github.nanaki_93.progress.changePreferences
import com.github.nanaki_93.progress.encodeSave
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalProgressConflictTest {
    private class CountingStore(val adapter: MemoryProgressStore) : ProgressStore by adapter {
        var reads = 0
        var writes = 0
        override fun read(): StoreReadResult { reads++; return adapter.read() }
        override fun write(expectedRaw: String?, replacementRaw: String): StoreWriteResult {
            writes++
            return adapter.write(expectedRaw, replacementRaw)
        }
    }

    private fun owner(store: ProgressStore): LocalProgressOwner {
        var id = 0
        return LocalProgressOwner(store, { 100L + id }, { "id_${++id}" })
    }

    private fun dark(subject: LocalProgressOwner) = subject.mutate {
        changePreferences(it, it.preferences.copy(colorMode = SavedColorMode.DARK))
    }

    @Test fun otherOwnerWritePausesBeforeNextWriteAndKeepPreservesMemory() {
        val backing = MemoryProgressBacking()
        val firstStore = CountingStore(MemoryProgressStore(backing))
        val secondStore = CountingStore(MemoryProgressStore(backing))
        val first = owner(firstStore)
        val second = owner(secondStore)
        dark(first)
        assertEquals(PersistenceStatus.Conflict, second.state.value.status)
        assertEquals(2, secondStore.reads) // startup plus event reread
        assertNull(second.savedBaseline) // baseline is not silently advanced
        assertEquals(PersistenceStatus.Conflict, second.keepThisView())
        dark(second)
        assertEquals(SavedColorMode.DARK, second.state.value.snapshot.preferences.colorMode)
        assertEquals(0, secondStore.writes)
        assertEquals(PersistenceStatus.Conflict, second.retrySaving())
        assertEquals(0, secondStore.writes)
        assertIs<SaveDecodeResult.Valid>(SaveCodec.decodeSave(encodeSave(second.state.value.snapshot)))
        assertEquals(first.savedBaseline, backing.raw)
        val oldGeneration = second.generation
        assertTrue(second.isCurrentGeneration(oldGeneration))
        assertTrue(second.reloadSavedState())
        assertFalse(second.isCurrentGeneration(oldGeneration))
        assertTrue(second.isCurrentGeneration(second.generation))
        assertEquals(oldGeneration + 1, second.generation)
        assertEquals(PersistenceStatus.Saved, second.state.value.status)
        assertEquals(first.state.value.snapshot, second.state.value.snapshot)
        assertEquals(first.savedBaseline, second.savedBaseline)
        assertNull(second.originalProtectedRaw)
        assertEquals(0, secondStore.writes) // reload is read-only
        first.dispose()
        second.dispose()
    }

    @Test fun equalSignalDoesNothingButDeletionAndInvalidReplacementsPauseUntilReload() {
        val original = encodeSave(SaveEnvelope(savedAtEpochMs = 5, snapshotId = "initial", revision = 0))
        val backing = MemoryProgressBacking(original)
        val store = CountingStore(MemoryProgressStore(backing))
        val subject = owner(store)
        val initial = subject.state.value
        backing.signalExternalChange()
        assertEquals(2, store.reads)
        assertEquals(initial, subject.state.value)
        assertEquals(0L, subject.generation)
        for (replacement in listOf(null, "not json", """{"schemaVersion":99}""")) {
            backing.externalChange(replacement)
            assertEquals(PersistenceStatus.Conflict, subject.state.value.status)
            assertEquals(initial.snapshot, subject.state.value.snapshot)
            assertEquals(original, subject.savedBaseline)
            assertEquals(PersistenceStatus.Conflict, subject.keepThisView())
            assertEquals(0, store.writes)
            assertTrue(subject.reloadSavedState())
            if (replacement == null) {
                assertEquals(PersistenceStatus.Fresh, subject.state.value.status)
                assertNull(subject.savedBaseline)
                assertNull(subject.originalProtectedRaw)
            } else {
                assertIs<PersistenceStatus.Protected>(subject.state.value.status)
                assertEquals(replacement, subject.originalProtectedRaw)
                assertEquals(replacement, subject.savedBaseline)
            }
            // Restore initial state by an explicit reload, not by an automatic event.
            backing.externalChange(original)
            assertEquals(PersistenceStatus.Conflict, subject.state.value.status)
            assertTrue(subject.reloadSavedState())
            assertEquals(initial.snapshot, subject.state.value.snapshot)
        }
        assertEquals(6L, subject.generation)
        subject.dispose()
    }

    @Test fun missedEventDetectedByPreWriteComparisonAndFailedReloadKeepsMemory() {
        val backing = MemoryProgressBacking()
        val adapter = MemoryProgressStore(backing)
        val store = CountingStore(adapter)
        val missedEvents = object : ProgressStore by store {
            override fun subscribe(onExternalChange: () -> Unit): StoreSubscription = StoreSubscription {}
        }
        val subject = owner(missedEvents)
        // Simulate a tab change whose notification was lost.
        val other = MemoryProgressStore(backing)
        val externalRaw = encodeSave(SaveEnvelope(savedAtEpochMs = 7, snapshotId = "external", revision = 0))
        assertEquals(StoreWriteResult.Written, other.write(null, externalRaw))
        dark(subject)
        assertEquals(1, store.writes)
        assertEquals(PersistenceStatus.Conflict, subject.state.value.status)
        assertEquals(SavedColorMode.DARK, subject.state.value.snapshot.preferences.colorMode)
        assertNull(subject.savedBaseline)
        assertEquals(externalRaw, backing.raw)
        adapter.readFailure = StoreFailure.DENIED
        assertFalse(subject.reloadSavedState())
        assertEquals(0L, subject.generation)
        assertEquals(PersistenceStatus.Conflict, subject.state.value.status)
        assertEquals(SavedColorMode.DARK, subject.state.value.snapshot.preferences.colorMode)
        adapter.readFailure = null
        assertTrue(subject.reloadSavedState())
        assertEquals(1L, subject.generation)
        assertEquals((SaveCodec.decodeSave(externalRaw) as SaveDecodeResult.Valid).snapshot, subject.state.value.snapshot)
        assertEquals(1, store.writes)
        subject.dispose()
    }

    @Test fun unreadableEventCannotTurnProtectedStateIntoRetryableSave() {
        val backing = MemoryProgressBacking("not json")
        val adapter = MemoryProgressStore(backing)
        val store = CountingStore(adapter)
        val subject = owner(store)
        adapter.readFailure = StoreFailure.DENIED
        backing.signalExternalChange()
        assertIs<PersistenceStatus.Protected>(subject.state.value.status)
        assertIs<PersistenceStatus.Protected>(subject.retrySaving())
        assertEquals(0, store.writes)
        assertEquals("not json", backing.raw)
        subject.dispose()
    }

    @Test fun disposalStopsEventsEvenWhenAQueuedCallbackRuns() {
        val backing = MemoryProgressBacking()
        val store = CountingStore(MemoryProgressStore(backing))
        var queued: (() -> Unit)? = null
        val platform = object : ProgressStore by store {
            override fun subscribe(onExternalChange: () -> Unit): StoreSubscription {
                queued = onExternalChange
                return StoreSubscription {} // model removal failure / queued delivery
            }
        }
        val subject = owner(platform)
        subject.dispose()
        subject.dispose()
        val generation = subject.generation
        backing.externalChange("not json")
        queued!!()
        assertEquals(1, store.reads)
        assertEquals(PersistenceStatus.Fresh, subject.state.value.status)
        assertEquals(generation, subject.generation)
        assertFalse(subject.isCurrentGeneration(generation))
        assertFalse(subject.reloadSavedState())
        assertEquals(0, store.writes)
    }
}
