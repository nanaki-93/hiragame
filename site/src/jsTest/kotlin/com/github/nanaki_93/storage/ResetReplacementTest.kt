package com.github.nanaki_93.storage

import com.github.nanaki_93.progress.CheckpointExerciseType
import com.github.nanaki_93.progress.CheckpointView
import com.github.nanaki_93.progress.CompactOutcome
import com.github.nanaki_93.progress.LessonProgress
import com.github.nanaki_93.progress.LessonStage
import com.github.nanaki_93.progress.PracticeCheckpoint
import com.github.nanaki_93.progress.ResetScope
import com.github.nanaki_93.progress.ReviewItemProgress
import com.github.nanaki_93.progress.ReviewOutcome
import com.github.nanaki_93.progress.SaveBounds
import com.github.nanaki_93.progress.SaveCodec
import com.github.nanaki_93.progress.SaveDecodeResult
import com.github.nanaki_93.progress.SaveEnvelope
import com.github.nanaki_93.progress.SavePreferences
import com.github.nanaki_93.progress.SaveProblem
import com.github.nanaki_93.progress.SavedColorMode
import com.github.nanaki_93.progress.changePreferences
import com.github.nanaki_93.progress.encodeSave
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResetReplacementTest {
    private val preferences = SavePreferences(colorMode = SavedColorMode.DARK, showRomaji = true, showReadings = false)
    private fun original(revision: Long = 6) = SaveEnvelope(
        savedAtEpochMs = 10, snapshotId = "old_id", revision = revision,
        preferences = preferences,
        lessonProgress = listOf(LessonProgress("unknown_lesson", 2, 9, LessonStage.SUMMARY, completedAtEpochMs = 9)),
        practiceProgress = listOf(PracticeCheckpoint(
            setId = "unknown_set", contentVersion = 2, updatedAtEpochMs = 9,
            runToken = "run_1", lastTransitionToken = "transition_1",
            exerciseIds = listOf("unknown_exercise"), outcomes = listOf(CompactOutcome.CORRECT),
            frontier = 1, view = CheckpointView.COMPLETE, lastCompletedAtEpochMs = 9,
            exerciseTypes = listOf(CheckpointExerciseType.CHOICE),
        )),
        reviewItems = listOf(ReviewItemProgress(
            itemId = "unknown_item", documentId = "unknown_document", outcome = ReviewOutcome.GOOD,
            lastReviewedAtEpochMs = 9, dueAtEpochMs = 20, intervalMs = 11,
            step = 2, repetitions = 3, lapses = 0, lastActionToken = "action_1",
        )),
    )
    private class SpyStore(raw: String?) : ProgressStore {
        val backing = MemoryProgressBacking(raw)
        val delegate = MemoryProgressStore(backing)
        var reads = 0
        var writes = 0
        var expected: String? = null
        var beforeWrite: (() -> Unit)? = null
        override fun read(): StoreReadResult { reads++; return delegate.read() }
        override fun write(expectedRaw: String?, replacementRaw: String): StoreWriteResult {
            writes++
            expected = expectedRaw
            beforeWrite?.invoke()
            return delegate.write(expectedRaw, replacementRaw)
        }
        override fun subscribe(onExternalChange: () -> Unit) = delegate.subscribe(onExternalChange)
    }
    private fun owner(store: ProgressStore, time: () -> Long = { 200L }, id: (() -> String)? = null): LocalProgressOwner {
        var next = 0
        return LocalProgressOwner(store, time, id ?: { "new_local_id_${++next}" })
    }
    private fun token(owner: LocalProgressOwner, scope: ResetScope) =
        assertIs<ReplacementPreparation.Ready>(owner.beginReset(scope)).token

    @Test fun bothScopesWriteOneCompleteSnapshotAndPreserveOtherStorage() {
        for (scope in ResetScope.entries) {
            val raw = encodeSave(original())
            val store = SpyStore(raw)
            // These represent unrelated and legacy keys, which a reset must never touch.
            val otherKeys = mapOf("legacy-color" to "light", "unrelated" to "keep", "offline-asset" to "cached")
            val subject = owner(store)
            val previous = subject.state.value
            val generation = subject.generation
            var notifications = 0
            subject.observeGeneration { notifications++ }
            store.beforeWrite = {
                assertEquals(previous, subject.state.value)
                assertEquals(generation, subject.generation)
                assertEquals(raw, store.backing.raw)
            }
            val authorized = token(subject, scope)
            assertEquals(ReplacementResult.Replaced, subject.confirmReplacement(authorized))
            assertEquals(ReplacementResult.Stale, subject.confirmReplacement(authorized))
            assertEquals(1, store.writes)
            assertEquals(raw, store.expected)
            val saved = assertIs<SaveDecodeResult.Valid>(SaveCodec.decodeSave(store.backing.raw!!)).snapshot
            assertEquals(saved, subject.state.value.snapshot)
            assertEquals(PersistenceStatus.Saved, subject.state.value.status)
            assertEquals(store.backing.raw, subject.savedBaseline)
            assertEquals("new_local_id_1", saved.snapshotId)
            assertEquals(200L, saved.savedAtEpochMs)
            assertEquals(7L, saved.revision)
            assertTrue(saved.lessonProgress.isEmpty())
            assertTrue(saved.practiceProgress.isEmpty())
            assertTrue(saved.reviewItems.isEmpty())
            assertEquals(if (scope == ResetScope.PROGRESS_ONLY) preferences else SavePreferences(), saved.preferences)
            assertEquals(mapOf("legacy-color" to "light", "unrelated" to "keep", "offline-asset" to "cached"), otherKeys)
            assertEquals(generation + 1, subject.generation)
            assertEquals(1, notifications) // practice session invalidation only after commit
            assertEquals(PersistenceStatus.Saved, subject.retrySaving())
            assertEquals(1, store.writes)
            val reopened = owner(MemoryProgressStore(store.backing))
            assertEquals(saved, reopened.state.value.snapshot)
            assertEquals(PersistenceStatus.Saved, reopened.state.value.status)
            reopened.dispose()
            subject.dispose()
        }
    }

    @Test fun freshMemoryOnlyAndProtectedEligibilityAndUnknownBaselineReconciliation() {
        val freshStore = SpyStore(null)
        val fresh = owner(freshStore)
        assertEquals(ReplacementResult.Replaced, fresh.confirmReplacement(token(fresh, ResetScope.PROGRESS_ONLY)))
        assertNull(freshStore.expected)
        assertEquals(1L, fresh.state.value.snapshot.revision)

        val memoryStore = SpyStore(encodeSave(original()))
        val memory = owner(memoryStore)
        memoryStore.delegate.writeFailure = StoreFailure.QUOTA
        memory.mutate { changePreferences(it, it.preferences.copy(showRomaji = false)) }
        assertIs<PersistenceStatus.MemoryOnly>(memory.state.value.status)
        memoryStore.delegate.writeFailure = null
        assertEquals(ReplacementResult.Replaced, memory.confirmReplacement(token(memory, ResetScope.PROGRESS_ONLY)))
        assertFalse(memory.state.value.snapshot.preferences.showRomaji)
        assertEquals(7L, memory.state.value.snapshot.revision)

        val protectedStore = SpyStore("unreadable original")
        val protected = owner(protectedStore)
        assertEquals(ReplacementPreparation.Blocked, protected.beginReset(ResetScope.PROGRESS_ONLY))
        assertEquals(0, protectedStore.writes)
        assertEquals("unreadable original", protected.originalProtectedRaw)
        val full = token(protected, ResetScope.FULL_LEARNER_STATE)
        assertEquals(ReplacementResult.Replaced, protected.confirmReplacement(full))
        assertEquals("unreadable original", protectedStore.expected)
        assertEquals(0L, protected.state.value.snapshot.revision)
        assertEquals(SavePreferences(), protected.state.value.snapshot.preferences)
        assertNull(protected.originalProtectedRaw)

        for (existing in listOf(false, true)) {
            val store = SpyStore(if (existing) encodeSave(original()) else null)
            store.delegate.readFailure = StoreFailure.DENIED
            val subject = owner(store)
            assertEquals(ReplacementPreparation.Blocked, subject.beginReset(ResetScope.FULL_LEARNER_STATE))
            store.delegate.readFailure = null
            if (existing) {
                assertEquals(ReplacementPreparation.Blocked, subject.beginReset(ResetScope.FULL_LEARNER_STATE))
                assertEquals(PersistenceStatus.Conflict, subject.state.value.status)
                assertEquals(ReplacementPreparation.Blocked, subject.beginReset(ResetScope.PROGRESS_ONLY))
                assertEquals(0, store.writes)
                assertTrue(subject.reloadSavedState())
            }
            assertEquals(ReplacementResult.Replaced, subject.confirmReplacement(token(subject, ResetScope.PROGRESS_ONLY)))
            assertEquals(1, store.writes)
        }
    }

    @Test fun cancellationSupersedingMutationRetryReloadAndDisposalInvalidateTokens() {
        val store = SpyStore(encodeSave(original()))
        val subject = owner(store)
        val first = token(subject, ResetScope.FULL_LEARNER_STATE)
        val second = token(subject, ResetScope.PROGRESS_ONLY)
        assertEquals(ReplacementResult.Stale, subject.confirmReplacement(first))
        assertTrue(subject.cancelReplacement(second))
        assertFalse(subject.cancelReplacement(second))
        assertEquals(ReplacementResult.Stale, subject.confirmReplacement(second))
        val changed = token(subject, ResetScope.PROGRESS_ONLY)
        subject.mutate { changePreferences(it, it.preferences.copy(showRomaji = false)) }
        assertEquals(ReplacementResult.Stale, subject.confirmReplacement(changed))
        store.delegate.writeFailure = StoreFailure.QUOTA
        subject.mutate { changePreferences(it, it.preferences.copy(showReadings = true)) }
        store.delegate.writeFailure = null
        val retried = token(subject, ResetScope.FULL_LEARNER_STATE)
        assertEquals(PersistenceStatus.Saved, subject.retrySaving())
        assertEquals(ReplacementResult.Stale, subject.confirmReplacement(retried))
        val reloaded = token(subject, ResetScope.FULL_LEARNER_STATE)
        assertTrue(subject.reloadSavedState())
        assertEquals(ReplacementResult.Stale, subject.confirmReplacement(reloaded))
        val disposed = token(subject, ResetScope.FULL_LEARNER_STATE)
        val writes = store.writes
        subject.dispose()
        assertEquals(ReplacementResult.Stale, subject.confirmReplacement(disposed))
        assertEquals(ReplacementPreparation.Blocked, subject.beginReset(ResetScope.FULL_LEARNER_STATE))
        assertEquals(writes, store.writes)
    }

    @Test fun readWriteAndMetadataFailuresKeepPayloadBaselineAndGeneration() {
        for (reason in StoreFailure.entries) for (readFailure in listOf(true, false)) {
            val raw = encodeSave(original())
            val store = SpyStore(raw)
            val subject = owner(store)
            val before = subject.state.value
            val generation = subject.generation
            var notifications = 0
            subject.observeGeneration { notifications++ }
            if (readFailure) store.delegate.readFailure = reason else store.delegate.writeFailure = reason
            val authorization = token(subject, ResetScope.FULL_LEARNER_STATE)
            assertEquals(ReplacementResult.Failure(reason), subject.confirmReplacement(authorization))
            assertEquals(ReplacementResult.Stale, subject.confirmReplacement(authorization))
            assertEquals(if (readFailure) 0 else 1, store.writes)
            assertEquals(before, subject.state.value)
            assertEquals(raw, store.backing.raw)
            assertEquals(raw, subject.savedBaseline)
            assertEquals(generation, subject.generation)
            assertEquals(0, notifications)
            assertEquals(PersistenceStatus.Saved, subject.retrySaving())
            assertEquals(if (readFailure) 0 else 1, store.writes) // no destructive retry
        }
        for (revision in listOf(6L, SaveBounds.MAX_SAFE_INTEGER)) for (bad in listOf("clock", "id", "duplicate", "throwClock", "throwId")) {
            val raw = encodeSave(original(revision))
            val store = SpyStore(raw)
            var fail = false
            val subject = owner(store,
                { if (fail && bad == "throwClock") error("clock unavailable") else if (fail && bad == "clock") -1L else 200L },
                { if (fail && bad == "throwId") error("identity unavailable") else if (fail && bad == "id") "bad id" else if (fail && bad == "duplicate") "old_id" else "new_local_id" })
            val before = subject.state.value
            val generation = subject.generation
            val authorization = token(subject, ResetScope.PROGRESS_ONLY)
            val reads = store.reads
            fail = true
            assertEquals(ReplacementResult.InvalidReplacement(SaveProblem.INVALID_SNAPSHOT), subject.confirmReplacement(authorization))
            assertEquals(ReplacementResult.Stale, subject.confirmReplacement(authorization))
            assertEquals(reads, store.reads)
            assertEquals(0, store.writes)
            assertEquals(raw, store.backing.raw)
            assertEquals(raw, subject.savedBaseline)
            assertEquals(before, subject.state.value)
            assertEquals(generation, subject.generation)
        }
    }

    @Test fun protectedFullResetFailureRetainsRecoveryAndProgressOnlyRequestExpiresFullToken() {
        val store = SpyStore("unreadable original")
        val subject = owner(store)
        subject.mutate { changePreferences(it, it.preferences.copy(colorMode = SavedColorMode.DARK)) }
        val active = subject.state.value
        val generation = subject.generation
        var notifications = 0
        subject.observeGeneration { notifications++ }
        val full = token(subject, ResetScope.FULL_LEARNER_STATE)
        assertEquals(ReplacementPreparation.Blocked, subject.beginReset(ResetScope.PROGRESS_ONLY))
        assertEquals(ReplacementResult.Stale, subject.confirmReplacement(full))
        store.delegate.writeFailure = StoreFailure.QUOTA
        val attempted = token(subject, ResetScope.FULL_LEARNER_STATE)
        assertEquals(ReplacementResult.Failure(StoreFailure.QUOTA), subject.confirmReplacement(attempted))
        assertEquals(ReplacementResult.Stale, subject.confirmReplacement(attempted))
        assertEquals(active, subject.state.value)
        assertEquals("unreadable original", store.backing.raw)
        assertEquals("unreadable original", subject.savedBaseline)
        assertEquals("unreadable original", subject.originalProtectedRaw)
        assertEquals(generation, subject.generation)
        assertEquals(0, notifications)
        assertEquals(active.status, subject.retrySaving())
        assertEquals(1, store.writes)
    }

    @Test fun externalEventsMissedWritesAndAdapterRacesCannotOverwrite() {
        for (external in listOf<String?>(null, "external bytes")) {
            val raw = encodeSave(original())
            val store = SpyStore(raw)
            val subject = owner(store)
            val authorization = token(subject, ResetScope.FULL_LEARNER_STATE)
            store.backing.externalChange(external)
            assertEquals(ReplacementResult.Stale, subject.confirmReplacement(authorization))
            assertEquals(PersistenceStatus.Conflict, subject.state.value.status)
            assertEquals(0, store.writes)
            assertEquals(external, store.backing.raw)

            val missedStore = SpyStore(raw)
            val silent = object : ProgressStore by missedStore {
                override fun subscribe(onExternalChange: () -> Unit) = StoreSubscription {}
            }
            val missed = owner(silent)
            val missedToken = token(missed, ResetScope.PROGRESS_ONLY)
            missedStore.backing.externalChange(external)
            assertEquals(ReplacementResult.Conflict, missed.confirmReplacement(missedToken))
            assertEquals(0, missedStore.writes)
            assertEquals(PersistenceStatus.Conflict, missed.state.value.status)

            val racedStore = SpyStore(raw)
            val raced = owner(racedStore)
            val racedToken = token(raced, ResetScope.FULL_LEARNER_STATE)
            racedStore.beforeWrite = { racedStore.backing.externalChange(external) }
            assertEquals(ReplacementResult.Conflict, raced.confirmReplacement(racedToken))
            assertEquals(1, racedStore.writes)
            assertEquals(external, racedStore.backing.raw)
            assertEquals(PersistenceStatus.Conflict, raced.state.value.status)
        }
    }
}
