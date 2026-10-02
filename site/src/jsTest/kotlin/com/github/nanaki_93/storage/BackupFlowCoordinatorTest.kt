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
}
