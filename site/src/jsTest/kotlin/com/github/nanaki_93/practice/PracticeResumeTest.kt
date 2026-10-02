package com.github.nanaki_93.practice

import com.github.nanaki_93.content.BundledContentLoader
import com.github.nanaki_93.content.CatalogLoad
import com.github.nanaki_93.content.ContentTextSource
import com.github.nanaki_93.progress.CheckpointView
import com.github.nanaki_93.progress.ProgressUpdate
import com.github.nanaki_93.storage.LocalProgressOwner
import com.github.nanaki_93.storage.MemoryProgressBacking
import com.github.nanaki_93.storage.MemoryProgressStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeResumeTest {
    private val setId = "practice-kana-a-i"
    private val source = object : ContentTextSource {
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
    private var ids = 0
    private fun owner(backing: MemoryProgressBacking) = LocalProgressOwner(
        MemoryProgressStore(backing), { 1000L }, { "resume_snapshot_${++ids}" },
    )
    private fun coordinator(scope: kotlinx.coroutines.CoroutineScope, progress: LocalProgressOwner,
        load: suspend () -> CatalogLoad = BundledContentLoader(source)::load,
    ) = LocalPracticeCoordinator(scope, progress, load, now = { 1000L })
    private fun LocalPracticeCoordinator.ready() = assertIs<LocalPracticeState.Ready>(state.value)

    @Test fun canonicalFeedbackResumesInFreshOwnerAndCompletesOnlyAfterContinue() = runTest {
        val backing = MemoryProgressBacking()
        val firstOwner = owner(backing)
        val first = coordinator(this, firstOwner)
        first.load(); runCurrent()
        first.start(setId)
        val prompt = first.ready().session!!
        assertEquals(listOf("exercise-kana-a-choice", "exercise-kana-i-reading"), prompt.plan.map { it.id })
        first.dispatch(PracticeCommand.Submit(prompt.id, prompt.revision, PracticeAnswer.Choice("option-kana-a")))
        val feedback = first.ready().session!!
        val oldContinue = PracticeCommand.Continue(feedback.id, feedback.revision)
        val raw = backing.raw
        first.dispose(); firstOwner.dispose()

        val secondOwner = owner(backing)
        val second = coordinator(this, secondOwner)
        second.load(); runCurrent()
        val checkpoint = secondOwner.state.value.snapshot.practiceProgress.single()
        assertEquals(CheckpointView.FEEDBACK, checkpoint.view)
        assertIs<PracticeCheckpointResolution.Available>(second.savedCheckpoints()[setId])
        assertEquals(raw, backing.raw) // resolving and resuming do not write
        second.resume(setId)
        var resumed = second.ready().session!!
        assertNotEquals(feedback.id, resumed.id)
        assertEquals(SessionView.Feedback(0), resumed.view)
        assertEquals(1, resumed.counts.correct)
        assertEquals(raw, backing.raw)
        second.dispatch(oldContinue)
        assertSame(resumed, second.ready().session)
        second.dispatch(PracticeCommand.Continue(resumed.id, resumed.revision))
        resumed = second.ready().session!!
        assertEquals(SessionView.Prompt(1), resumed.view)
        assertEquals(CheckpointView.PROMPT, secondOwner.state.value.snapshot.practiceProgress.single().view)
        second.dispatch(PracticeCommand.Submit(resumed.id, resumed.revision, PracticeAnswer.Text(" i ")))
        resumed = second.ready().session!!
        assertEquals(SessionView.Feedback(1), resumed.view)
        assertEquals(2, resumed.counts.correct)
        second.dispatch(PracticeCommand.Continue(resumed.id, resumed.revision))
        assertEquals(SessionView.Complete, second.ready().session!!.view)
        val completed = secondOwner.state.value.snapshot.practiceProgress.single()
        assertEquals(CheckpointView.COMPLETE, completed.view)
        second.dispatch(PracticeCommand.Continue(resumed.id, resumed.revision))
        assertEquals(completed, secondOwner.state.value.snapshot.practiceProgress.single())
        second.dispatch(PracticeCommand.Leave(second.ready().session!!.id, second.ready().session!!.revision))
        second.resume(setId)
        assertEquals(SessionView.Complete, second.ready().session!!.view)
        assertEquals(completed, secondOwner.state.value.snapshot.practiceProgress.single())
    }

    @Test fun missingSetAndIncompatibleExerciseRemainSavedUntilExplicitRecoveryOrReturn() = runTest {
        val backing = MemoryProgressBacking()
        val progress = owner(backing)
        val full = assertIs<CatalogLoad.Ready>(BundledContentLoader(source).load())
        val set = full.content.practiceSets.getValue(setId)
        val coordinator = coordinator(this, progress) { CatalogLoad.Ready(full.content) }
        coordinator.load(); runCurrent()
        coordinator.start(setId)
        val priorSession = coordinator.ready().session!!
        val staleSkip = PracticeCommand.Skip(priorSession.id, priorSession.revision)
        val original = progress.state.value.snapshot.practiceProgress.single()
        val changed = original.copy(exerciseIds = listOf("missing-exercise") + original.exerciseIds.drop(1))
        progress.mutate { ProgressUpdate.Applied(it.copy(practiceProgress = listOf(changed))) }
        coordinator.load(); runCurrent()
        val raw = backing.raw
        assertEquals(false, assertIs<PracticeCheckpointResolution.Unavailable>(coordinator.savedCheckpoints()[setId]).missingSet)
        coordinator.resume(setId)
        coordinator.start(setId) // ordinary Start must not silently replace the incompatible record
        assertNull(coordinator.ready().session)
        assertEquals(raw, backing.raw)
        coordinator.startFreshAfterUnavailable(setId)
        val fresh = coordinator.ready().session!!
        assertEquals(SessionView.Prompt(0), fresh.view)
        coordinator.dispatch(staleSkip)
        assertSame(fresh, coordinator.ready().session)
        assertNotEquals(changed.runToken, progress.state.value.snapshot.practiceProgress.single().runToken)
        coordinator.dispatch(PracticeCommand.Leave(fresh.id, fresh.revision))

        val current = progress.state.value.snapshot.practiceProgress.single()
        val noSet = coordinator(this, progress) {
            CatalogLoad.Ready(full.content.copy(practiceSets = emptyMap()))
        }
        noSet.load(); runCurrent()
        val before = backing.raw
        assertTrue(assertIs<PracticeCheckpointResolution.Unavailable>(noSet.savedCheckpoints()[setId]).missingSet)
        noSet.startFreshAfterUnavailable(setId)
        noSet.resume(setId)
        assertNull(noSet.ready().session)
        assertEquals(before, backing.raw)
        assertEquals(current, progress.state.value.snapshot.practiceProgress.single())
        noSet.dispose()
        coordinator.load(); runCurrent()
        assertIs<PracticeCheckpointResolution.Available>(coordinator.savedCheckpoints()[setId])
        assertEquals(current, progress.state.value.snapshot.practiceProgress.single())
        assertEquals(set.id, current.setId)
    }

    @Test fun missingContentReturnsWithoutReplacingOriginalCheckpoint() = runTest {
        val backing = MemoryProgressBacking()
        val progress = owner(backing)
        val full = assertIs<CatalogLoad.Ready>(BundledContentLoader(source).load())
        val first = coordinator(this, progress) { full }
        first.load(); runCurrent(); first.start(setId)
        val original = progress.state.value.snapshot.practiceProgress.single()
        val wire = backing.raw
        first.dispose()
        var missing = true
        val second = coordinator(this, progress) {
            if (missing) CatalogLoad.Ready(full.content.copy(practiceSets = emptyMap())) else full
        }
        second.load(); runCurrent()
        assertTrue(assertIs<PracticeCheckpointResolution.Unavailable>(second.savedCheckpoints()[setId]).missingSet)
        second.resume(setId)
        assertEquals(wire, backing.raw)
        missing = false
        second.load(); runCurrent()
        assertIs<PracticeCheckpointResolution.Available>(second.savedCheckpoints()[setId])
        assertEquals(original, progress.state.value.snapshot.practiceProgress.single())
        second.resume(setId)
        assertEquals(SessionView.Prompt(0), second.ready().session!!.view)
        assertEquals(wire, backing.raw)
    }

    @Test fun reloadDropsPendingFailedActionAndItsRetryToken() = runTest {
        val backing = MemoryProgressBacking()
        val progress = owner(backing)
        val coordinator = LocalPracticeCoordinator(this, progress, BundledContentLoader(source)::load,
            reducer = { session, command ->
                if (command is PracticeCommand.Skip) error("simulated reducer failure")
                reduce(session, command)
            }, now = { 1000L })
        coordinator.load(); runCurrent(); coordinator.start(setId)
        val before = coordinator.ready().session!!
        coordinator.dispatch(PracticeCommand.Skip(before.id, before.revision))
        val error = coordinator.ready().operationError!!
        assertSame(before, coordinator.ready().session)
        assertTrue(progress.reloadSavedState())
        assertNull(coordinator.ready().session)
        coordinator.retryOperation(error)
        coordinator.leave(error)
        assertNull(coordinator.ready().session)
        assertNull(coordinator.ready().operationError)
        coordinator.resume(setId)
        val restored = coordinator.ready().session!!
        coordinator.dispatch(PracticeCommand.Skip(before.id, before.revision))
        assertSame(restored, coordinator.ready().session)
    }

    @Test fun previouslyObtainedFlowClearsImmediatelyOnOwnerReloadEvenWhenSaveIsUnchanged() = runTest {
        val backing = MemoryProgressBacking()
        val progress = owner(backing)
        val coordinator = coordinator(this, progress)
        coordinator.load(); runCurrent()
        coordinator.start(setId)
        val observedFlow = coordinator.state // retain the flow a UI collector already holds
        assertTrue(assertIs<LocalPracticeState.Ready>(observedFlow.value).session != null)
        val wire = backing.raw
        assertTrue(progress.reloadSavedState()) // same bytes and same status; no coordinator call or scheduler advance
        assertEquals(wire, backing.raw)
        assertNull(assertIs<LocalPracticeState.Ready>(observedFlow.value).session)
        coordinator.dispose()
        progress.dispose()
    }

    @Test fun ownerReloadInvalidatesSessionPendingRetryAndOldCallbacks() = runTest {
        val backing = MemoryProgressBacking()
        val progress = owner(backing)
        val coordinator = coordinator(this, progress)
        coordinator.load(); runCurrent()
        coordinator.start(setId)
        val old = coordinator.ready().session!!
        val oldSkip = PracticeCommand.Skip(old.id, old.revision)
        val observedFlow = coordinator.state
        backing.externalChange(null)
        assertTrue(progress.reloadSavedState())
        assertNull(assertIs<LocalPracticeState.Ready>(observedFlow.value).session) // no coordinator call
        coordinator.dispatch(oldSkip)
        coordinator.retryOperation(PracticeOperationError(PracticeOperation.DISPATCH, "stale", 1))
        coordinator.start(setId)
        val fresh = coordinator.ready().session!!
        assertNotEquals(old.id, fresh.id)
        coordinator.dispatch(oldSkip)
        assertSame(fresh, coordinator.ready().session)
        assertEquals(0, fresh.counts.completed)
    }
}
