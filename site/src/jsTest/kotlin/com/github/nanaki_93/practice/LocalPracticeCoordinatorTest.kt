package com.github.nanaki_93.practice

import com.github.nanaki_93.content.BundledContentException
import com.github.nanaki_93.content.BundledContentLoader
import com.github.nanaki_93.content.CatalogLoad
import com.github.nanaki_93.content.ContentTextSource
import com.github.nanaki_93.content.EmptyContentReason
import com.github.nanaki_93.content.ContentHttpException
import com.github.nanaki_93.content.CompletionExercise
import com.github.nanaki_93.content.JapaneseText
import com.github.nanaki_93.content.ProductionExercise
import com.github.nanaki_93.pages.promptAnswer
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

    @Test fun promptControlsMapToTypedCoordinatorSubmissionsWithoutConsumingInvalidAnswers() = runTest {
        val content = seed().content
        val original = content.practiceSets.getValue("practice-kana-a-i")
        val fill = JapaneseText("ます", "ます", translation = "polite ending")
        val completed = JapaneseText("読みます", "よみます", translation = "read")
        val set = original.copy(exercises = original.exercises + listOf(
            CompletionExercise("fill-ending", "Complete the sentence", "読み{blank}", listOf(fill), completed, "Polite ending"),
            ProductionExercise("write-response", "Write a reply", listOf(completed), listOf("Use a polite ending")),
        ))
        val coordinator = LocalPracticeCoordinator(this, { CatalogLoad.Ready(content.copy(practiceSets = mapOf(set.id to set))) })
        coordinator.load()
        runCurrent()
        coordinator.start(set.id)
        fun session() = assertIs<LocalPracticeState.Ready>(coordinator.state.value).session!!
        fun submit(choice: String? = null, draft: String = "", rating: Assessment? = null, revealed: Boolean = false) {
            val current = session()
            val exercise = current.plan[(current.view as SessionView.Prompt).index].exercise
            val answer = promptAnswer(exercise, choice, draft, rating, revealed) ?: return
            coordinator.dispatch(PracticeCommand.Submit(current.id, current.revision, answer))
        }
        fun continueSession() {
            val current = session()
            coordinator.dispatch(PracticeCommand.Continue(current.id, current.revision))
        }
        submit()
        assertEquals(SessionView.Prompt(0, InvalidReason.BLANK_INPUT), session().view)
        assertEquals(0, session().counts.completed)
        submit(choice = "option-kana-a")
        assertEquals(1, session().counts.correct)
        continueSession()
        submit(draft = "  ")
        assertEquals(SessionView.Prompt(1, InvalidReason.BLANK_INPUT), session().view)
        assertEquals(1, session().counts.completed)
        submit(draft = " i ")
        assertEquals(2, session().counts.correct)
        continueSession()
        submit(draft = "読みます") // whole sentence is not the authored fill
        assertEquals(1, session().counts.incorrect)
        continueSession()
        val production = session().plan[3].exercise
        assertNull(promptAnswer(production, null, "読みます", Assessment.NEEDS_PRACTICE, exampleRevealed = false))
        submit(draft = "読みます", rating = Assessment.NEEDS_PRACTICE) // cannot submit before reveal, even with a rating
        assertEquals(SessionView.Prompt(3), session().view)
        assertEquals(3, session().counts.completed)
        submit(draft = "読みます", revealed = true) // example visible, but no rating yet
        assertEquals(SessionView.Prompt(3), session().view)
        submit(draft = " ", rating = Assessment.NEEDS_PRACTICE, revealed = true)
        assertEquals(SessionView.Prompt(3, InvalidReason.BLANK_INPUT), session().view)
        assertEquals(3, session().counts.completed)
        submit(draft = " 読みます ", rating = Assessment.NEEDS_PRACTICE, revealed = true)
        assertEquals(1, session().counts.selfAssessed)
        assertEquals(0, session().counts.revealed) // showing an example is not a graded or resolved outcome
        assertEquals(SessionView.Feedback(3), session().view)
    }

    @Test fun feedbackNavigationIsExplicitGuardedAndReviewIsReadOnly() = runTest {
        val coordinator = LocalPracticeCoordinator(this, BundledContentLoader(seedSource()))
        coordinator.load()
        runCurrent()
        coordinator.start("practice-kana-a-i")
        fun session() = assertIs<LocalPracticeState.Ready>(coordinator.state.value).session!!
        val first = session()
        val skip = PracticeCommand.Skip(first.id, first.revision)
        coordinator.dispatch(skip)
        coordinator.dispatch(skip) // duplicate action from the old render
        assertEquals(SessionView.Feedback(0), session().view)
        assertEquals(1, session().counts.skipped)
        assertEquals(0, session().counts.correct)
        val retry = PracticeCommand.Retry(session().id, session().revision)
        coordinator.dispatch(retry)
        coordinator.dispatch(retry)
        assertEquals(SessionView.Prompt(0), session().view)
        assertEquals(0, session().counts.completed)
        val reveal = PracticeCommand.Reveal(session().id, session().revision)
        coordinator.dispatch(reveal)
        assertEquals(SessionView.Feedback(0), session().view)
        assertEquals(1, session().counts.revealed)
        assertEquals(0, session().counts.correct)
        val advance = PracticeCommand.Continue(session().id, session().revision)
        coordinator.dispatch(advance)
        coordinator.dispatch(advance)
        assertEquals(SessionView.Prompt(1), session().view)
        coordinator.dispatch(PracticeCommand.Previous(session().id, session().revision))
        val review = session()
        assertEquals(SessionView.Review(0, SessionView.Prompt(1)), review.view)
        coordinator.dispatch(PracticeCommand.Submit(review.id, review.revision, PracticeAnswer.Choice("option-kana-a")))
        coordinator.dispatch(PracticeCommand.Retry(review.id, review.revision))
        assertSame(review, session())
        coordinator.dispatch(PracticeCommand.Next(review.id, review.revision))
        assertEquals(SessionView.Prompt(1), session().view)
        coordinator.dispatch(PracticeCommand.Submit(session().id, session().revision, PracticeAnswer.Text("i")))
        assertEquals(SessionView.Feedback(1), session().view)
        coordinator.dispatch(PracticeCommand.Previous(session().id, session().revision))
        coordinator.dispatch(PracticeCommand.Return(session().id, session().revision))
        assertEquals(SessionView.Feedback(1), session().view)
        val finish = PracticeCommand.Continue(session().id, session().revision)
        coordinator.dispatch(finish)
        coordinator.dispatch(finish)
        assertEquals(SessionView.Complete, session().view)
        assertEquals(2, session().counts.completed)
        coordinator.dispatch(PracticeCommand.Previous(session().id, session().revision))
        val lastReview = session()
        assertTrue(lastReview.isComplete)
        assertEquals(SessionView.Review(1, SessionView.Complete), lastReview.view)
        coordinator.dispatch(PracticeCommand.Previous(lastReview.id, lastReview.revision))
        coordinator.dispatch(PracticeCommand.Next(session().id, session().revision))
        coordinator.dispatch(PracticeCommand.Next(session().id, session().revision))
        assertEquals(SessionView.Complete, session().view)
        assertEquals(2, session().counts.completed)
        val old = session()
        val restart = PracticeCommand.Restart(old.id, old.revision, old.id)
        coordinator.dispatch(restart)
        coordinator.dispatch(restart)
        assertTrue(session().id > old.id)
        assertEquals(0, session().counts.completed)
        coordinator.dispatch(PracticeCommand.Leave(old.id, old.revision))
        assertEquals(SessionView.Prompt(0), session().view)
        coordinator.dispatch(PracticeCommand.Leave(session().id, session().revision))
        assertNull(assertIs<LocalPracticeState.Ready>(coordinator.state.value).session)
    }

    @Test fun launcherStartsSelectedReviewedSetWithoutIdentityAndReloadClearsSession() = runTest {
        val seed = seed().content
        val original = seed.practiceSets.getValue("practice-kana-a-i")
        val additional = original.copy(id = "practice-kana-extra", title = "Another reviewed practice")
        val content = seed.copy(practiceSets = seed.practiceSets + (additional.id to additional))
        val coordinator = LocalPracticeCoordinator(this, { CatalogLoad.Ready(content) })
        coordinator.load()
        coordinator.start(original.id) // no start during loading
        assertIs<LocalPracticeState.Loading>(coordinator.state.value)
        runCurrent()
        val ready = assertIs<LocalPracticeState.Ready>(coordinator.state.value)
        assertEquals(listOf(original.id, additional.id), ready.availablePracticeSets.keys.toList())
        coordinator.start(additional.id)
        val session = assertIs<LocalPracticeState.Ready>(coordinator.state.value).session!!
        assertEquals(additional.exercises.map { it.id }, session.plan.map { it.id })
        coordinator.start(original.id) // cannot replace an active session
        assertSame(session, assertIs<LocalPracticeState.Ready>(coordinator.state.value).session)
        coordinator.dispatch(PracticeCommand.Leave(session.id, session.revision))
        assertNull(assertIs<LocalPracticeState.Ready>(coordinator.state.value).session)
        coordinator.start(original.id)
        assertTrue(assertIs<LocalPracticeState.Ready>(coordinator.state.value).session!!.id > session.id)
        coordinator.load() // a page reload starts from content, not a saved session
        assertIs<LocalPracticeState.Loading>(coordinator.state.value)
        runCurrent()
        assertNull(assertIs<LocalPracticeState.Ready>(coordinator.state.value).session)
    }

    @Test fun homeWiresLocalLoadAndDisposalWithoutLegacyInitialization() {
        val fs: dynamic = js("require('fs')")
        val path: dynamic = js("require('path')")
        val file = "site/src/jsMain/kotlin/com/github/nanaki_93/pages/Index.kt"
        var root: String = js("process.cwd()") as String
        while (!(fs.existsSync(path.resolve(root, file)) as Boolean)) {
            val parent = path.dirname(root) as String
            check(parent != root) { "Home source not found" }
            root = parent
        }
        val home = fs.readFileSync(path.resolve(root, file), "utf8") as String
        for (required in listOf("DisposableEffect(coordinator)", "coordinator.load()", "coordinator.dispose()",
            "LocalPracticeState.Loading", "LocalPracticeState.Empty", "LocalPracticeState.Error",
            "LocalPracticeState.Ready", "coordinator::retryLoad", "coordinator.start(set.id)",
            "sets.size == 1", "set.title")) {
            assertTrue(required in home, "Home missing $required")
        }
        for (obsolete in listOf("ConfigLoader", "AuthService", "GameService", "SessionManager",
            "userId", "login", "GameMode", "LevelListRequest", "GameStatistics", "delay(", "launchSafe")) {
            assertTrue(obsolete !in home, "Home still contains $obsolete")
        }
        for (required in listOf("PracticePrompt(session, view, send)", "promptAnswer(exercise, selectedChoice, draft, assessment, exampleRevealed)",
            "PracticeCommand.Submit(session.id, session.revision, answer)", "InputType.Radio", "InputType.Text",
            "TextArea(value = draft", "Legend {", "Label(attrs", "Input(type =", "attr(\"lang\", \"ja\")",
            "TagElement<HTMLElement>(\"ruby\"", "TagElement<HTMLElement>(\"rt\"", "remember(session.id, exercise.id)")) {
            assertTrue(required in home, "Home missing native prompt feature $required")
        }
        val productionUi = home.substringAfter("is ProductionExercise -> {", "").substringBefore("    val validation = view.validation")
        assertTrue("if (!exampleRevealed)" in productionUi && "Reveal example and criteria" in productionUi)
        assertTrue("if (exercise !is ProductionExercise || exampleRevealed)" in home)
        assertTrue("if (exampleRevealed) assessment?.let" in home)
        assertTrue(productionUi.indexOf("Reveal example and criteria") < productionUi.indexOf("Your self-assessment"))
        assertTrue("P { JapanesePassage(example) }" in productionUi, "Authored example must be shown as Japanese text")
        for (unsafe in listOf("innerHTML", "unsafeHTML", "SearchableTextInput", "onKeyDown", "localStorage")) {
            assertTrue(unsafe !in home, "Home prompt contains unsafe or legacy behavior: $unsafe")
        }
        for (required in listOf("SessionCounts(session)", "PracticeFeedback(session, view.index, reviewing = false)",
            "PracticeFeedback(session, view.index, reviewing = true)", "SessionView.Complete ->", "SessionView.Review ->",
            "PracticeCommand.Skip(session.id, session.revision)", "PracticeCommand.Reveal(session.id, session.revision)",
            "PracticeCommand.Continue(session.id, session.revision)", "PracticeCommand.Retry(session.id, session.revision)",
            "PracticeCommand.Previous(session.id, session.revision)", "PracticeCommand.Next(session.id, session.revision)",
            "PracticeCommand.Return(session.id, session.revision)", "PracticeCommand.Restart(session.id, session.revision, session.id)",
            "PracticeCommand.Leave(session.id, session.revision)", "counts.skipped", "counts.revealed", "counts.selfAssessed",
            "is AuthoredFeedback.Choice ->", "is AuthoredFeedback.Reading ->", "is AuthoredFeedback.Completion ->",
            "is AuthoredFeedback.Production ->", "JapaneseFeedbackText(feedback.stimulus)", "JapaneseFeedbackText(feedback.completedExample)",
            "text.translation?.let", "text.gloss?.let", "Session outcomes", "not saved", "not automatically graded")) {
            assertTrue(required in home, "Home missing feedback/navigation feature $required")
        }
        val reviewUi = home.substringAfter("is SessionView.Review -> {").substringBefore("SessionView.Complete -> {")
        for (forbidden in listOf("PracticeCommand.Submit(", "PracticeCommand.Retry(", "PracticeCommand.Continue(", "PracticeCommand.Skip(", "PracticeCommand.Reveal(")) {
            assertTrue(forbidden !in reviewUi, "History must be read-only: $forbidden")
        }
        val styles = fs.readFileSync(path.resolve(root, "site/src/jsMain/kotlin/com/github/nanaki_93/components/styles/JpStyles.kt"), "utf8") as String
        val practiceStyles = styles.substringAfter("registerStyleBase(\".practice-answer\")").substringBefore("registerStyleBase(\".practice-choice\")")
        assertTrue(".outline(" !in practiceStyles, "Native practice fields must retain a visible focus outline")
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

    @Test fun failedFeedbackNavigationRetainsOutcomeUntilExplicitRetryOrLeave() = runTest {
        val valid = seed()
        var fail = true
        val coordinator = LocalPracticeCoordinator(this, { valid }, reducer = { state, command ->
            if (fail && command is PracticeCommand.Continue) error("private navigation failure")
            reduce(state, command)
        })
        coordinator.load()
        runCurrent()
        coordinator.start("practice-kana-a-i")
        fun ready() = assertIs<LocalPracticeState.Ready>(coordinator.state.value)
        val first = ready().session!!
        coordinator.dispatch(PracticeCommand.Reveal(first.id, first.revision))
        val feedback = ready().session!!
        val advance = PracticeCommand.Continue(feedback.id, feedback.revision)
        coordinator.dispatch(advance)
        val error = ready().operationError!!
        assertSame(feedback, ready().session)
        assertEquals(1, ready().session!!.counts.revealed)
        coordinator.dispatch(PracticeCommand.Leave(feedback.id, feedback.revision)) // blocked until retry or leave error
        assertSame(feedback, ready().session)
        fail = false
        coordinator.retryOperation(error)
        assertNull(ready().operationError)
        assertEquals(SessionView.Prompt(1), ready().session!!.view)
        coordinator.dispatch(advance) // old feedback action is stale
        assertEquals(SessionView.Prompt(1), ready().session!!.view)
        val next = ready().session!!
        coordinator.dispatch(PracticeCommand.Skip(next.id, next.revision))
        val lastFeedback = ready().session!!
        fail = true
        coordinator.dispatch(PracticeCommand.Continue(lastFeedback.id, lastFeedback.revision))
        val lastError = ready().operationError!!
        assertSame(lastFeedback, ready().session)
        coordinator.leave(lastError)
        assertNull(ready().session)
        assertNull(ready().operationError)
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
