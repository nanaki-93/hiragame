package com.github.nanaki_93.storage

import com.github.nanaki_93.progress.SaveCodec
import com.github.nanaki_93.progress.SaveDecodeResult
import com.github.nanaki_93.progress.SaveProblem
import com.github.nanaki_93.progress.SavedColorMode
import com.github.nanaki_93.progress.changePreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProtectedSaveRecoveryTest {
    private class CountingStore(val adapter: MemoryProgressStore) : ProgressStore by adapter {
        var reads = 0
        var writes = 0
        var lastExpected: String? = null
        var lastReplacement: String? = null
        override fun read(): StoreReadResult { reads++; return adapter.read() }
        override fun write(expectedRaw: String?, replacementRaw: String): StoreWriteResult {
            writes++
            lastExpected = expectedRaw
            lastReplacement = replacementRaw
            return adapter.write(expectedRaw, replacementRaw)
        }
    }

    private fun owner(store: ProgressStore): LocalProgressOwner {
        var id = 0
        return LocalProgressOwner(store, { 100L }, { "recovery_${++id}" })
    }

    private fun dark(subject: LocalProgressOwner) = subject.mutate {
        changePreferences(it, it.preferences.copy(colorMode = SavedColorMode.DARK))
    }

    @Test fun cancelAndRepeatedTokensCannotReplaceProtectedOriginal() {
        val raw = "{not valid\n日本語"
        val backing = MemoryProgressBacking(raw)
        val store = CountingStore(MemoryProgressStore(backing))
        val subject = owner(store)
        assertIs<PersistenceStatus.Protected>(subject.state.value.status)
        val before = subject.state.value
        val first = subject.beginProtectedReplacement()!!
        val second = subject.beginProtectedReplacement()!!
        assertEquals(ProtectedReplacementResult.Stale, subject.confirmProtectedReplacement(first))
        assertTrue(subject.cancelProtectedReplacement(second))
        assertFalse(subject.cancelProtectedReplacement(second))
        assertEquals(ProtectedReplacementResult.Stale, subject.confirmProtectedReplacement(second))
        assertEquals(1, store.reads) // issuance and cancel do not access storage
        assertEquals(0, store.writes)
        assertEquals(raw, backing.raw)
        assertEquals(raw, subject.originalProtectedRaw)
        assertEquals(before, subject.state.value)
    }

    @Test fun confirmedRecoveryWritesOneValidFreshSnapshotAndOnlyThenPublishesIt() {
        for (raw in listOf("not json", """{"schemaVersion":99}""")) {
            val backing = MemoryProgressBacking(raw)
            val store = CountingStore(MemoryProgressStore(backing))
            lateinit var subject: LocalProgressOwner
            var beforeWrite: LocalProgressState? = null
            val checking = object : ProgressStore by store {
                override fun write(expectedRaw: String?, replacementRaw: String): StoreWriteResult {
                    assertEquals(beforeWrite, subject.state.value) // storage-first, even inside write
                    return store.write(expectedRaw, replacementRaw)
                }
            }
            subject = owner(checking)
            dark(subject) // active work must not leak into a fresh replacement
            beforeWrite = subject.state.value
            val generation = subject.generation
            val token = subject.beginProtectedReplacement()!!
            assertEquals(ProtectedReplacementResult.Replaced, subject.confirmProtectedReplacement(token))
            assertEquals(raw, store.lastExpected)
            assertEquals(1, store.writes)
            assertEquals(2, store.reads) // startup and explicit confirmation reread
            assertEquals(store.lastReplacement, backing.raw)
            val replacement = (SaveCodec.decodeSave(backing.raw!!) as SaveDecodeResult.Valid).snapshot
            assertEquals(replacement, subject.state.value.snapshot)
            assertEquals(0L, replacement.revision)
            assertEquals(SavedColorMode.SYSTEM, replacement.preferences.colorMode)
            assertEquals(PersistenceStatus.Saved, subject.state.value.status)
            assertNull(subject.originalProtectedRaw)
            assertEquals(backing.raw, subject.savedBaseline)
            assertFalse(subject.isCurrentGeneration(generation))
            assertNull(subject.beginProtectedReplacement())
            assertEquals(ProtectedReplacementResult.Stale, subject.confirmProtectedReplacement(token))
            assertFalse(subject.cancelProtectedReplacement(token))
            assertEquals(1, store.writes)
        }
    }

    @Test fun readDenialAndEveryWriteFailurePreserveOriginalAndActiveMemory() {
        for (reason in StoreFailure.entries) {
            val raw = """{"schemaVersion":100}"""
            val backing = MemoryProgressBacking(raw)
            val adapter = MemoryProgressStore(backing)
            val store = CountingStore(adapter)
            val subject = owner(store)
            dark(subject)
            val before = subject.state.value
            val generation = subject.generation
            var callbacks = 0
            subject.observeGeneration { callbacks++ }
            adapter.readFailure = reason
            val denied = subject.beginProtectedReplacement()!!
            assertEquals(ProtectedReplacementResult.Failure(reason), subject.confirmProtectedReplacement(denied))
            assertEquals(0, store.writes)
            assertEquals(ProtectedReplacementResult.Stale, subject.confirmProtectedReplacement(denied))
            adapter.readFailure = null
            adapter.writeFailure = reason
            val failed = subject.beginProtectedReplacement()!!
            assertEquals(ProtectedReplacementResult.Failure(reason), subject.confirmProtectedReplacement(failed))
            assertEquals(1, store.writes)
            assertEquals(ProtectedReplacementResult.Stale, subject.confirmProtectedReplacement(failed))
            assertEquals(before, subject.state.value)
            assertEquals(raw, backing.raw)
            assertEquals(raw, subject.savedBaseline)
            assertEquals(raw, subject.originalProtectedRaw)
            assertEquals(generation, subject.generation)
            assertTrue(subject.isCurrentGeneration(generation))
            assertEquals(0, callbacks)
            adapter.writeFailure = null
            assertEquals(ProtectedReplacementResult.Replaced,
                subject.confirmProtectedReplacement(subject.beginProtectedReplacement()!!))
            assertEquals(2, store.writes)
            assertEquals(1, callbacks)
        }
    }

    @Test fun invalidFreshEnvelopeCannotReplaceProtectedOriginal() {
        val backing = MemoryProgressBacking("broken")
        val store = CountingStore(MemoryProgressStore(backing))
        var time = 100L
        val subject = LocalProgressOwner(store, { time }, { "valid_id" })
        val before = subject.state.value
        time = -1L
        val token = subject.beginProtectedReplacement()!!
        assertEquals(ProtectedReplacementResult.InvalidReplacement(SaveProblem.INVALID_SNAPSHOT),
            subject.confirmProtectedReplacement(token))
        assertEquals(ProtectedReplacementResult.Stale, subject.confirmProtectedReplacement(token))
        assertEquals(before, subject.state.value)
        assertEquals("broken", backing.raw)
        assertEquals("broken", subject.originalProtectedRaw)
        assertEquals(0, store.writes)
    }

    @Test fun externalChangeOrMissedEventNeverOverwritesTheOtherValue() {
        for (external in listOf<String?>(null, "replacement", """{"schemaVersion":2}""")) {
            val backing = MemoryProgressBacking("bad save")
            val store = CountingStore(MemoryProgressStore(backing))
            val subject = owner(store)
            dark(subject)
            val originalMemory = subject.state.value.snapshot
            val token = subject.beginProtectedReplacement()!!
            backing.externalChange(external)
            assertEquals(ProtectedReplacementResult.Stale, subject.confirmProtectedReplacement(token))
            assertEquals(0, store.writes)
            assertEquals(PersistenceStatus.Conflict, subject.state.value.status)
            assertEquals(originalMemory, subject.state.value.snapshot)
            assertEquals("bad save", subject.originalProtectedRaw)
            assertEquals(external, backing.raw)

            // A lost event is still caught by confirmation's reread.
            val missed = object : ProgressStore by store {
                override fun subscribe(onExternalChange: () -> Unit) = StoreSubscription {}
            }
            backing.externalChange("bad save")
            val silentOwner = owner(missed)
            val silentToken = silentOwner.beginProtectedReplacement()!!
            backing.externalChange(external)
            assertEquals(ProtectedReplacementResult.Conflict, silentOwner.confirmProtectedReplacement(silentToken))
            assertEquals(PersistenceStatus.Conflict, silentOwner.state.value.status)
            assertEquals("bad save", silentOwner.originalProtectedRaw)
            assertEquals(0, store.writes)
            assertEquals(external, backing.raw)
        }
    }

    @Test fun changeBetweenRereadAndWriteIsCaughtByStoreComparison() {
        val backing = MemoryProgressBacking("broken")
        val delegate = CountingStore(MemoryProgressStore(backing))
        val racing = object : ProgressStore by delegate {
            override fun write(expectedRaw: String?, replacementRaw: String): StoreWriteResult {
                backing.externalChange("other tab")
                return delegate.write(expectedRaw, replacementRaw)
            }
        }
        val subject = owner(racing)
        val memory = subject.state.value.snapshot
        val token = subject.beginProtectedReplacement()!!
        assertEquals(ProtectedReplacementResult.Conflict, subject.confirmProtectedReplacement(token))
        assertEquals(1, delegate.writes)
        assertEquals("broken", delegate.lastExpected)
        assertEquals("other tab", backing.raw)
        assertEquals(memory, subject.state.value.snapshot)
        assertEquals("broken", subject.originalProtectedRaw)
        assertEquals(PersistenceStatus.Conflict, subject.state.value.status)
    }

    @Test fun reloadDisposeAndAcceptedLearningInvalidatePendingConfirmation() {
        val backing = MemoryProgressBacking("broken")
        val store = CountingStore(MemoryProgressStore(backing))
        val subject = owner(store)
        val beforeChange = subject.beginProtectedReplacement()!!
        dark(subject)
        assertEquals(ProtectedReplacementResult.Stale, subject.confirmProtectedReplacement(beforeChange))
        val beforeReload = subject.beginProtectedReplacement()!!
        assertTrue(subject.reloadSavedState())
        assertEquals(ProtectedReplacementResult.Stale, subject.confirmProtectedReplacement(beforeReload))
        val beforeDispose = subject.beginProtectedReplacement()!!
        subject.dispose()
        assertEquals(ProtectedReplacementResult.Stale, subject.confirmProtectedReplacement(beforeDispose))
        assertNull(subject.beginProtectedReplacement())
        assertFalse(subject.cancelProtectedReplacement(beforeDispose))
        assertEquals(0, store.writes)
        assertEquals("broken", backing.raw)
        assertNull(owner(MemoryProgressStore()).beginProtectedReplacement())
    }
}
