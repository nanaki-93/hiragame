package com.github.nanaki_93.storage

import com.github.nanaki_93.content.BundledContentLoader
import com.github.nanaki_93.content.ContentTextSource
import com.github.nanaki_93.practice.LocalPracticeCoordinator
import com.github.nanaki_93.practice.LocalPracticeState
import com.github.nanaki_93.practice.PracticeAnswer
import com.github.nanaki_93.practice.PracticeCheckpointResolution
import com.github.nanaki_93.practice.PracticeCommand
import com.github.nanaki_93.practice.SessionView
import com.github.nanaki_93.progress.CheckpointView
import com.github.nanaki_93.progress.LessonProgress
import com.github.nanaki_93.progress.LessonStage
import com.github.nanaki_93.progress.ProgressUpdate
import com.github.nanaki_93.progress.ResetScope
import com.github.nanaki_93.progress.ReviewItemProgress
import com.github.nanaki_93.progress.ReviewOutcome
import com.github.nanaki_93.progress.SaveCodec
import com.github.nanaki_93.progress.SaveDecodeResult
import com.github.nanaki_93.progress.SavedColorMode
import com.github.nanaki_93.progress.changePreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** FINAL GATE fixture: exercise the adapters and canonical Resume path together, not in the focused coordinator filter. */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupRoundTripIntegrationTest {
    private val content = object : ContentTextSource {
        override suspend fun readText(relativePath: String): String {
            val fs: dynamic = js("require('fs')")
            val path: dynamic = js("require('path')")
            var root: String = js("process.cwd()") as String
            while (!(fs.existsSync(path.resolve(root, "site/src/jsMain/resources/public/content/catalog.json")) as Boolean)) {
                val parent = path.dirname(root) as String
                check(parent != root)
                root = parent
            }
            return fs.readFileSync(path.resolve(root, "site/src/jsMain/resources/public/content", relativePath), "utf8") as String
        }
    }
    private class FilePlatform : BackupFilePlatform {
        var bytes = byteArrayOf()
        override fun byteSize(file: Any) = bytes.size.toLong()
        override fun read(file: Any, completed: (Result<ByteArray>) -> Unit): BackupFileResource {
            completed(Result.success(bytes))
            return BackupFileResource {}
        }
        override fun timeout(delayMs: Int, expired: () -> Unit) = BackupFileResource {}
    }

    @Test fun exportResetPreviewRestoreAndFreshCanonicalResume() = runTest {
        val backing = MemoryProgressBacking()
        var nextId = 0
        var writeTime = 1000L
        fun open() = LocalProgressOwner(MemoryProgressStore(backing), { writeTime }, { "roundtrip_${++nextId}" })
        val owner = open()
        val first = LocalPracticeCoordinator(this, owner, BundledContentLoader(content)::load, now = { 1000L })
        first.load(); runCurrent()
        val setId = "practice-kana-a-i"
        first.start(setId)
        val prompt = assertIs<LocalPracticeState.Ready>(first.state.value).session!!
        first.dispatch(PracticeCommand.Submit(prompt.id, prompt.revision, PracticeAnswer.Choice("option-kana-a")))
        assertIs<SessionView.Feedback>(assertIs<LocalPracticeState.Ready>(first.state.value).session!!.view)
        owner.mutate { changePreferences(it, it.preferences.copy(colorMode = SavedColorMode.DARK, showRomaji = true, showReadings = false)) }
        owner.mutate { snapshot -> ProgressUpdate.Applied(snapshot.copy(
            lessonProgress = listOf(
                LessonProgress("unknown_finished", 1, 800, LessonStage.SUMMARY, completedAtEpochMs = 800),
                LessonProgress("unknown_unfinished", 1, 900, LessonStage.DIALOGUE),
            ),
            reviewItems = listOf(ReviewItemProgress("unknown_item", "unknown_doc", ReviewOutcome.GOOD, 900,
                dueAtEpochMs = 2000, intervalMs = 1100, step = 2, repetitions = 3, lapses = 0, lastActionToken = "action_1")),
        )) }
        val payload = owner.state.value.snapshot
        assertEquals(CheckpointView.FEEDBACK, payload.practiceProgress.single().view)
        var exported = ""
        val sink = ProgressDownloadSink { _, _, text -> exported = text }
        assertIs<DownloadResult.Downloaded>(ProgressDownloads(owner, sink, { 2000L }).exportCurrent())
        writeTime = 3000L
        val reset = assertIs<ReplacementPreparation.Ready>(owner.beginReset(ResetScope.FULL_LEARNER_STATE)).token
        assertEquals(ReplacementResult.Replaced, owner.confirmReplacement(reset))
        assertNull(assertIs<LocalPracticeState.Ready>(first.state.value).session)
        assertEquals(emptyList(), owner.state.value.snapshot.practiceProgress)
        val platform = FilePlatform().apply { bytes = exported.encodeToByteArray() }
        val flow = BackupFlowCoordinator(owner, BackupFileReader(platform))
        flow.select(Unit, "backup.json")
        val preview = assertIs<BackupFlowState.Preview>(flow.state.value).details
        assertEquals(2, preview.lessonCount)
        assertEquals(1, preview.completedLessonCount)
        assertEquals(1, preview.practiceCheckpointCount)
        assertEquals(1, preview.reviewItemCount)
        flow.requestConfirmation()
        assertIs<BackupFlowState.Confirming>(flow.state.value)
        writeTime = 4000L
        flow.confirm()
        assertIs<BackupFlowState.Success>(flow.state.value)
        val restored = owner.state.value.snapshot
        assertEquals(payload.preferences, restored.preferences)
        assertEquals(payload.lessonProgress, restored.lessonProgress)
        assertEquals(payload.practiceProgress, restored.practiceProgress)
        assertEquals(payload.reviewItems, restored.reviewItems)
        assertNotEquals(payload.snapshotId, restored.snapshotId)
        assertNotEquals(payload.savedAtEpochMs, restored.savedAtEpochMs)
        assertNotEquals(payload.revision, restored.revision)
        assertEquals(restored, assertIs<SaveDecodeResult.Valid>(SaveCodec.decodeSave(backing.raw!!)).snapshot)
        flow.dispose(); first.dispose(); owner.dispose()
        val reopened = open()
        assertEquals(restored, reopened.state.value.snapshot)
        val second = LocalPracticeCoordinator(this, reopened, BundledContentLoader(content)::load, now = { 3000L })
        second.load(); runCurrent()
        assertIs<PracticeCheckpointResolution.Available>(second.savedCheckpoints()[setId])
        val raw = backing.raw
        second.resume(setId)
        assertEquals(SessionView.Feedback(0), assertIs<LocalPracticeState.Ready>(second.state.value).session!!.view)
        assertEquals(raw, backing.raw)
        second.dispose(); reopened.dispose()
    }
}
