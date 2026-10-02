package com.github.nanaki_93.storage

import com.github.nanaki_93.progress.SaveBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BackupFileReaderTest {
    private class FakeFile(val size: Long, val name: String = "untrusted.exe", val type: String = "application/octet-stream")

    private class FakePlatform : BackupFilePlatform {
        var reads = 0
        var timers = 0
        var closedReads = 0
        var closedTimers = 0
        var delay = 0
        var failStart = false
        var failSize = false
        var immediateBytes: ByteArray? = null
        var immediateTimeout = false
        var readCallback: ((Result<ByteArray>) -> Unit)? = null
        var timerCallback: (() -> Unit)? = null
        override fun byteSize(file: Any): Long? {
            if (failSize) throw IllegalStateException("size denied")
            return (file as FakeFile).size
        }
        override fun read(file: Any, completed: (Result<ByteArray>) -> Unit): BackupFileResource {
            reads++
            if (failStart) throw IllegalStateException("read denied")
            readCallback = completed
            immediateBytes?.let { completed(Result.success(it)) }
            return BackupFileResource { closedReads++ }
        }
        override fun timeout(delayMs: Int, expired: () -> Unit): BackupFileResource {
            timers++
            delay = delayMs
            timerCallback = expired
            if (immediateTimeout) expired()
            return BackupFileResource { closedTimers++ }
        }
    }

    @Test fun checksHintBeforeReadAndActualBytesAfterReadWithoutTrustingNameOrType() {
        val platform = FakePlatform()
        val reader = BackupFileReader(platform)
        val results = mutableListOf<BackupReadResult>()
        reader.read(FakeFile(0), results::add)
        reader.read(FakeFile((SaveBounds.MAX_JSON_BYTES + 4097).toLong()), results::add)
        platform.failSize = true
        reader.read(FakeFile(2), results::add)
        platform.failSize = false
        assertEquals(listOf(BackupReadResult.Empty, BackupReadResult.TooLarge, BackupReadResult.Failed), results)
        assertEquals(0, platform.reads)
        val file = FakeFile(1, "state.txt", "text/plain")
        reader.read(file, results::add)
        platform.readCallback!!(Result.success("日本語".encodeToByteArray()))
        assertEquals(BackupReadResult.Text("日本語"), results.last())
        assertEquals(1, platform.closedReads)
        assertEquals(1, platform.closedTimers)
        reader.read(file, results::add) // same file can be chosen again
        platform.readCallback!!(Result.success(ByteArray(SaveBounds.MAX_JSON_BYTES + 4097)))
        assertEquals(BackupReadResult.TooLarge, results.last())
        assertEquals(2, platform.closedReads)
        assertEquals(2, platform.closedTimers)
    }

    @Test fun invalidUtf8AndEmptyActualReadAreRejectedAndResourcesReleased() {
        val platform = FakePlatform()
        val reader = BackupFileReader(platform)
        val results = mutableListOf<BackupReadResult>()
        reader.read(FakeFile(4), results::add)
        platform.readCallback!!(Result.success(byteArrayOf(0xc3.toByte(), 0x28)))
        assertEquals(BackupReadResult.InvalidUtf8, results.single())
        reader.read(FakeFile(4), results::add)
        platform.readCallback!!(Result.success(byteArrayOf()))
        assertEquals(BackupReadResult.Empty, results.last())
        assertEquals(2, platform.closedReads)
        assertEquals(2, platform.closedTimers)
    }

    @Test fun timeoutEndsReadAndAllowsRetryWhileLateCallbacksAreIgnored() {
        val platform = FakePlatform()
        val reader = BackupFileReader(platform)
        val results = mutableListOf<BackupReadResult>()
        reader.read(FakeFile(2), results::add)
        assertEquals(10_000, platform.delay)
        val late = platform.readCallback!!
        platform.timerCallback!!()
        assertEquals(listOf<BackupReadResult>(BackupReadResult.TimedOut), results)
        assertEquals(1, platform.closedReads)
        assertEquals(1, platform.closedTimers)
        late(Result.success("old".encodeToByteArray()))
        reader.read(FakeFile(3), results::add)
        platform.readCallback!!(Result.success("new".encodeToByteArray()))
        assertEquals(listOf<BackupReadResult>(BackupReadResult.TimedOut, BackupReadResult.Text("new")), results)
    }

    @Test fun synchronousPlatformCallbacksAlsoReleaseReturnedHandles() {
        val platform = FakePlatform()
        val reader = BackupFileReader(platform)
        val results = mutableListOf<BackupReadResult>()
        platform.immediateTimeout = true
        reader.read(FakeFile(2), results::add)
        assertEquals(listOf<BackupReadResult>(BackupReadResult.TimedOut), results)
        assertEquals(0, platform.reads)
        assertEquals(1, platform.closedTimers)
        platform.immediateTimeout = false
        platform.immediateBytes = "ok".encodeToByteArray()
        reader.read(FakeFile(2), results::add)
        assertEquals(BackupReadResult.Text("ok"), results.last())
        assertEquals(1, platform.closedReads)
        assertEquals(2, platform.closedTimers)
        reader.dispose()
    }

    @Test fun errorsCancellationAndDisposalNeverDeliverLateResults() {
        val platform = FakePlatform()
        val reader = BackupFileReader(platform, 42)
        val results = mutableListOf<BackupReadResult>()
        platform.failStart = true
        reader.read(FakeFile(2), results::add)
        assertEquals(listOf<BackupReadResult>(BackupReadResult.Failed), results)
        assertEquals(1, platform.closedTimers)
        platform.failStart = false
        reader.read(FakeFile(2), results::add)
        platform.readCallback!!(Result.failure(IllegalStateException("private file path")))
        assertEquals(listOf<BackupReadResult>(BackupReadResult.Failed, BackupReadResult.Failed), results)
        assertEquals(42, platform.delay)
        val handle = reader.read(FakeFile(2), results::add)
        val lateRead = platform.readCallback!!
        val lateTimer = platform.timerCallback!!
        handle.cancel()
        handle.cancel()
        lateRead(Result.success("late".encodeToByteArray()))
        lateTimer()
        assertEquals(2, results.size)
        reader.read(FakeFile(2), results::add)
        val lateAfterDispose = platform.readCallback!!
        reader.dispose()
        lateAfterDispose(Result.success("late".encodeToByteArray()))
        assertEquals(2, results.size)
        assertEquals(3, platform.closedReads)
        assertEquals(4, platform.closedTimers)
        reader.read(FakeFile(2), results::add)
        assertEquals(BackupReadResult.Failed, results.last())
        assertEquals(4, platform.reads)
        assertIs<BackupReadResult.Failed>(results.last())
        assertTrue(platform.closedTimers >= platform.closedReads)
        assertFalse(results.any { it is BackupReadResult.Text })
        // Constructing the browser adapter does not access DOM globals under Node.
        BackupFileReader().dispose()
    }
}
