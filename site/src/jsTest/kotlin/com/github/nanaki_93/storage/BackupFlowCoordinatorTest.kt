package com.github.nanaki_93.storage

import com.github.nanaki_93.progress.BackupCodec
import com.github.nanaki_93.progress.BackupProblem
import com.github.nanaki_93.progress.SaveBounds
import com.github.nanaki_93.progress.LessonProgress
import com.github.nanaki_93.progress.LessonStage
import com.github.nanaki_93.progress.ReviewItemProgress
import com.github.nanaki_93.progress.ReviewOutcome
import com.github.nanaki_93.progress.SaveEnvelope
import com.github.nanaki_93.progress.SavePreferences
import com.github.nanaki_93.progress.SavedColorMode
import com.github.nanaki_93.progress.encodeSave
import com.github.nanaki_93.progress.changePreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupFlowCoordinatorTest {
    private data class FileHint(val size: Long = 1)
    private class FakePlatform : BackupFilePlatform {
        val reads = mutableListOf<(Result<ByteArray>) -> Unit>()
        val timers = mutableListOf<() -> Unit>()
        var aborted = 0
        var cleared = 0
        override fun byteSize(file: Any) = (file as FileHint).size
        override fun read(file: Any, completed: (Result<ByteArray>) -> Unit): BackupFileResource {
            reads.add(completed)
            return BackupFileResource { aborted++ }
        }
        override fun timeout(delayMs: Int, expired: () -> Unit): BackupFileResource {
            assertEquals(10_000, delayMs)
            timers.add(expired)
            return BackupFileResource { cleared++ }
        }
        fun complete(index: Int, text: String) = reads[index](Result.success(text.encodeToByteArray()))
    }

    private fun BackupFlowCoordinator(reader: BackupFileReader, newSnapshotId: () -> String = { "migration_id" }) =
        BackupFlowCoordinator(LocalProgressOwner(MemoryProgressStore(MemoryProgressBacking()), { 200 }, { "local_id" }), reader, newSnapshotId)

    private val saved = SaveEnvelope(savedAtEpochMs = 50, snapshotId = "source_id", revision = 3,
        preferences = SavePreferences(colorMode = SavedColorMode.DARK, showRomaji = true),
        lessonProgress = listOf(
            LessonProgress("unknown_lesson", 1, 42, LessonStage.SUMMARY, completedAtEpochMs = 42),
            LessonProgress("other_lesson", 1, 44, LessonStage.DIALOGUE),
        ),
        reviewItems = listOf(ReviewItemProgress("unknown_item", "unknown_doc", ReviewOutcome.GOOD, 45,
            dueAtEpochMs = 100, lastActionToken = "action_1")))
    private fun wrapped() = BackupCodec.encodeBackup(saved, "2.0+test", 90)

    @Test fun fullyValidatedWrappedAndBareFilesGiveMetadataWithoutCatalogGuesses() {
        val platform = FakePlatform()
        val flow = BackupFlowCoordinator(BackupFileReader(platform))
        val suspiciousName = "<img src=x onerror=alert(1)>" + "x".repeat(300)
        flow.select(FileHint(), suspiciousName)
        assertEquals(suspiciousName.take(255), assertIs<BackupFlowState.Reading>(flow.state.value).filename)
        platform.complete(0, wrapped())
        val details = assertIs<BackupFlowState.Preview>(flow.state.value).details
        assertEquals(suspiciousName.take(255), details.filename) // presentation data, never interpreted
        assertEquals(90L, details.exportedAtEpochMs)
        assertEquals(50L, details.snapshotAtEpochMs)
        assertEquals("2.0+test", details.appVersion)
        assertEquals(1, details.sourceSchemaVersion)
        assertNull(details.migratedFrom)
        assertEquals(saved.preferences, details.preferences)
        assertEquals(2, details.lessonCount)
        assertEquals(1, details.completedLessonCount)
        assertEquals(0, details.practiceCheckpointCount)
        assertEquals(1, details.reviewItemCount)
        flow.select(FileHint(), "bare.json")
        platform.complete(1, encodeSave(saved))
        val bare = assertIs<BackupFlowState.Preview>(flow.state.value).details
        assertNull(bare.exportedAtEpochMs)
        assertNull(bare.appVersion)
        assertEquals(saved.savedAtEpochMs, bare.snapshotAtEpochMs)
        assertEquals(2, platform.aborted)
        assertEquals(2, platform.cleared)
        flow.dispose()
    }

    @Test fun migratedFixturePreviewsSourceVersionWithoutInventingExportDate() {
        val platform = FakePlatform()
        val flow = BackupFlowCoordinator(BackupFileReader(platform), { "migrated_id" })
        flow.select(FileHint(), "old.json")
        platform.complete(0, """{"schemaVersion":0,"savedAtEpochMs":12,"preferences":{"colorMode":"dark"},"lessonProgress":[],"reviewItems":[]}""")
        val details = assertIs<BackupFlowState.Preview>(flow.state.value).details
        assertEquals(0, details.sourceSchemaVersion)
        assertEquals(0, details.migratedFrom)
        assertEquals(12L, details.snapshotAtEpochMs)
        assertNull(details.exportedAtEpochMs)
        assertNull(details.appVersion)
        assertEquals(SavedColorMode.DARK, details.preferences.colorMode)
        flow.dispose()
    }

    @Test fun timeoutMalformedAndReadErrorsNeverPreviewAndPermitSameFileRetry() {
        val platform = FakePlatform()
        val flow = BackupFlowCoordinator(BackupFileReader(platform))
        val file = FileHint()
        flow.select(file, "repeat.json")
        platform.timers[0]()
        assertEquals(BackupFlowError.Read(BackupReadError.TIMED_OUT),
            assertIs<BackupFlowState.Error>(flow.state.value).reason)
        platform.complete(0, wrapped()) // late callback
        assertIs<BackupFlowState.Error>(flow.state.value)
        flow.select(file, "repeat.json")
        platform.complete(1, "{not valid <script> private}")
        assertEquals(BackupFlowError.Validation(BackupProblem.MALFORMED_JSON),
            assertIs<BackupFlowState.Error>(flow.state.value).reason)
        flow.select(file, "repeat.json")
        platform.reads[2](Result.failure(IllegalStateException("private path")))
        assertEquals(BackupFlowError.Read(BackupReadError.FAILED),
            assertIs<BackupFlowState.Error>(flow.state.value).reason)
        flow.select(FileHint(0), "repeat.json")
        assertEquals(BackupFlowError.Read(BackupReadError.EMPTY),
            assertIs<BackupFlowState.Error>(flow.state.value).reason)
        flow.select(FileHint(), "repeat.json")
        platform.complete(3, wrapped())
        assertIs<BackupFlowState.Preview>(flow.state.value)
        assertEquals(4, platform.aborted)
        assertEquals(4, platform.cleared)
        flow.dispose()
    }

    @Test fun byteAndCompatibilityRejectionsHaveFixedCategories() {
        val platform = FakePlatform()
        val flow = BackupFlowCoordinator(BackupFileReader(platform))
        flow.select(FileHint(SaveBounds.MAX_JSON_BYTES.toLong() + 4097), "large")
        assertEquals(BackupFlowError.Read(BackupReadError.TOO_LARGE),
            assertIs<BackupFlowState.Error>(flow.state.value).reason)
        flow.select(FileHint(), "utf8")
        platform.reads[0](Result.success(byteArrayOf(0xc3.toByte(), 0x28)))
        assertEquals(BackupFlowError.Read(BackupReadError.INVALID_UTF8),
            assertIs<BackupFlowState.Error>(flow.state.value).reason)
        flow.select(FileHint(), "wrong app")
        platform.complete(1, wrapped().replace("\"appId\":\"hiragame\"", "\"appId\":\"other\""))
        assertEquals(BackupFlowError.Validation(BackupProblem.WRONG_APP),
            assertIs<BackupFlowState.Error>(flow.state.value).reason)
        flow.dispose()
    }

    @Test fun newSelectionCancelAndDisposeAbortAndSuppressOlderCallbacks() {
        val platform = FakePlatform()
        val flow = BackupFlowCoordinator(BackupFileReader(platform))
        flow.select(FileHint(), "first")
        flow.select(FileHint(), "second")
        assertEquals(1, platform.aborted)
        platform.complete(0, wrapped())
        assertEquals("second", assertIs<BackupFlowState.Reading>(flow.state.value).filename)
        platform.complete(1, wrapped())
        assertIs<BackupFlowState.Preview>(flow.state.value)
        flow.cancel() // picker cancellation also discards a preview
        assertEquals(BackupFlowState.Idle, flow.state.value)
        flow.select(FileHint(), "second")
        val late = platform.reads[2]
        flow.cancel()
        late(Result.success(wrapped().encodeToByteArray()))
        assertEquals(BackupFlowState.Idle, flow.state.value)
        flow.select(FileHint(), "second")
        flow.dispose()
        platform.complete(3, wrapped())
        assertEquals(BackupFlowState.Idle, flow.state.value)
        flow.select(FileHint(), "ignored")
        assertEquals(4, platform.reads.size)
        assertEquals(4, platform.aborted)
        assertEquals(4, platform.cleared)
    }

    @Test fun noReadOrErrorPathChangesLearnerStateOrStoredBytes() {
        val raw = encodeSave(saved)
        val backing = MemoryProgressBacking(raw)
        val owner = LocalProgressOwner(MemoryProgressStore(backing), { 200 }, { "local_id" })
        val before = owner.state.value
        val baseline = owner.savedBaseline
        val platform = FakePlatform()
        val flow = BackupFlowCoordinator(BackupFileReader(platform))
        flow.select(FileHint(), "bad")
        platform.complete(0, "null")
        assertIs<BackupFlowState.Error>(flow.state.value)
        flow.select(FileHint(), "good")
        platform.complete(1, wrapped())
        assertIs<BackupFlowState.Preview>(flow.state.value)
        flow.cancel()
        assertEquals(before, owner.state.value)
        assertEquals(raw, backing.raw)
        assertEquals(baseline, owner.savedBaseline)
        assertEquals(0L, owner.generation)
        assertTrue(flow.state.value == BackupFlowState.Idle)
        flow.dispose()
        owner.dispose()
    }

    private fun reviewed(owner: LocalProgressOwner, platform: FakePlatform): BackupFlowCoordinator =
        BackupFlowCoordinator(owner, BackupFileReader(platform)).also {
            it.select(FileHint(), "candidate.json")
            platform.complete(platform.reads.lastIndex, wrapped())
            assertIs<BackupFlowState.Preview>(it.state.value)
        }

    @Test fun reviewNeverWritesAndConfirmIsSeparateOneUse() {
        val backing = MemoryProgressBacking(encodeSave(saved.copy(snapshotId = "old_id")))
        val owner = LocalProgressOwner(MemoryProgressStore(backing), { 200 }, { "local_id" })
        val flow = reviewed(owner, FakePlatform())
        val original = backing.raw
        assertEquals(0L, owner.generation)
        assertEquals(original, backing.raw)
        flow.confirm() // preview alone cannot commit
        assertEquals(original, backing.raw)
        flow.requestConfirmation()
        assertIs<BackupFlowState.Confirming>(flow.state.value)
        assertEquals(original, backing.raw)
        flow.confirm()
        assertIs<BackupFlowState.Success>(flow.state.value)
        val committed = backing.raw
        flow.confirm()
        flow.requestConfirmation()
        assertEquals(committed, backing.raw)
        assertEquals(1L, owner.generation)
        flow.dispose()
        owner.dispose()
    }

    @Test fun cancellationSelectionAndDisposalRevokeAuthorization() {
        val backing = MemoryProgressBacking(encodeSave(saved.copy(snapshotId = "old_id")))
        val owner = LocalProgressOwner(MemoryProgressStore(backing), { 200 }, { "local_id" })
        val platform = FakePlatform()
        val flow = reviewed(owner, platform)
        flow.requestConfirmation()
        flow.cancel()
        flow.confirm()
        assertEquals(BackupFlowState.Idle, flow.state.value)
        assertEquals(0L, owner.generation)
        flow.select(FileHint(), "again")
        platform.complete(1, wrapped())
        flow.requestConfirmation()
        flow.select(FileHint(), "superseded")
        flow.confirm()
        platform.complete(2, wrapped())
        flow.requestConfirmation()
        flow.dispose()
        flow.confirm()
        assertEquals(0L, owner.generation)
        assertEquals(encodeSave(saved.copy(snapshotId = "old_id")), backing.raw)
        owner.dispose()
    }

    @Test fun mutationRetryReloadAndExternalChangeRequireRenewedReview() {
        for (mode in listOf("mutation", "retry", "reload", "external", "missed")) {
            val raw = encodeSave(saved.copy(snapshotId = "old_id"))
            val backing = MemoryProgressBacking(raw)
            val store = MemoryProgressStore(backing)
            val owner = LocalProgressOwner(if (mode == "missed") object : ProgressStore by store {
                override fun subscribe(onExternalChange: () -> Unit) = StoreSubscription {}
            } else store, { 200 }, { "local_id" })
            val flow = reviewed(owner, FakePlatform())
            if (mode != "mutation") flow.requestConfirmation()
            when (mode) {
                "mutation" -> owner.mutate { changePreferences(it, it.preferences.copy(showReadings = false)) }
                "retry" -> {
                    store.writeFailure = StoreFailure.QUOTA
                    owner.mutate { changePreferences(it, it.preferences.copy(showReadings = false)) }
                    store.writeFailure = null
                    owner.retrySaving()
                }
                "reload" -> owner.reloadSavedState()
                "external", "missed" -> backing.externalChange(null)
            }
            if (mode == "missed") {
                // Only the owner's final reread can detect an event that never arrived.
                flow.confirm()
                assertEquals(BackupConfirmationError.Replacement(ReplacementResult.Conflict),
                    assertIs<BackupFlowState.Failure>(flow.state.value).reason)
            } else {
                flow.ownerChanged()
                assertEquals(BackupConfirmationError.Expired,
                    assertIs<BackupFlowState.Failure>(flow.state.value).reason)
                flow.confirm()
            }
            assertEquals(0L, if (mode == "reload") owner.generation - 1 else owner.generation)
            if (mode == "external" || mode == "missed") assertNull(backing.raw)
            else assertTrue(backing.raw != null)
            flow.requestConfirmation() // failure is not a preview
            assertIs<BackupFlowState.Failure>(flow.state.value)
            flow.renewReview()
            assertIs<BackupFlowState.Preview>(flow.state.value)
            if (mode == "external" || mode == "missed") {
                flow.requestConfirmation()
                assertEquals(BackupConfirmationError.Blocked,
                    assertIs<BackupFlowState.Failure>(flow.state.value).reason)
            }
            flow.dispose()
            owner.dispose()
        }
    }

    @Test fun reconcilingUnknownStartupBaselineToMissingRequiresNewReviewAndConfirmation() {
        val backing = MemoryProgressBacking()
        val delegate = MemoryProgressStore(backing)
        var denyRead = true
        var reads = 0
        var writes = 0
        val store = object : ProgressStore by delegate {
            override fun read(): StoreReadResult {
                reads++
                return if (denyRead) StoreReadResult.Failure(StoreFailure.DENIED) else delegate.read()
            }
            override fun write(expectedRaw: String?, replacementRaw: String): StoreWriteResult {
                writes++
                return delegate.write(expectedRaw, replacementRaw)
            }
        }
        var nextId = 0
        val owner = LocalProgressOwner(store, { 200 }, { "local_${++nextId}" })
        val flow = reviewed(owner, FakePlatform())
        val before = owner.state.value
        assertEquals(PersistenceStatus.MemoryOnly(StoreFailure.DENIED), before.status)
        assertNull(owner.savedBaseline)
        assertEquals(false, owner.hasKnownBaseline)
        assertEquals(1, reads)
        denyRead = false

        flow.requestConfirmation() // beginRestore reconciles missing, but cannot authorize the old review
        assertEquals(BackupConfirmationError.Expired,
            assertIs<BackupFlowState.Failure>(flow.state.value).reason)
        assertEquals(true, owner.hasKnownBaseline)
        assertEquals(before, owner.state.value) // state, raw and generation alone would miss this change
        assertNull(owner.savedBaseline)
        assertNull(backing.raw)
        assertEquals(0L, owner.generation)
        assertEquals(2, reads)
        flow.confirm()
        flow.requestConfirmation() // cannot turn a failure directly into a confirmation
        assertEquals(0, writes)
        assertIs<BackupFlowState.Failure>(flow.state.value)

        flow.renewReview()
        assertIs<BackupFlowState.Preview>(flow.state.value)
        flow.requestConfirmation()
        assertIs<BackupFlowState.Confirming>(flow.state.value)
        assertEquals(0, writes)
        flow.confirm()
        assertIs<BackupFlowState.Success>(flow.state.value)
        assertEquals(1, writes)
        assertEquals(1L, owner.generation)
        flow.confirm()
        assertEquals(1, writes)
        flow.dispose()
        owner.dispose()
    }

    @Test fun failedWriteKeepsCandidateInactiveAndRequiresFreshConfirmation() {
        val raw = encodeSave(saved.copy(snapshotId = "old_id"))
        val backing = MemoryProgressBacking(raw)
        val store = MemoryProgressStore(backing).apply { writeFailure = StoreFailure.QUOTA }
        val owner = LocalProgressOwner(store, { 200 }, { "local_id" })
        val before = owner.state.value
        val flow = reviewed(owner, FakePlatform())
        flow.requestConfirmation()
        flow.confirm()
        assertEquals(BackupConfirmationError.Replacement(ReplacementResult.Failure(StoreFailure.QUOTA)),
            assertIs<BackupFlowState.Failure>(flow.state.value).reason)
        flow.confirm()
        assertEquals(before, owner.state.value)
        assertEquals(raw, backing.raw)
        assertEquals(0L, owner.generation)
        store.writeFailure = null
        flow.renewReview()
        flow.requestConfirmation()
        flow.confirm()
        assertIs<BackupFlowState.Success>(flow.state.value)
        flow.dispose()
        owner.dispose()
    }
}
