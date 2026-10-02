package com.github.nanaki_93.storage

import com.github.nanaki_93.progress.SaveProblem
import com.github.nanaki_93.progress.SavedColorMode
import com.github.nanaki_93.progress.changePreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReplacementAuthorizationTest {
    private class ObservedStore(val backing: MemoryProgressBacking) : ProgressStore {
        private val delegate = MemoryProgressStore(backing)
        var reads = 0
        var writes = 0
        var expected: String? = null
        var beforeWrite: (() -> Unit)? = null
        var failRead: StoreFailure? = null
        var failWrite: StoreFailure? = null
        override fun read(): StoreReadResult {
            reads++
            return failRead?.let { StoreReadResult.Failure(it) } ?: delegate.read()
        }
        override fun write(expectedRaw: String?, replacementRaw: String): StoreWriteResult {
            writes++
            expected = expectedRaw
            beforeWrite?.invoke()
            return failWrite?.let { StoreWriteResult.Failure(it) }
                ?: delegate.write(expectedRaw, replacementRaw)
        }
        override fun subscribe(onExternalChange: () -> Unit): StoreSubscription = delegate.subscribe(onExternalChange)
    }

    private fun owner(store: ProgressStore, time: () -> Long = { 123L }) =
        LocalProgressOwner(store, time, { "local_valid_id" })

    @Test fun tokenBelongsToOneOwnerAndOneOperationAndIsConsumedBeforeRead() {
        val backing = MemoryProgressBacking("unreadable")
        val store = ObservedStore(backing)
        val first = owner(store)
        val other = owner(store)
        val token = assertIs<ReplacementPreparation.Ready>(first.prepareProtectedReplacement()).token
        assertEquals(ReplacementResult.Stale, other.confirmReplacement(token))
        assertEquals(2, store.reads)
        assertEquals(0, store.writes)
        store.failRead = StoreFailure.DENIED
        assertEquals(ReplacementResult.Failure(StoreFailure.DENIED), first.confirmReplacement(token))
        assertEquals(ReplacementResult.Stale, first.confirmReplacement(token))
        assertEquals(3, store.reads)
        assertEquals(0, store.writes)
        assertEquals("unreadable", backing.raw)
        assertEquals(ReplacementPreparation.Blocked, owner(MemoryProgressStore()).prepareProtectedReplacement())
    }

    @Test fun cancelMutationAndReloadInvalidateWithoutWriting() {
        val backing = MemoryProgressBacking("unreadable")
        val store = ObservedStore(backing)
        val subject = owner(store)
        val canceled = subject.beginProtectedReplacement()!!
        assertTrue(subject.cancelReplacement(canceled))
        assertFalse(subject.cancelReplacement(canceled))
        assertEquals(ReplacementResult.Stale, subject.confirmReplacement(canceled))
        val changed = subject.beginProtectedReplacement()!!
        subject.mutate { changePreferences(it, it.preferences.copy(colorMode = SavedColorMode.DARK)) }
        assertEquals(ReplacementResult.Stale, subject.confirmReplacement(changed))
        val reloaded = subject.beginProtectedReplacement()!!
        assertTrue(subject.reloadSavedState())
        assertEquals(ReplacementResult.Stale, subject.confirmReplacement(reloaded))
        val disposed = subject.beginProtectedReplacement()!!
        subject.dispose()
        assertEquals(ReplacementResult.Stale, subject.confirmReplacement(disposed))
        assertEquals(0, store.writes)
        assertEquals("unreadable", backing.raw)
    }

    @Test fun invalidMetadataAndDeniedWriteKeepMemoryBaselineGenerationAndCallbacks() {
        val backing = MemoryProgressBacking("unreadable")
        val store = ObservedStore(backing)
        var time = 123L
        val subject = owner(store) { time }
        subject.mutate { changePreferences(it, it.preferences.copy(colorMode = SavedColorMode.DARK)) }
        val original = subject.state.value
        val generation = subject.generation
        var notifications = 0
        subject.observeGeneration { notifications++ }
        time = -1L
        val invalid = subject.beginProtectedReplacement()!!
        assertEquals(ReplacementResult.InvalidReplacement(SaveProblem.INVALID_SNAPSHOT),
            subject.confirmReplacement(invalid))
        assertEquals(ReplacementResult.Stale, subject.confirmReplacement(invalid))
        assertEquals(0, store.writes)
        time = 123L
        store.failWrite = StoreFailure.QUOTA
        val denied = subject.beginProtectedReplacement()!!
        store.beforeWrite = {
            assertEquals(original, subject.state.value)
            assertEquals(generation, subject.generation)
            assertEquals("unreadable", backing.raw)
        }
        assertEquals(ReplacementResult.Failure(StoreFailure.QUOTA), subject.confirmReplacement(denied))
        assertEquals(ReplacementResult.Stale, subject.confirmReplacement(denied))
        assertEquals(1, store.writes)
        assertEquals("unreadable", store.expected)
        assertEquals(original, subject.state.value)
        assertEquals("unreadable", subject.savedBaseline)
        assertEquals("unreadable", subject.originalProtectedRaw)
        assertEquals(generation, subject.generation)
        assertTrue(subject.isCurrentGeneration(generation))
        assertEquals(0, notifications)
        assertEquals("unreadable", backing.raw)
    }

    @Test fun successfulConfirmationPublishesOnlyAfterTheSingleGuardedWrite() {
        val backing = MemoryProgressBacking("unreadable")
        val store = ObservedStore(backing)
        val subject = owner(store)
        val initial = subject.state.value
        val generation = subject.generation
        var notifications = 0
        subject.observeGeneration { notifications++ }
        store.beforeWrite = {
            assertEquals(initial, subject.state.value)
            assertEquals(generation, subject.generation)
            assertEquals("unreadable", backing.raw)
        }
        val token = subject.beginProtectedReplacement()!!
        assertEquals(ReplacementResult.Replaced, subject.confirmReplacement(token))
        assertEquals(ReplacementResult.Stale, subject.confirmReplacement(token))
        assertEquals(2, store.reads)
        assertEquals(1, store.writes)
        assertEquals("unreadable", store.expected)
        assertEquals(backing.raw, subject.savedBaseline)
        assertNull(subject.originalProtectedRaw)
        assertIs<PersistenceStatus.Saved>(subject.state.value.status)
        assertEquals(1, notifications)
        assertFalse(subject.isCurrentGeneration(generation))
    }

    @Test fun missedExternalDeletionOrChangeIsDetectedBeforeAnyWrite() {
        for (external in listOf<String?>(null, "other")) {
            val backing = MemoryProgressBacking("unreadable")
            val store = ObservedStore(backing)
            val silent = object : ProgressStore by store {
                override fun subscribe(onExternalChange: () -> Unit) = StoreSubscription {}
            }
            val subject = owner(silent)
            val initial = subject.state.value.snapshot
            val generation = subject.generation
            val token = subject.beginProtectedReplacement()!!
            backing.externalChange(external)
            assertEquals(ReplacementResult.Conflict, subject.confirmReplacement(token))
            assertEquals(0, store.writes)
            assertEquals(external, backing.raw)
            assertEquals(initial, subject.state.value.snapshot)
            assertEquals(generation, subject.generation)
            assertEquals(PersistenceStatus.Conflict, subject.state.value.status)
        }
    }
}
