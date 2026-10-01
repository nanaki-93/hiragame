package com.github.nanaki_93.practice

import com.github.nanaki_93.content.BundledContentException
import com.github.nanaki_93.content.BundledContentLoader
import com.github.nanaki_93.content.CatalogLoad
import com.github.nanaki_93.content.ContentTextSource
import com.github.nanaki_93.content.EmptyContentReason
import com.github.nanaki_93.content.ContentHttpException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LocalPracticeCoordinatorTest {
    /** Node-only fake: reads canonical source files, not a packaged artifact or HTTP endpoint. */
    private val sourceRoot = "site/src/jsMain/resources/public/content/"
    private fun seedSource(): ContentTextSource = object : ContentTextSource {
        override suspend fun readText(relativePath: String): String {
            val fs: dynamic = js("require('fs')")
            val path: dynamic = js("require('path')")
            val cwd: String = js("process.cwd()") as String
            var root = cwd
            while (!(fs.existsSync(path.resolve(root, sourceRoot, "catalog.json")) as Boolean)) {
                val parent = path.dirname(root) as String
                check(parent != root) { "Canonical content source tree not found above $cwd" }
                root = parent
            }
            return fs.readFileSync(path.resolve(root, sourceRoot, relativePath), "utf8") as String
        }
    }

    private suspend fun seed(): CatalogLoad.Ready =
        assertIs<CatalogLoad.Ready>(BundledContentLoader(seedSource()).load())

    @Test fun canonicalTwoItemSessionRequiresExplicitContinue() = runTest {
        val coordinator = LocalPracticeCoordinator(this, BundledContentLoader(seedSource()))
        coordinator.load()
        runCurrent()
        val ready = assertIs<LocalPracticeState.Ready>(coordinator.state.value)
        assertEquals(listOf("practice-kana-a-i"), ready.availablePracticeSets.keys.toList())
        assertEquals(listOf("lesson-confirm-meeting-time"), ready.content.lessons.keys.toList())
        coordinator.start("practice-kana-a-i")
        var session = assertIs<LocalPracticeState.Ready>(coordinator.state.value).session!!
        assertEquals(listOf("exercise-kana-a-choice", "exercise-kana-i-reading"), session.plan.map { it.id })
        val id = session.id
        coordinator.dispatch(PracticeCommand.Submit(id, session.revision, PracticeAnswer.Choice("missing")))
        session = assertIs<LocalPracticeState.Ready>(coordinator.state.value).session!!
        assertEquals(SessionView.Prompt(0, InvalidReason.UNKNOWN_CHOICE), session.view)
        assertEquals(0, session.counts.completed)
        coordinator.dispatch(PracticeCommand.Submit(id, session.revision, PracticeAnswer.Choice("option-kana-a")))
        session = assertIs<LocalPracticeState.Ready>(coordinator.state.value).session!!
        assertEquals(SessionView.Feedback(0), session.view)
        assertEquals(1, session.counts.correct)
        coordinator.dispatch(PracticeCommand.Continue(id, session.revision - 1)) // stale callback
        assertSame(session, assertIs<LocalPracticeState.Ready>(coordinator.state.value).session)
        coordinator.dispatch(PracticeCommand.Continue(id, session.revision))
        session = assertIs<LocalPracticeState.Ready>(coordinator.state.value).session!!
        assertEquals(SessionView.Prompt(1), session.view)
        coordinator.dispatch(PracticeCommand.Submit(id, session.revision, PracticeAnswer.Text(" i ")))
        session = assertIs<LocalPracticeState.Ready>(coordinator.state.value).session!!
        assertEquals(SessionView.Feedback(1), session.view)
        assertEquals(2, session.counts.correct)
        assertTrue(!session.isComplete)
        coordinator.dispatch(PracticeCommand.Continue(id, session.revision))
        session = assertIs<LocalPracticeState.Ready>(coordinator.state.value).session!!
        assertEquals(SessionView.Complete, session.view)
        assertEquals(2, session.counts.completed)
        coordinator.dispatch(PracticeCommand.Previous(id, session.revision))
        assertIs<SessionView.Review>(assertIs<LocalPracticeState.Ready>(coordinator.state.value).session!!.view)
        coordinator.dispatch(PracticeCommand.Return(id, session.revision + 1))
        assertEquals(SessionView.Complete, assertIs<LocalPracticeState.Ready>(coordinator.state.value).session!!.view)
    }

    @Test fun failedLoadCanRetryAndEmptyCanReload() = runTest {
        val valid = seed()
        var attempts = 0
        val coordinator = LocalPracticeCoordinator(this, {
            if (++attempts == 1) throw BundledContentException("catalog.json", "untrusted server body")
            valid
        })
        coordinator.load()
        runCurrent()
        val error = assertIs<LocalPracticeState.Error>(coordinator.state.value)
        assertEquals(LoadErrorKind.CONTENT, error.kind)
        assertEquals("catalog.json", error.affectedPath)
        assertTrue(!error.safeMessage.contains("untrusted"))
        coordinator.retryLoad()
        assertIs<LocalPracticeState.Loading>(coordinator.state.value)
        runCurrent()
        assertIs<LocalPracticeState.Ready>(coordinator.state.value)
        assertEquals(2, attempts)
        coordinator.retryLoad() // no accidental reload from ready
        assertEquals(2, attempts)
        var empty = true
        val other = LocalPracticeCoordinator(this, {
            if (empty) CatalogLoad.Empty(EmptyContentReason.NO_PRACTICE) else valid
        })
        other.load()
        runCurrent()
        assertEquals(EmptyContentReason.NO_PRACTICE, assertIs<LocalPracticeState.Empty>(other.state.value).reason)
        empty = false
        other.retryLoad()
        runCurrent()
        assertIs<LocalPracticeState.Ready>(other.state.value)
    }

    @Test fun loadErrorsExposeTypedKindWithoutExaminingDiagnosticText() = runTest {
        val failures = listOf(
            BundledContentException("practice/broken.json", "untrusted", ContentHttpException(503)) to LoadErrorKind.HTTP,
            BundledContentException("practice/broken.json", "untrusted") to LoadErrorKind.CONTENT,
            IllegalStateException("untrusted") to LoadErrorKind.UNEXPECTED,
        )
        for ((failure, expected) in failures) {
            val coordinator = LocalPracticeCoordinator(this, { throw failure })
            coordinator.load()
            runCurrent()
            val error = assertIs<LocalPracticeState.Error>(coordinator.state.value)
            assertEquals(expected, error.kind)
            assertEquals(if (failure is BundledContentException) "practice/broken.json" else null, error.affectedPath)
            assertTrue(!error.safeMessage.contains("untrusted"))
        }
    }

    @Test fun replacedLoadsAreCancelledAndLateResultsCannotOverwriteSuccessOrFailure() = runTest {
        val valid = seed()
        val release = CompletableDeferred<CatalogLoad>()
        var cancelled = 0
        var attempts = 0
        val coordinator = LocalPracticeCoordinator(this, {
            if (++attempts == 1) {
                try { withContext(NonCancellable) { release.await() } }
                finally { cancelled++ }
            } else valid
        })
        coordinator.load()
        runCurrent()
        coordinator.load()
        runCurrent()
        assertIs<LocalPracticeState.Ready>(coordinator.state.value)
        release.complete(CatalogLoad.Empty(EmptyContentReason.EMPTY_CATALOG))
        runCurrent()
        assertIs<LocalPracticeState.Ready>(coordinator.state.value)
        assertEquals(1, cancelled)

        val lateFailure = CompletableDeferred<Unit>()
        attempts = 0
        val second = LocalPracticeCoordinator(this, {
            if (++attempts == 1) {
                withContext(NonCancellable) { lateFailure.await() }
                error("late failure")
            } else valid
        })
        second.load()
        runCurrent()
        second.load()
        runCurrent()
        lateFailure.complete(Unit)
        runCurrent()
        assertIs<LocalPracticeState.Ready>(second.state.value)
    }

    @Test fun disposedLoadCannotPublishEvenIfItsSourceIgnoresCancellation() = runTest {
        val release = CompletableDeferred<Unit>()
        val valid = seed()
        val coordinator = LocalPracticeCoordinator(this, {
            withContext(NonCancellable) { release.await() }
            valid
        })
        coordinator.load()
        runCurrent()
        coordinator.dispose()
        release.complete(Unit)
        runCurrent()
        assertIs<LocalPracticeState.Loading>(coordinator.state.value)
        coordinator.load()
        assertIs<LocalPracticeState.Loading>(coordinator.state.value)
    }

    @Test fun invalidActionsAndEvaluatorFailuresRetainSessionWithRetryOrLeave() = runTest {
        val valid = seed()
        var fail = true
        val coordinator = LocalPracticeCoordinator(this, { valid }, reducer = { state, command ->
            if (fail && command is PracticeCommand.Submit) error("private evaluator diagnostic")
            reduce(state, command)
        })
        coordinator.load()
        runCurrent()
        coordinator.start("practice-kana-a-i")
        val before = assertIs<LocalPracticeState.Ready>(coordinator.state.value).session!!
        val command = PracticeCommand.Submit(before.id, before.revision, PracticeAnswer.Choice("option-kana-a"))
        coordinator.dispatch(command)
        val broken = assertIs<LocalPracticeState.Ready>(coordinator.state.value)
        assertSame(before, broken.session)
        assertEquals(0, broken.session!!.counts.completed)
        assertEquals(PracticeOperation.DISPATCH, broken.operationError!!.operation)
        assertTrue(!broken.operationError.safeMessage.contains("private"))
        coordinator.dispatch(command) // only the explicit retry may repeat a failed action
        assertSame(before, assertIs<LocalPracticeState.Ready>(coordinator.state.value).session)
        fail = false
        coordinator.retryOperation(broken.operationError)
        val recovered = assertIs<LocalPracticeState.Ready>(coordinator.state.value)
        assertNull(recovered.operationError)
        assertEquals(SessionView.Feedback(0), recovered.session!!.view)
        coordinator.dispatch(command)
        assertEquals(1, assertIs<LocalPracticeState.Ready>(coordinator.state.value).session!!.counts.completed)
        coordinator.dispatch(PracticeCommand.Leave(before.id, recovered.session.revision))
        assertNull(assertIs<LocalPracticeState.Ready>(coordinator.state.value).session)
        coordinator.start("practice-kana-a-i")
        val fresh = assertIs<LocalPracticeState.Ready>(coordinator.state.value).session!!
        assertTrue(fresh.id > before.id)
        coordinator.dispatch(command)
        assertSame(fresh, assertIs<LocalPracticeState.Ready>(coordinator.state.value).session)
    }

    @Test fun failedStartCanRetryAndLeaveAndRestartUsesCoordinatorIdentity() = runTest {
        val valid = seed()
        var fail = true
        val coordinator = LocalPracticeCoordinator(this, { valid }, sessionFactory = { id, set, limit ->
            if (fail) error("private factory diagnostic")
            startSession(id, set, limit)
        })
        coordinator.load()
        runCurrent()
        coordinator.start("practice-kana-a-i")
        val error = assertIs<LocalPracticeState.Ready>(coordinator.state.value)
        assertNull(error.session)
        assertEquals(PracticeOperation.START, error.operationError!!.operation)
        coordinator.leave(error.operationError)
        assertNull(assertIs<LocalPracticeState.Ready>(coordinator.state.value).operationError)
        coordinator.start("practice-kana-a-i")
        val secondError = assertIs<LocalPracticeState.Ready>(coordinator.state.value).operationError!!
        fail = false
        coordinator.retryOperation(error.operationError) // stale error action must not affect a later failure
        assertNull(assertIs<LocalPracticeState.Ready>(coordinator.state.value).session)
        coordinator.leave(error.operationError)
        assertEquals(secondError, assertIs<LocalPracticeState.Ready>(coordinator.state.value).operationError)
        coordinator.retryOperation(secondError)
        var session = assertIs<LocalPracticeState.Ready>(coordinator.state.value).session!!
        val oldId = session.id
        coordinator.dispatch(PracticeCommand.Restart(oldId, session.revision, oldId))
        session = assertIs<LocalPracticeState.Ready>(coordinator.state.value).session!!
        assertTrue(session.id > oldId)
        assertEquals(0, session.counts.completed)
        coordinator.dispatch(PracticeCommand.Restart(oldId, 0, Long.MAX_VALUE))
        assertSame(session, assertIs<LocalPracticeState.Ready>(coordinator.state.value).session)
    }
}
