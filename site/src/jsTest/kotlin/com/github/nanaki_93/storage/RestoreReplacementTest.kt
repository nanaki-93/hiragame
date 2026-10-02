package com.github.nanaki_93.storage

import com.github.nanaki_93.progress.BackupCodec
import com.github.nanaki_93.progress.BackupDecodeResult
import com.github.nanaki_93.progress.LessonProgress
import com.github.nanaki_93.progress.LessonStage
import com.github.nanaki_93.progress.ReviewItemProgress
import com.github.nanaki_93.progress.ReviewOutcome
import com.github.nanaki_93.progress.SaveBounds
import com.github.nanaki_93.progress.SaveCodec
import com.github.nanaki_93.progress.SaveDecodeResult
import com.github.nanaki_93.progress.SaveEnvelope
import com.github.nanaki_93.progress.SaveProblem
import com.github.nanaki_93.progress.SavedColorMode
import com.github.nanaki_93.progress.ValidatedBackup
import com.github.nanaki_93.progress.changePreferences
import com.github.nanaki_93.progress.encodeSave
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RestoreReplacementTest {
    private class SpyStore(val backing: MemoryProgressBacking, val silent: Boolean = false) : ProgressStore {
        val adapter = MemoryProgressStore(backing)
        var reads = 0
        var writes = 0
        var expected: String? = null
        var beforeWrite: (() -> Unit)? = null
        override fun read(): StoreReadResult { reads++; return adapter.read() }
        override fun write(expectedRaw: String?, replacementRaw: String): StoreWriteResult {
            writes++
            expected = expectedRaw
            beforeWrite?.invoke()
            return adapter.write(expectedRaw, replacementRaw)
        }
        override fun subscribe(onExternalChange: () -> Unit): StoreSubscription =
            if (silent) StoreSubscription {} else adapter.subscribe(onExternalChange)
    }

    private val imported = SaveEnvelope(savedAtEpochMs = 60, snapshotId = "imported_id", revision = 43,
        preferences = com.github.nanaki_93.progress.SavePreferences(colorMode = SavedColorMode.DARK, showRomaji = true),
        lessonProgress = listOf(LessonProgress("unknown_lesson", 2, 55, LessonStage.SUMMARY, completedAtEpochMs = 55)),
        reviewItems = listOf(ReviewItemProgress("unknown_item", "unknown_doc", ReviewOutcome.GOOD, 58,
            dueAtEpochMs = 80, intervalMs = 22, step = 2, repetitions = 3, lapses = 0, lastActionToken = "action_1")))
    private fun candidate(): ValidatedBackup = assertIs<BackupDecodeResult.Valid>(
        BackupCodec.decodeBackup(BackupCodec.encodeBackup(imported, "1.0-SNAPSHOT", 90))
    ).backup
    private fun owner(store: ProgressStore, clock: () -> Long = { 200L }, id: (() -> String)? = null): LocalProgressOwner {
        var next = 0
        return LocalProgressOwner(store, clock, id ?: { "new_local_id_${++next}" })
    }
    private fun stored(revision: Long = 7) = encodeSave(SaveEnvelope(savedAtEpochMs = 10,
        snapshotId = "old_id", revision = revision))
    private fun restoredPayload(snapshot: SaveEnvelope) {
        assertEquals(imported.preferences, snapshot.preferences)
        assertEquals(imported.lessonProgress, snapshot.lessonProgress)
        assertEquals(imported.practiceProgress, snapshot.practiceProgress)
        assertEquals(imported.reviewItems, snapshot.reviewItems)
    }

    @Test fun freshSavedMemoryOnlyAndProtectedReplaceCompletePayloadWithLocalMetadata() {
        for (raw in listOf<String?>(null, stored(), "unreadable")) {
            val backing = MemoryProgressBacking(raw)
            val store = SpyStore(backing)
            val subject = owner(store)
            val before = subject.state.value
            val generation = subject.generation
            var callbacks = 0
            subject.observeGeneration { callbacks++ }
            store.beforeWrite = {
                assertEquals(before, subject.state.value)
                assertEquals(generation, subject.generation)
                assertEquals(raw, backing.raw)
            }
            val token = assertIs<ReplacementPreparation.Ready>(subject.beginRestore(candidate())).token
            assertEquals(ReplacementResult.Replaced, subject.confirmReplacement(token))
            assertEquals(ReplacementResult.Stale, subject.confirmReplacement(token))
            assertEquals(1, store.writes)
            assertEquals(raw, store.expected)
            val result = subject.state.value.snapshot
            assertEquals(result, assertIs<SaveDecodeResult.Valid>(SaveCodec.decodeSave(backing.raw!!)).snapshot)
            restoredPayload(result)
            assertTrue(result.snapshotId.startsWith("new_local_id_"))
            assertEquals(200L, result.savedAtEpochMs)
            assertEquals(if (raw == "unreadable") 0L else if (raw == null) 1L else 8L, result.revision)
            assertEquals(PersistenceStatus.Saved, subject.state.value.status)
            assertEquals(backing.raw, subject.savedBaseline)
            assertNull(subject.originalProtectedRaw)
            assertFalse(subject.isCurrentGeneration(generation))
            assertEquals(1, callbacks)
        }
        val backing = MemoryProgressBacking(stored())
        val store = SpyStore(backing)
        val subject = owner(store)
        store.adapter.writeFailure = StoreFailure.QUOTA
        subject.mutate { changePreferences(it, it.preferences.copy(showReadings = false)) }
        assertIs<PersistenceStatus.MemoryOnly>(subject.state.value.status)
        val previous = subject.state.value
        store.adapter.writeFailure = null
        val token = assertIs<ReplacementPreparation.Ready>(subject.beginRestore(candidate())).token
        assertEquals(ReplacementResult.Replaced, subject.confirmReplacement(token))
        assertEquals(previous.snapshot.revision + 1, subject.state.value.snapshot.revision)
        restoredPayload(subject.state.value.snapshot)
    }

    @Test fun unknownStartupBaselineMustBeReconciledWithoutReplacingDiscoveredData() {
        val backing = MemoryProgressBacking(stored())
        val store = SpyStore(backing)
        store.adapter.readFailure = StoreFailure.DENIED
        val subject = owner(store)
        val generation = subject.generation
        assertEquals(ReplacementPreparation.Blocked, subject.beginRestore(candidate()))
        assertEquals(0, store.writes)
        store.adapter.readFailure = null
        assertEquals(ReplacementPreparation.Blocked, subject.beginRestore(candidate()))
        assertEquals(PersistenceStatus.Conflict, subject.state.value.status)
        assertEquals(ReplacementPreparation.Blocked, subject.beginRestore(candidate()))
        assertEquals(stored(), backing.raw)
        assertEquals(generation, subject.generation)
        assertTrue(subject.reloadSavedState())
        assertIs<ReplacementPreparation.Ready>(subject.beginRestore(candidate()))

        val missing = MemoryProgressBacking()
        val other = SpyStore(missing)
        other.adapter.readFailure = StoreFailure.DENIED
        val memoryOwner = owner(other)
        memoryOwner.mutate { changePreferences(it, it.preferences.copy(showRomaji = true)) }
        val memory = memoryOwner.state.value
        other.adapter.readFailure = null
        val token = assertIs<ReplacementPreparation.Ready>(memoryOwner.beginRestore(candidate())).token
        assertEquals(memory, memoryOwner.state.value)
        assertEquals(ReplacementResult.Replaced, memoryOwner.confirmReplacement(token))
        assertEquals(1, other.writes)
    }

    @Test fun readAndWriteFailuresConsumeTokenWithoutPublishingAndRetryTargetsAcceptedMemory() {
        for (reason in StoreFailure.entries) {
            val backing = MemoryProgressBacking(stored())
            val store = SpyStore(backing)
            val subject = owner(store)
            val original = subject.state.value
            val generation = subject.generation
            var callbacks = 0
            subject.observeGeneration { callbacks++ }
            val denied = assertIs<ReplacementPreparation.Ready>(subject.beginRestore(candidate())).token
            store.adapter.readFailure = reason
            assertEquals(ReplacementResult.Failure(reason), subject.confirmReplacement(denied))
            assertEquals(ReplacementResult.Stale, subject.confirmReplacement(denied))
            assertEquals(0, store.writes)
            store.adapter.readFailure = null
            store.adapter.writeFailure = reason
            val failed = assertIs<ReplacementPreparation.Ready>(subject.beginRestore(candidate())).token
            assertEquals(ReplacementResult.Failure(reason), subject.confirmReplacement(failed))
            assertEquals(ReplacementResult.Stale, subject.confirmReplacement(failed))
            assertEquals(1, store.writes)
            assertEquals(original, subject.state.value)
            assertEquals(stored(), backing.raw)
            assertEquals(stored(), subject.savedBaseline)
            assertEquals(generation, subject.generation)
            assertEquals(0, callbacks)
            assertEquals(PersistenceStatus.Saved, subject.retrySaving())
            assertEquals(1, store.writes)
        }
        val backing = MemoryProgressBacking(stored())
        val store = SpyStore(backing)
        val subject = owner(store)
        store.adapter.writeFailure = StoreFailure.QUOTA
        subject.mutate { changePreferences(it, it.preferences.copy(showRomaji = true)) }
        val accepted = subject.state.value.snapshot
        val token = assertIs<ReplacementPreparation.Ready>(subject.beginRestore(candidate())).token
        assertEquals(ReplacementResult.Failure(StoreFailure.QUOTA), subject.confirmReplacement(token))
        store.adapter.writeFailure = null
        assertEquals(PersistenceStatus.Saved, subject.retrySaving())
        assertEquals(accepted.preferences, subject.state.value.snapshot.preferences)
        assertEquals(emptyList(), subject.state.value.snapshot.lessonProgress)
    }

    @Test fun throwingMetadataSuppliersConsumeRestoreAuthorizationBeforeStorageAccess() {
        for (protected in listOf(false, true)) for (throwClock in listOf(false, true)) {
            val raw = if (protected) "unreadable" else stored()
            val backing = MemoryProgressBacking(raw)
            val store = SpyStore(backing)
            var fail = false
            var ids = 0
            val subject = owner(store,
                { if (fail && throwClock) error("clock unavailable") else 200L },
                { if (fail && !throwClock) error("identity unavailable") else "new_local_id_${++ids}" })
            val original = subject.state.value
            val baseline = subject.savedBaseline
            val recovery = subject.originalProtectedRaw
            val generation = subject.generation
            var callbacks = 0
            subject.observeGeneration { callbacks++ }
            val token = assertIs<ReplacementPreparation.Ready>(subject.beginRestore(candidate())).token
            val reads = store.reads
            fail = true
            assertEquals(ReplacementResult.InvalidReplacement(SaveProblem.INVALID_SNAPSHOT), subject.confirmReplacement(token))
            assertEquals(ReplacementResult.Stale, subject.confirmReplacement(token))
            assertEquals(reads, store.reads)
            assertEquals(0, store.writes)
            assertEquals(raw, backing.raw)
            assertEquals(baseline, subject.savedBaseline)
            assertEquals(recovery, subject.originalProtectedRaw)
            assertEquals(original, subject.state.value)
            assertEquals(generation, subject.generation)
            assertEquals(0, callbacks)
            fail = false
            assertEquals(ReplacementResult.Replaced, subject.confirmReplacement(
                assertIs<ReplacementPreparation.Ready>(subject.beginRestore(candidate())).token))
            assertEquals(1, store.writes)
        }
    }

    @Test fun overflowInvalidMetadataAndInvalidCandidateNeverWrite() {
        for (revision in listOf(7L, SaveBounds.MAX_SAFE_INTEGER)) {
            val backing = MemoryProgressBacking(stored(revision))
            val store = SpyStore(backing)
            var time = -1L
            var id = "new_local_id"
            val subject = owner(store, { time }, { id })
            val original = subject.state.value
            val generation = subject.generation
            val baseline = subject.savedBaseline
            val token = assertIs<ReplacementPreparation.Ready>(subject.beginRestore(candidate())).token
            assertEquals(ReplacementResult.InvalidReplacement(SaveProblem.INVALID_SNAPSHOT), subject.confirmReplacement(token))
            assertEquals(ReplacementResult.Stale, subject.confirmReplacement(token))
            time = 200
            id = "bad id"
            val badId = assertIs<ReplacementPreparation.Ready>(subject.beginRestore(candidate())).token
            assertEquals(ReplacementResult.InvalidReplacement(SaveProblem.INVALID_SNAPSHOT), subject.confirmReplacement(badId))
            id = "imported_id"
            val duplicate = assertIs<ReplacementPreparation.Ready>(subject.beginRestore(candidate())).token
            assertEquals(ReplacementResult.InvalidReplacement(SaveProblem.INVALID_SNAPSHOT), subject.confirmReplacement(duplicate))
            id = "old_id"
            val reused = assertIs<ReplacementPreparation.Ready>(subject.beginRestore(candidate())).token
            assertEquals(ReplacementResult.InvalidReplacement(SaveProblem.INVALID_SNAPSHOT), subject.confirmReplacement(reused))
            id = "new_local_id"
            val next = assertIs<ReplacementPreparation.Ready>(subject.beginRestore(candidate())).token
            assertEquals(if (revision == SaveBounds.MAX_SAFE_INTEGER)
                ReplacementResult.InvalidReplacement(SaveProblem.INVALID_SNAPSHOT) else ReplacementResult.Replaced,
                subject.confirmReplacement(next))
            assertEquals(ReplacementResult.Stale, subject.confirmReplacement(next))
            assertEquals(if (revision == SaveBounds.MAX_SAFE_INTEGER) 0 else 1, store.writes)
            if (revision == SaveBounds.MAX_SAFE_INTEGER) {
                assertEquals(original, subject.state.value)
                assertEquals(stored(revision), backing.raw)
                assertEquals(baseline, subject.savedBaseline)
                assertEquals(generation, subject.generation)
            }
        }
        val backing = MemoryProgressBacking(stored())
        val store = SpyStore(backing)
        val subject = owner(store)
        val forged = candidate().copy(snapshot = imported.copy(snapshotId = "unsafe id"))
        val token = assertIs<ReplacementPreparation.Ready>(subject.beginRestore(forged)).token
        assertEquals(ReplacementResult.InvalidReplacement(SaveProblem.INVALID_SNAPSHOT), subject.confirmReplacement(token))
        assertEquals(0, store.writes)
    }

    @Test fun protectedWorkAndRecoveryTextSurviveFailedRestore() {
        val backing = MemoryProgressBacking("unreadable")
        val store = SpyStore(backing)
        val subject = owner(store)
        subject.mutate { changePreferences(it, it.preferences.copy(showRomaji = true)) }
        val active = subject.state.value
        val generation = subject.generation
        var callbacks = 0
        subject.observeGeneration { callbacks++ }
        store.adapter.writeFailure = StoreFailure.QUOTA
        val token = assertIs<ReplacementPreparation.Ready>(subject.beginRestore(candidate())).token
        assertEquals(ReplacementResult.Failure(StoreFailure.QUOTA), subject.confirmReplacement(token))
        assertEquals(active, subject.state.value)
        assertEquals("unreadable", backing.raw)
        assertEquals("unreadable", subject.originalProtectedRaw)
        assertEquals(generation, subject.generation)
        assertEquals(0, callbacks)
        assertEquals(active.status, subject.retrySaving())
    }

    @Test fun externalEventsMissedChangesDeletionAndAdapterRaceCannotOverwrite() {
        for (external in listOf<String?>(null, "other tab")) {
            val backing = MemoryProgressBacking(stored())
            val store = SpyStore(backing)
            val subject = owner(store)
            val initial = subject.state.value.snapshot
            val token = assertIs<ReplacementPreparation.Ready>(subject.beginRestore(candidate())).token
            backing.externalChange(external)
            assertEquals(ReplacementResult.Stale, subject.confirmReplacement(token))
            assertEquals(PersistenceStatus.Conflict, subject.state.value.status)
            assertEquals(0, store.writes)
            assertEquals(initial, subject.state.value.snapshot)
            assertEquals(external, backing.raw)

            val silentBacking = MemoryProgressBacking(stored())
            val silent = SpyStore(silentBacking, silent = true)
            val missed = owner(silent)
            val missedToken = assertIs<ReplacementPreparation.Ready>(missed.beginRestore(candidate())).token
            silentBacking.externalChange(external)
            assertEquals(ReplacementResult.Conflict, missed.confirmReplacement(missedToken))
            assertEquals(0, silent.writes)
            assertEquals(PersistenceStatus.Conflict, missed.state.value.status)

            val racedBacking = MemoryProgressBacking(stored())
            val raced = SpyStore(racedBacking, silent = true)
            val racingOwner = owner(raced)
            val racedToken = assertIs<ReplacementPreparation.Ready>(racingOwner.beginRestore(candidate())).token
            raced.beforeWrite = { racedBacking.externalChange(external) }
            assertEquals(ReplacementResult.Conflict, racingOwner.confirmReplacement(racedToken))
            assertEquals(1, raced.writes)
            assertEquals(external, racedBacking.raw)
            assertEquals(PersistenceStatus.Conflict, racingOwner.state.value.status)
        }
    }

    @Test fun mutationRetryReloadCancelAndDisposalExpireAuthorizations() {
        val backing = MemoryProgressBacking(stored())
        val store = SpyStore(backing)
        val subject = owner(store)
        val first = assertIs<ReplacementPreparation.Ready>(subject.beginRestore(candidate())).token
        val second = assertIs<ReplacementPreparation.Ready>(subject.beginRestore(candidate())).token
        assertEquals(ReplacementResult.Stale, subject.confirmReplacement(first))
        assertTrue(subject.cancelReplacement(second))
        assertEquals(ReplacementResult.Stale, subject.confirmReplacement(second))
        val mutated = assertIs<ReplacementPreparation.Ready>(subject.beginRestore(candidate())).token
        subject.mutate { changePreferences(it, it.preferences.copy(showRomaji = true)) }
        assertEquals(ReplacementResult.Stale, subject.confirmReplacement(mutated))
        store.adapter.writeFailure = StoreFailure.QUOTA
        subject.mutate { changePreferences(it, it.preferences.copy(showReadings = false)) }
        store.adapter.writeFailure = null
        val retried = assertIs<ReplacementPreparation.Ready>(subject.beginRestore(candidate())).token
        assertEquals(PersistenceStatus.Saved, subject.retrySaving())
        assertEquals(ReplacementResult.Stale, subject.confirmReplacement(retried))
        val reloaded = assertIs<ReplacementPreparation.Ready>(subject.beginRestore(candidate())).token
        assertTrue(subject.reloadSavedState())
        assertEquals(ReplacementResult.Stale, subject.confirmReplacement(reloaded))
        val disposed = assertIs<ReplacementPreparation.Ready>(subject.beginRestore(candidate())).token
        subject.dispose()
        assertEquals(ReplacementResult.Stale, subject.confirmReplacement(disposed))
        assertEquals(ReplacementPreparation.Blocked, subject.beginRestore(candidate()))
    }
}
