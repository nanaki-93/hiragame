package com.github.nanaki_93.lesson

import com.github.nanaki_93.content.BundledContentException
import com.github.nanaki_93.content.BundledContentLoader
import com.github.nanaki_93.content.CatalogLoad
import com.github.nanaki_93.content.ContentHttpException
import com.github.nanaki_93.content.ContentTextSource
import com.github.nanaki_93.progress.LessonProgress
import com.github.nanaki_93.progress.ReviewItemProgress
import com.github.nanaki_93.progress.ReviewOutcome
import com.github.nanaki_93.storage.PersistenceStatus
import com.github.nanaki_93.storage.StoreFailure
import com.github.nanaki_93.progress.SaveProblem
import com.github.nanaki_93.progress.LessonStage
import com.github.nanaki_93.progress.ProgressUpdate
import com.github.nanaki_93.storage.LocalProgressOwner
import com.github.nanaki_93.storage.MemoryProgressBacking
import com.github.nanaki_93.storage.MemoryProgressStore
import com.github.nanaki_93.storage.ProgressStore
import com.github.nanaki_93.storage.StoreReadResult
import com.github.nanaki_93.storage.StoreSubscription
import com.github.nanaki_93.storage.StoreWriteResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LocalLessonCoordinatorTest {
    private val review = """{"status":"reviewed","reviewerType":"agent","reviewDate":"2026-10-01","reviewNote":"seed.md","provenance":"Original","rights":"publishable","rightsBasis":"Original"}"""
    private val files = mapOf(
        "catalog.json" to """{"formatVersion":1,"contentVersion":1,"topics":[{"id":"workplace","title":"Workplace","description":"At work"}],"entries":[{"id":"one","kind":"lesson","topicId":"workplace","path":"lessons/one.json"}]}""",
        "lessons/one.json" to """{"formatVersion":1,"contentVersion":1,"id":"one","topicId":"workplace","title":"Lesson","situation":"At work","communicationGoal":"Ask","difficulty":"beginner","durationMinutes":5,"prerequisiteLessonIds":[],"dialogue":{"speakers":[],"turns":[]},"phrases":[],"grammarNotes":[],"rolePlay":{"task":"Ask","criteria":["Polite"]},"reviewItems":[],"review":$review,"exercises":[],"conversationGraph":null}""",
    )
    private fun loader(assets: Map<String, String> = files) = BundledContentLoader(object : ContentTextSource {
        override suspend fun readText(relativePath: String): String = assets[relativePath] ?: error("missing asset")
    })
    private var sequence = 0
    private fun owner(store: ProgressStore = CountingStore()) =
        LocalProgressOwner(store, { 1000L }, { "snapshot_${++sequence}" })
    private class CountingStore(private val backing: MemoryProgressStore = MemoryProgressStore()) : ProgressStore {
        var writes = 0
        override fun read(): StoreReadResult = backing.read()
        override fun write(expectedRaw: String?, replacementRaw: String): StoreWriteResult {
            writes++
            return backing.write(expectedRaw, replacementRaw)
        }
        override fun subscribe(onExternalChange: () -> Unit): StoreSubscription = backing.subscribe(onExternalChange)
    }
    private fun record(stage: LessonStage, item: String? = null) = LessonProgress("one", 99, 1000L, stage, item)
    private fun put(owner: LocalProgressOwner, record: LessonProgress) {
        owner.mutate { ProgressUpdate.Applied(it.copy(lessonProgress = listOf(record))) }
    }

    @Test fun startNavigationAndFinishCommitOnlyGuardedActions() = runTest {
        val store = CountingStore()
        val progress = owner(store)
        val coordinator = LocalLessonCoordinator(this, progress, loader()::load, now = { 2000L })
        coordinator.load("one")
        runCurrent()
        coordinator.start()
        val situation = assertIs<LocalLessonState.Active>(coordinator.state.value)
        assertEquals(LessonCommit.Accepted(PersistenceStatus.Saved), situation.commit)
        assertEquals(LessonStage.SITUATION, progress.state.value.snapshot.lessonProgress.single().stage)
        val startWrites = store.writes
        coordinator.start()
        coordinator.finish(situation.session.id, situation.session.revision) // not Summary
        assertEquals(startWrites, store.writes)
        val next = LessonCommand.Next(situation.session.id, situation.session.revision)
        coordinator.dispatch(next)
        val dialogue = assertIs<LocalLessonState.Active>(coordinator.state.value)
        assertEquals(LessonStage.DIALOGUE, dialogue.session.stage)
        coordinator.dispatch(next)
        assertEquals(startWrites + 1, store.writes)
        // All empty stages require explicit navigation; Summary itself isn't completion.
        while (assertIs<LocalLessonState.Active>(coordinator.state.value).session.stage != LessonStage.SUMMARY) {
            val session = assertIs<LocalLessonState.Active>(coordinator.state.value).session
            if (session.stage == LessonStage.ROLE_PLAY) coordinator.dispatch(LessonCommand.Skip(session.id, session.revision))
            val current = assertIs<LocalLessonState.Active>(coordinator.state.value).session
            coordinator.dispatch(if (current.stage == LessonStage.ROLE_PLAY) LessonCommand.Continue(current.id, current.revision)
                else LessonCommand.Next(current.id, current.revision))
        }
        val summary = assertIs<LocalLessonState.Active>(coordinator.state.value)
        assertEquals(null, progress.state.value.snapshot.lessonProgress.single().completedAtEpochMs)
        assertEquals(false, summary.finished)
        coordinator.finish(summary.session.id, summary.session.revision)
        val finished = assertIs<LocalLessonState.Active>(coordinator.state.value)
        assertTrue(finished.finished)
        assertEquals(LessonCommit.Accepted(PersistenceStatus.Saved), finished.commit)
        assertEquals(2000L, progress.state.value.snapshot.lessonProgress.single().completedAtEpochMs)
        val writes = store.writes
        coordinator.finish(summary.session.id, summary.session.revision)
        assertEquals(writes, store.writes)
        coordinator.dispose()
        progress.dispose()
    }

    @Test fun recoveryOnlyOnConsentPreservesCompletionReviewsAndOtherRecords() = runTest {
        val store = CountingStore()
        val progress = owner(store)
        val old = record(LessonStage.DIALOGUE, "removed-turn").copy(completedAtEpochMs = 500L)
        val other = LessonProgress("unavailable", 1, 500L, LessonStage.SUMMARY, completedAtEpochMs = 500L)
        val review = ReviewItemProgress("item", "other", ReviewOutcome.GOOD, 500L,
            lastActionToken = "token")
        progress.mutate { ProgressUpdate.Applied(it.copy(lessonProgress = listOf(old, other), reviewItems = listOf(review))) }
        val coordinator = LocalLessonCoordinator(this, progress, loader()::load, now = { 2000L })
        coordinator.load("one")
        runCurrent()
        assertIs<LocalLessonState.RecoveryRequired>(coordinator.state.value)
        val writes = store.writes
        coordinator.start()
        coordinator.resume()
        assertEquals(writes, store.writes)
        assertEquals(old, progress.state.value.snapshot.lessonProgress.first())
        coordinator.recoverToSituation()
        val recovered = assertIs<LocalLessonState.Active>(coordinator.state.value)
        assertEquals(LessonStage.SITUATION, recovered.session.stage)
        assertEquals(LessonCommit.Accepted(PersistenceStatus.Saved), recovered.commit)
        assertEquals(old.copy(contentVersion = 1, updatedAtEpochMs = 2000L,
            stage = LessonStage.SITUATION, checkpointId = null), progress.state.value.snapshot.lessonProgress.first())
        assertEquals(other, progress.state.value.snapshot.lessonProgress.last())
        assertEquals(listOf(review), progress.state.value.snapshot.reviewItems)
        coordinator.recoverToSituation()
        assertEquals(writes + 1, store.writes)
        coordinator.dispose()
        progress.dispose()
    }

    @Test fun deniedAndRejectedMutationsStayUsableWithoutFalseCompletion() = runTest {
        val backing = MemoryProgressStore().also { it.writeFailure = StoreFailure.DENIED }
        val store = CountingStore(backing)
        val progress = owner(store)
        var time = 2000L
        val coordinator = LocalLessonCoordinator(this, progress, loader()::load, now = { time })
        coordinator.load("one")
        runCurrent()
        coordinator.start()
        val started = assertIs<LocalLessonState.Active>(coordinator.state.value)
        assertIs<LessonCommit.Accepted>(started.commit)
        assertIs<PersistenceStatus.MemoryOnly>(assertIs<LessonCommit.Accepted>(started.commit).status)
        assertEquals(1, store.writes)
        time = -1L // shared mutation rejects an invalid timestamp before the owner can publish it
        coordinator.dispatch(LessonCommand.Next(started.session.id, started.session.revision))
        val moved = assertIs<LocalLessonState.Active>(coordinator.state.value)
        assertEquals(LessonStage.DIALOGUE, moved.session.stage)
        assertEquals(LessonCommit.Rejected(SaveProblem.INVALID_SNAPSHOT), moved.commit)
        assertEquals(LessonStage.SITUATION, progress.state.value.snapshot.lessonProgress.single().stage)
        assertEquals(1, store.writes)
        coordinator.dispatch(LessonCommand.Next(moved.session.id, moved.session.revision))
        val understanding = assertIs<LocalLessonState.Active>(coordinator.state.value)
        assertEquals(LessonStage.UNDERSTANDING, understanding.session.stage)
        assertIs<LessonCommit.Rejected>(understanding.commit)
        coordinator.dispose()
        progress.dispose()
    }

    @Test fun rejectedFinishDoesNotClaimCompletionAndMayBeRetried() = runTest {
        val progress = owner()
        var time = 2000L
        val coordinator = LocalLessonCoordinator(this, progress, loader()::load, now = { time })
        coordinator.load("one")
        runCurrent()
        coordinator.start()
        while (assertIs<LocalLessonState.Active>(coordinator.state.value).session.stage != LessonStage.SUMMARY) {
            val session = assertIs<LocalLessonState.Active>(coordinator.state.value).session
            coordinator.dispatch(when (session.stage) {
                LessonStage.ROLE_PLAY -> LessonCommand.SkipRemaining(session.id, session.revision)
                else -> LessonCommand.Next(session.id, session.revision)
            })
        }
        val summary = assertIs<LocalLessonState.Active>(coordinator.state.value).session
        time = -1L
        coordinator.finish(summary.id, summary.revision)
        val rejected = assertIs<LocalLessonState.Active>(coordinator.state.value)
        assertEquals(false, rejected.finished)
        assertEquals(LessonCommit.Rejected(SaveProblem.INVALID_SNAPSHOT), rejected.commit)
        assertEquals(null, progress.state.value.snapshot.lessonProgress.single().completedAtEpochMs)
        time = 3000L
        coordinator.finish(summary.id, summary.revision)
        assertTrue(assertIs<LocalLessonState.Active>(coordinator.state.value).finished)
        assertEquals(3000L, progress.state.value.snapshot.lessonProgress.single().completedAtEpochMs)
        coordinator.dispose()
        progress.dispose()
    }

    @Test fun clockTriggeredOwnerReloadRejectsObsoleteActionBeforeMutation() = runTest {
        val store = CountingStore()
        val progress = owner(store)
        var reloadOnClock = false
        val coordinator = LocalLessonCoordinator(this, progress, loader()::load, now = {
            if (reloadOnClock) progress.reloadSavedState()
            2000L
        })
        coordinator.load("one")
        runCurrent()
        coordinator.start()
        val active = assertIs<LocalLessonState.Active>(coordinator.state.value).session
        val writes = store.writes
        reloadOnClock = true
        coordinator.dispatch(LessonCommand.Next(active.id, active.revision))
        assertIs<LocalLessonState.Entry>(coordinator.state.value)
        assertEquals(writes, store.writes)
        coordinator.dispose()
        progress.dispose()
    }

    @Test fun protectedAndConflictingSavesStayUntouchedWhileNavigationContinues() = runTest {
        val protectedBacking = MemoryProgressBacking("unsupported original")
        val protectedStore = CountingStore(MemoryProgressStore(protectedBacking))
        val protectedOwner = owner(protectedStore)
        val protectedLesson = LocalLessonCoordinator(this, protectedOwner, loader())
        protectedLesson.load("one")
        runCurrent()
        protectedLesson.start()
        val protectedSession = assertIs<LocalLessonState.Active>(protectedLesson.state.value)
        assertIs<PersistenceStatus.Protected>(assertIs<LessonCommit.Accepted>(protectedSession.commit).status)
        assertEquals("unsupported original", protectedBacking.raw)
        assertEquals(0, protectedStore.writes)
        protectedLesson.dispatch(LessonCommand.Next(protectedSession.session.id, protectedSession.session.revision))
        assertEquals(LessonStage.DIALOGUE, assertIs<LocalLessonState.Active>(protectedLesson.state.value).session.stage)
        protectedLesson.dispose()
        protectedOwner.dispose()

        val backing = MemoryProgressBacking()
        val store = CountingStore(MemoryProgressStore(backing))
        val progress = owner(store)
        val coordinator = LocalLessonCoordinator(this, progress, loader())
        coordinator.load("one")
        runCurrent()
        coordinator.start()
        val started = assertIs<LocalLessonState.Active>(coordinator.state.value).session
        backing.externalChange("another tab's original")
        coordinator.dispatch(LessonCommand.Next(started.id, started.revision))
        val moved = assertIs<LocalLessonState.Active>(coordinator.state.value)
        assertEquals(LessonCommit.Accepted(PersistenceStatus.Conflict), moved.commit)
        assertEquals("another tab's original", backing.raw)
        assertEquals(1, store.writes)
        coordinator.dispose()
        progress.dispose()
    }

    @Test fun validatedLookupEntryAndResumeAreReadOnlyAndNeverFallback() = runTest {
        val store = CountingStore()
        val progress = owner(store)
        val coordinator = LocalLessonCoordinator(this, progress, loader()::load, now = { 1000L })
        coordinator.load("one")
        assertIs<LocalLessonState.Loading>(coordinator.state.value)
        runCurrent()
        assertIs<LessonCheckpointResolution.NoRecord>(assertIs<LocalLessonState.Entry>(coordinator.state.value).checkpoint)
        assertEquals(0, store.writes)
        coordinator.load("../../one.json")
        runCurrent()
        assertEquals(LocalLessonState.Missing("../../one.json"), coordinator.state.value)
        coordinator.load("absent")
        runCurrent()
        assertEquals(LocalLessonState.Missing("absent"), coordinator.state.value)
        assertEquals(0, store.writes)
        put(progress, record(LessonStage.DIALOGUE))
        val writes = store.writes
        coordinator.load("one")
        runCurrent()
        val entry = assertIs<LocalLessonState.Entry>(coordinator.state.value)
        assertIs<LessonCheckpointResolution.Available>(entry.checkpoint)
        coordinator.resume()
        val session = assertIs<LocalLessonState.Active>(coordinator.state.value).session
        assertEquals(LessonStage.DIALOGUE, session.stage)
        assertTrue(session.resumed)
        assertEquals(writes, store.writes)
        assertEquals(record(LessonStage.DIALOGUE), progress.state.value.snapshot.lessonProgress.single())
        coordinator.dispose()
        progress.dispose()
    }

    @Test fun incompatibleCheckpointIsReadOnlyAndMissingDocumentPreservesRecord() = runTest {
        val store = CountingStore()
        val progress = owner(store)
        put(progress, record(LessonStage.DIALOGUE, "removed-turn"))
        val writes = store.writes
        val coordinator = LocalLessonCoordinator(this, progress, loader())
        coordinator.load("one")
        runCurrent()
        assertEquals(LessonCheckpointMismatch.REMOVED_ITEM,
            assertIs<LocalLessonState.RecoveryRequired>(coordinator.state.value).checkpoint.reason)
        coordinator.resume()
        assertIs<LocalLessonState.RecoveryRequired>(coordinator.state.value)
        coordinator.load("missing")
        runCurrent()
        assertIs<LocalLessonState.Missing>(coordinator.state.value)
        assertEquals(listOf(record(LessonStage.DIALOGUE, "removed-turn")), progress.state.value.snapshot.lessonProgress)
        assertEquals(writes, store.writes)
        coordinator.dispose()
        progress.dispose()
    }

    @Test fun emptyAndFailureStatesUseFixedMessagesAndRetryValidatedLoader() = runTest {
        val store = CountingStore()
        val progress = owner(store)
        var assets = files + ("lessons/one.json" to "<html>private body</html>")
        val coordinator = LocalLessonCoordinator(this, progress, { loader(assets).load() })
        coordinator.load("one")
        runCurrent()
        val invalid = assertIs<LocalLessonState.Error>(coordinator.state.value)
        assertEquals(LessonLoadErrorKind.CONTENT, invalid.kind)
        assertEquals("Unable to load reviewed content. Retry the load.", invalid.safeMessage)
        assets = files
        coordinator.retryLoad()
        runCurrent()
        assertIs<LocalLessonState.Entry>(coordinator.state.value)
        assertEquals(0, store.writes)
        coordinator.dispose()
        val empty = LocalLessonCoordinator(this, progress, loader(mapOf("catalog.json" to
            """{"formatVersion":1,"contentVersion":1,"topics":[],"entries":[]}""")))
        empty.load("one")
        runCurrent()
        assertEquals(LocalLessonState.Empty(LessonEmptyReason.EMPTY_CATALOG), empty.state.value)
        empty.dispose()
        val noLesson = LocalLessonCoordinator(this, progress, { CatalogLoad.Ready(loader(files).load().let {
            (it as CatalogLoad.Ready).content.copy(lessons = emptyMap())
        }) })
        noLesson.load("one")
        runCurrent()
        assertEquals(LocalLessonState.Empty(LessonEmptyReason.NO_LESSONS), noLesson.state.value)
        noLesson.dispose()
        progress.dispose()
    }

    @Test fun errorsNeverExposeTransportTextAndCanRetry() = runTest {
        val progress = owner()
        var failure: Exception? = BundledContentException("secret.json", "private body", ContentHttpException(403))
        val coordinator = LocalLessonCoordinator(this, progress, {
            failure?.let { throw it }
            loader().load()
        })
        for ((exception, kind) in listOf(
            BundledContentException("secret.json", "private body", ContentHttpException(403)) to LessonLoadErrorKind.HTTP,
            IllegalStateException("private body") to LessonLoadErrorKind.UNEXPECTED,
        )) {
            failure = exception
            coordinator.load("one")
            runCurrent()
            val error = assertIs<LocalLessonState.Error>(coordinator.state.value)
            assertEquals(kind, error.kind)
            assertTrue("private" !in error.safeMessage && "secret" !in error.safeMessage)
        }
        failure = null
        coordinator.retryLoad()
        runCurrent()
        assertIs<LocalLessonState.Entry>(coordinator.state.value)
        coordinator.dispose()
        progress.dispose()
    }

    @Test fun overlapCancellationIgnoringSourceAndDisposalCannotPublishStaleResult() = runTest {
        val first = CompletableDeferred<CatalogLoad>()
        val second = CompletableDeferred<CatalogLoad>()
        val ready = loader().load()
        val progress = owner()
        var calls = 0
        val coordinator = LocalLessonCoordinator(this, progress, {
            withContext(NonCancellable) { if (++calls == 1) first.await() else second.await() }
        })
        coordinator.load("one")
        runCurrent()
        coordinator.load("absent")
        runCurrent()
        first.complete(ready)
        runCurrent()
        assertIs<LocalLessonState.Loading>(coordinator.state.value)
        second.complete(ready)
        runCurrent()
        assertEquals(LocalLessonState.Missing("absent"), coordinator.state.value)
        coordinator.load("one")
        runCurrent() // third load would wait on second, already completed
        assertIs<LocalLessonState.Entry>(coordinator.state.value)
        coordinator.dispose()
        coordinator.load("one")
        assertEquals(3, calls)
        progress.dispose()
    }

    @Test fun disposalPreventsCancellationIgnoringSuccessAndFailureFromPublishing() = runTest {
        val pending = CompletableDeferred<CatalogLoad>()
        val progress = owner()
        val coordinator = LocalLessonCoordinator(this, progress, {
            withContext(NonCancellable) { pending.await() }
        })
        coordinator.load("one")
        runCurrent()
        coordinator.dispose()
        pending.complete(loader().load())
        runCurrent()
        assertIs<LocalLessonState.Loading>(coordinator.state.value)
        coordinator.load("one")
        assertIs<LocalLessonState.Loading>(coordinator.state.value)
        progress.dispose()
    }

    @Test fun reloadAndReplacementInvalidateActiveSessionSynchronouslyEvenWhenSnapshotEqual() = runTest {
        val store = CountingStore()
        val progress = owner(store)
        val coordinator = LocalLessonCoordinator(this, progress, loader())
        coordinator.load("one")
        runCurrent()
        coordinator.start()
        val original = assertIs<LocalLessonState.Active>(coordinator.state.value).session
        assertTrue(progress.reloadSavedState()) // fresh/missing -> equal learner records
        assertIs<LocalLessonState.Entry>(coordinator.state.value)
        coordinator.dispatch(LessonCommand.Next(original.id, original.revision))
        assertIs<LocalLessonState.Entry>(coordinator.state.value)
        coordinator.start()
        val restarted = assertIs<LocalLessonState.Active>(coordinator.state.value).session
        assertNotEquals(original.id, restarted.id)
        // A committed owner replacement also notifies listeners synchronously.
        val token = assertIs<com.github.nanaki_93.storage.ReplacementPreparation.Ready>(
            progress.beginReset(com.github.nanaki_93.progress.ResetScope.PROGRESS_ONLY)).token
        assertIs<com.github.nanaki_93.storage.ReplacementResult.Replaced>(progress.confirmReplacement(token))
        assertIs<LocalLessonState.Entry>(coordinator.state.value)
        coordinator.dispatch(LessonCommand.Next(restarted.id, restarted.revision))
        assertIs<LocalLessonState.Entry>(coordinator.state.value)
        coordinator.dispose()
        progress.dispose()
    }

    @Test fun ownerReloadDuringPendingLoadStartsNewGenerationAndOldSourceCannotPublish() = runTest {
        val first = CompletableDeferred<CatalogLoad>()
        val second = CompletableDeferred<CatalogLoad>()
        val progress = owner()
        val ready = loader().load()
        var calls = 0
        val coordinator = LocalLessonCoordinator(this, progress, {
            withContext(NonCancellable) { if (++calls == 1) first.await() else second.await() }
        })
        coordinator.load("one")
        runCurrent()
        assertTrue(progress.reloadSavedState())
        runCurrent()
        first.completeExceptionally(IllegalStateException("old secret"))
        runCurrent()
        assertIs<LocalLessonState.Loading>(coordinator.state.value)
        second.complete(ready)
        runCurrent()
        assertIs<LocalLessonState.Entry>(coordinator.state.value)
        assertEquals(2, calls)
        coordinator.dispose()
        progress.dispose()
    }

    @Test fun synchronousOwnerInvalidationWithinReducerCannotPublishObsoleteSession() = runTest {
        val progress = owner()
        val coordinator = LocalLessonCoordinator(this, progress, loader()::load, reducer = { session, _ ->
            progress.reloadSavedState()
            session
        })
        coordinator.load("one")
        runCurrent()
        coordinator.start()
        val session = assertIs<LocalLessonState.Active>(coordinator.state.value).session
        coordinator.dispatch(LessonCommand.Next(session.id, session.revision))
        assertIs<LocalLessonState.Entry>(coordinator.state.value)
        coordinator.dispose()
        progress.dispose()
    }

    @Test fun failedActionMayBeRetriedWithoutReplayingAfterExit() = runTest {
        val progress = owner()
        var fail = true
        val coordinator = LocalLessonCoordinator(this, progress, loader()::load, reducer = { session, command ->
            if (fail) error("private exception") else reduceLesson(session, command)
        })
        coordinator.load("one")
        runCurrent()
        coordinator.start()
        val initial = assertIs<LocalLessonState.Active>(coordinator.state.value).session
        coordinator.dispatch(LessonCommand.Next(initial.id, initial.revision))
        val error = assertIs<LocalLessonState.Active>(coordinator.state.value).operationError!!
        fail = false
        coordinator.retryOperation(error)
        val advanced = assertIs<LocalLessonState.Active>(coordinator.state.value)
        assertEquals(LessonStage.DIALOGUE, advanced.session.stage)
        coordinator.retryOperation(error)
        assertSame(advanced, coordinator.state.value)
        coordinator.dispose()
        progress.dispose()
    }

    @Test fun cancelledLoadInLiveScopeCanRetry() = runTest {
        val progress = owner()
        var calls = 0
        val coordinator = LocalLessonCoordinator(this, progress, {
            if (++calls == 1) throw CancellationException("private cancellation")
            loader().load()
        })
        coordinator.load("one")
        runCurrent()
        val stopped = assertIs<LocalLessonState.Error>(coordinator.state.value)
        assertEquals(LessonLoadErrorKind.CANCELLED, stopped.kind)
        assertEquals("Lesson loading stopped. Retry or return to Topics.", stopped.safeMessage)
        coordinator.retryLoad()
        runCurrent()
        assertIs<LocalLessonState.Entry>(coordinator.state.value)
        assertEquals(2, calls)
        coordinator.dispose()
        progress.dispose()
    }

    @Test fun alreadyCancelledScopeCannotLeaveNewLoadStuck() = runTest {
        val parent = Job()
        parent.cancel()
        val progress = owner()
        var calls = 0
        val coordinator = LocalLessonCoordinator(CoroutineScope(coroutineContext + parent), progress, {
            calls++
            loader().load()
        })
        coordinator.load("one")
        runCurrent()
        val error = assertIs<LocalLessonState.Error>(coordinator.state.value)
        assertEquals(LessonLoadErrorKind.CANCELLED, error.kind)
        assertEquals("Lesson loading stopped. Return to Topics or Home.", error.safeMessage)
        assertEquals(0, calls)
        coordinator.dispose()
        progress.dispose()
    }

    @Test fun cancelledScopeAndFailedReducerRetainSafeActionableState() = runTest {
        val parent = Job()
        val late = CompletableDeferred<CatalogLoad>()
        val progress = owner()
        val coordinator = LocalLessonCoordinator(CoroutineScope(coroutineContext + parent), progress, {
            withContext(NonCancellable) { late.await() }
        })
        coordinator.load("one")
        runCurrent()
        parent.cancel()
        late.complete(loader().load())
        runCurrent()
        val cancelled = assertIs<LocalLessonState.Error>(coordinator.state.value)
        assertEquals(LessonLoadErrorKind.CANCELLED, cancelled.kind)
        assertEquals("Lesson loading stopped. Return to Topics or Home.", cancelled.safeMessage)
        coordinator.retryLoad() // A dead parent cannot run a new load; exit remains safe.
        assertSame(cancelled, coordinator.state.value)
        coordinator.dispose()
        val failing = LocalLessonCoordinator(this, progress, loader()::load, reducer = { _, _ -> error("secret") })
        failing.load("one")
        runCurrent()
        failing.start()
        val session = assertIs<LocalLessonState.Active>(failing.state.value).session
        failing.dispatch(LessonCommand.Next(session.id, session.revision))
        val active = assertIs<LocalLessonState.Active>(failing.state.value)
        assertSame(session, active.session)
        assertEquals("Unable to complete this lesson action. Retry or return to Topics.", active.operationError?.safeMessage)
        failing.leaveAfterError(active.operationError!!)
        assertIs<LocalLessonState.Entry>(failing.state.value)
        failing.dispose()
        progress.dispose()
    }
}
