package com.github.nanaki_93.storage

import com.github.nanaki_93.progress.ProgressUpdate
import com.github.nanaki_93.progress.SaveBounds
import com.github.nanaki_93.progress.SaveCodec
import com.github.nanaki_93.progress.SaveDecodeResult
import com.github.nanaki_93.progress.SaveEnvelope
import com.github.nanaki_93.progress.SavePreferences
import com.github.nanaki_93.progress.SaveProblem
import com.github.nanaki_93.progress.SavedColorMode
import com.github.nanaki_93.progress.changePreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class LocalProgressOwnerTest {
    private class CountingStore(private val delegate: MemoryProgressStore) : ProgressStore by delegate {
        var reads = 0
        var writes = 0
        override fun read(): StoreReadResult { reads++; return delegate.read() }
        override fun write(expectedRaw: String?, replacementRaw: String): StoreWriteResult {
            writes++
            return delegate.write(expectedRaw, replacementRaw)
        }
    }

    private fun owner(store: ProgressStore): LocalProgressOwner {
        var id = 0
        return LocalProgressOwner(store, { 100L + id }, { "id_${++id}" })
    }

    private fun dark(snapshot: SaveEnvelope) =
        changePreferences(snapshot, snapshot.preferences.copy(colorMode = SavedColorMode.DARK))

    @Test fun freshDoesNotWriteAndOneReadLoadsSavedEnvelope() {
        val backing = MemoryProgressBacking()
        val store = CountingStore(MemoryProgressStore(backing))
        val first = owner(store)
        assertEquals(1, store.reads)
        assertEquals(0, store.writes)
        assertEquals(PersistenceStatus.Fresh, first.state.value.status)
        assertNull(first.savedBaseline)
        assertEquals(ProgressMutationResult.Unchanged, first.mutate {
            changePreferences(it, SavePreferences())
        })
        assertEquals(0, store.writes)
        assertEquals(ProgressMutationResult.Accepted, first.mutate(::dark))
        assertEquals(1, store.writes)
        assertEquals(1L, first.state.value.snapshot.revision)
        assertEquals(PersistenceStatus.Saved, first.state.value.status)
        assertEquals(backing.raw, first.savedBaseline)
        val secondStore = CountingStore(MemoryProgressStore(backing))
        val second = owner(secondStore)
        assertEquals(1, secondStore.reads)
        assertEquals(0, secondStore.writes)
        assertEquals(first.state.value.snapshot, second.state.value.snapshot)
        assertEquals(PersistenceStatus.Saved, second.state.value.status)
    }

    @Test fun protectedOriginalIsNeverAutomaticallyReplacedEvenWhenLearningContinues() {
        for (raw in listOf("not json", """{"schemaVersion":99}""")) {
            val backing = MemoryProgressBacking(raw)
            val store = CountingStore(MemoryProgressStore(backing))
            val subject = owner(store)
            assertIs<PersistenceStatus.Protected>(subject.state.value.status)
            assertEquals(raw, subject.originalProtectedRaw)
            assertEquals(raw, subject.savedBaseline)
            assertEquals(ProgressMutationResult.Accepted, subject.mutate(::dark))
            assertEquals(SavedColorMode.DARK, subject.state.value.snapshot.preferences.colorMode)
            assertIs<PersistenceStatus.Protected>(subject.state.value.status)
            assertEquals(0, store.writes)
            assertEquals(raw, backing.raw)
            assertIs<PersistenceStatus.Protected>(subject.retrySaving())
        }
    }

    @Test fun failuresPauseWritesButRetryPersistsLatestMemoryOnce() {
        for (reason in StoreFailure.entries) {
            val backing = MemoryProgressBacking()
            val adapter = MemoryProgressStore(backing)
            val store = CountingStore(adapter)
            val subject = owner(store)
            adapter.writeFailure = reason
            assertEquals(ProgressMutationResult.Accepted, subject.mutate(::dark))
            assertEquals(PersistenceStatus.MemoryOnly(reason), subject.state.value.status)
            assertEquals(0L, subject.state.value.snapshot.revision)
            assertNull(backing.raw)
            val latest = subject.state.value.snapshot.preferences.copy(showReadings = false)
            assertEquals(ProgressMutationResult.Accepted, subject.mutate { changePreferences(it, latest) })
            assertEquals(1, store.writes) // no repeated automatic attempts
            assertFalse(subject.state.value.snapshot.preferences.showReadings)
            adapter.writeFailure = null
            assertEquals(PersistenceStatus.Saved, subject.retrySaving())
            assertEquals(2, store.writes)
            assertEquals(1L, subject.state.value.snapshot.revision)
            assertEquals(subject.state.value.snapshot, (SaveCodec.decodeSave(backing.raw!!) as SaveDecodeResult.Valid).snapshot)
            assertEquals(PersistenceStatus.Saved, subject.retrySaving())
            assertEquals(2, store.writes)
        }
    }

    @Test fun deniedStartupCannotOverwriteUnknownOriginalAndCanRetryWhenMissing() {
        val backing = MemoryProgressBacking("original")
        val adapter = MemoryProgressStore(backing)
        adapter.readFailure = StoreFailure.DENIED
        val store = CountingStore(adapter)
        val subject = owner(store)
        assertEquals(PersistenceStatus.MemoryOnly(StoreFailure.DENIED), subject.state.value.status)
        assertNull(subject.originalProtectedRaw)
        assertNull(subject.savedBaseline)
        subject.mutate(::dark)
        adapter.readFailure = null
        assertEquals(PersistenceStatus.Conflict, subject.retrySaving())
        assertEquals("original", backing.raw)
        assertEquals(0, store.writes)

        val absent = MemoryProgressBacking()
        val otherAdapter = MemoryProgressStore(absent).apply { readFailure = StoreFailure.DENIED }
        val otherStore = CountingStore(otherAdapter)
        val other = owner(otherStore)
        other.mutate(::dark)
        otherAdapter.readFailure = null
        assertEquals(PersistenceStatus.Saved, other.retrySaving())
        assertEquals(2, otherStore.reads)
        assertEquals(1, otherStore.writes)
    }

    @Test fun limitAndInvalidMutationsRetainLastValidStateWithoutWriting() {
        val backing = MemoryProgressBacking()
        val store = CountingStore(MemoryProgressStore(backing))
        val subject = owner(store)
        subject.mutate(::dark)
        val saved = subject.state.value.snapshot
        val raw = backing.raw
        assertEquals(ProgressMutationResult.Rejected(SaveProblem.INVALID_SNAPSHOT), subject.mutate {
            ProgressUpdate.Applied(it.copy(snapshotId = "forged"))
        })
        assertEquals(ProgressMutationResult.Rejected(SaveProblem.INVALID_SNAPSHOT), subject.mutate {
            ProgressUpdate.Rejected(it)
        })
        assertEquals(ProgressMutationResult.Rejected(SaveProblem.INVALID_SNAPSHOT), subject.mutate {
            ProgressUpdate.Applied(it.copy(lessonProgress = it.lessonProgress +
                com.github.nanaki_93.progress.LessonProgress("bad id", 1, 10, com.github.nanaki_93.progress.LessonStage.SUMMARY)))
        })
        assertEquals(saved, subject.state.value.snapshot)
        assertEquals(SaveProblem.INVALID_SNAPSHOT, subject.state.value.rejectedUpdate)
        assertEquals(raw, backing.raw)
        assertEquals(1, store.writes)

        // Wire size is checked before either memory publication or a storage write.
        val huge = (1..SaveBounds.MAX_REVIEW_RECORDS).map {
            com.github.nanaki_93.progress.ReviewItemProgress(
                "item_${it}_" + "i".repeat(110), "d".repeat(128),
                com.github.nanaki_93.progress.ReviewOutcome.GOOD, 1,
                lastActionToken = "t".repeat(128),
            )
        }
        val oversized = subject.mutate { ProgressUpdate.Applied(it.copy(reviewItems = huge)) }
        assertEquals(ProgressMutationResult.Rejected(SaveProblem.OVERSIZED), oversized)
        assertEquals(saved, subject.state.value.snapshot)
        assertEquals(raw, backing.raw)
    }

    @Test fun retryChecksOriginalRawAndNeverOverwritesAnExternalChange() {
        val original = com.github.nanaki_93.progress.encodeSave(
            SaveEnvelope(savedAtEpochMs = 1, snapshotId = "original", revision = 0),
        )
        val backing = MemoryProgressBacking(original)
        val adapter = MemoryProgressStore(backing).apply { writeFailure = StoreFailure.QUOTA }
        val store = CountingStore(adapter)
        val subject = owner(store)
        subject.mutate(::dark)
        assertEquals(original, subject.savedBaseline)
        assertEquals(original, backing.raw)
        assertEquals(0L, subject.state.value.snapshot.revision)
        adapter.writeFailure = null
        backing.externalChange("replacement from elsewhere")
        assertEquals(PersistenceStatus.Conflict, subject.state.value.status)
        assertEquals(PersistenceStatus.Conflict, subject.retrySaving())
        assertEquals("replacement from elsewhere", backing.raw)
        assertEquals(original, subject.savedBaseline)
        assertEquals(SavedColorMode.DARK, subject.state.value.snapshot.preferences.colorMode)
        assertEquals(1, store.writes)
        assertEquals(PersistenceStatus.Conflict, subject.retrySaving())
        assertEquals(1, store.writes)
    }

    @Test fun failedRetryRemainsPausedAndLaterRetryDoesNotReplayActions() {
        val backing = MemoryProgressBacking()
        val adapter = MemoryProgressStore(backing).apply { writeFailure = StoreFailure.DENIED }
        val store = CountingStore(adapter)
        val subject = owner(store)
        subject.mutate(::dark)
        adapter.writeFailure = StoreFailure.QUOTA
        assertEquals(PersistenceStatus.MemoryOnly(StoreFailure.QUOTA), subject.retrySaving())
        assertEquals(2, store.writes)
        assertEquals(0L, subject.state.value.snapshot.revision)
        assertNull(backing.raw)
        adapter.writeFailure = null
        assertEquals(PersistenceStatus.Saved, subject.retrySaving())
        assertEquals(3, store.writes)
        assertEquals(1L, subject.state.value.snapshot.revision)
    }

    @Test fun versionZeroMigrationRemainsInMemoryUntilAnExplicitMutation() {
        val raw = """{"schemaVersion":0,"savedAtEpochMs":7,"preferences":{"colorMode":"light"},"lessonProgress":[],"reviewItems":[]}"""
        val backing = MemoryProgressBacking(raw)
        val store = CountingStore(MemoryProgressStore(backing))
        val subject = owner(store)
        assertEquals(PersistenceStatus.Saved, subject.state.value.status)
        assertEquals(0L, subject.state.value.snapshot.revision)
        assertEquals(raw, backing.raw)
        assertEquals(0, store.writes)
        subject.mutate(::dark)
        assertEquals(1L, subject.state.value.snapshot.revision)
        assertEquals(1, store.writes)
        assertEquals(1, (SaveCodec.decodeSave(backing.raw!!) as SaveDecodeResult.Valid).snapshot.schemaVersion)
    }
}
