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
import com.github.nanaki_93.pages.saveStatusMessage
import com.github.nanaki_93.pages.selectColorMode
import com.github.nanaki_93.storage.LocalProgressOwner
import com.github.nanaki_93.storage.MemoryProgressStore
import com.github.nanaki_93.storage.ProgressStore
import com.github.nanaki_93.storage.StoreReadResult
import com.github.nanaki_93.storage.StoreWriteResult
import com.github.nanaki_93.storage.StoreSubscription
import com.github.nanaki_93.storage.StoreFailure
import com.github.nanaki_93.storage.PersistenceStatus
import com.github.nanaki_93.storage.ProgressMutationResult
import com.github.nanaki_93.progress.CheckpointView
import com.github.nanaki_93.progress.SavedColorMode
import com.github.nanaki_93.progress.SaveProblem
import com.github.nanaki_93.progress.LessonProgress
import com.github.nanaki_93.progress.LessonStage
import com.github.nanaki_93.progress.ProgressUpdate
import com.github.nanaki_93.progress.ReviewItemProgress
import com.github.nanaki_93.progress.ReviewOutcome
import com.github.nanaki_93.progress.SaveCodec
import com.github.nanaki_93.progress.SaveDecodeResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CoroutineScope
import com.github.nanaki_93.content.PracticeSet
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

    private var snapshotSequence = 0
    private fun owner(store: ProgressStore = MemoryProgressStore()): LocalProgressOwner =
        LocalProgressOwner(store, { 1_000L }, { "snapshot_${++snapshotSequence}" })

    // Existing reducer tests also run against an isolated fake owner without browser globals.
    private fun coordinator(
        scope: CoroutineScope, load: suspend () -> CatalogLoad,
        progress: LocalProgressOwner = owner(),
        reducer: (PracticeSession, PracticeCommand) -> PracticeSession = ::reduce,
        sessionFactory: (Long, PracticeSet, Int) -> PracticeSession = ::startSession,
    ) = LocalPracticeCoordinator(scope, progress, load, reducer, sessionFactory, now = { 1_000L })

    private fun coordinator(scope: CoroutineScope, loader: BundledContentLoader, progress: LocalProgressOwner = owner()) =
        coordinator(scope, loader::load, progress)

    private class CountingStore(val delegate: MemoryProgressStore = MemoryProgressStore()) : ProgressStore {
        var writes = 0
        override fun read(): StoreReadResult = delegate.read()
        override fun write(expectedRaw: String?, replacementRaw: String): StoreWriteResult {
            writes++
            return delegate.write(expectedRaw, replacementRaw)
        }
        override fun subscribe(onExternalChange: () -> Unit): StoreSubscription = delegate.subscribe(onExternalChange)
    }

    private suspend fun seed(): CatalogLoad.Ready =
        assertIs<CatalogLoad.Ready>(BundledContentLoader(seedSource()).load())

    @Test fun canonicalTwoItemSessionRequiresExplicitContinue() = runTest {
        val coordinator = coordinator(this, BundledContentLoader(seedSource()))
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
        val coordinator = coordinator(this, { CatalogLoad.Ready(content.copy(practiceSets = mapOf(set.id to set))) })
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
        val coordinator = coordinator(this, BundledContentLoader(seedSource()))
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
        val coordinator = coordinator(this, { CatalogLoad.Ready(content) })
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

    @Test fun colorModeSelectionWritesOnlyChangesAndAppliesAcceptedChoices() {
        val store = CountingStore()
        val progress = owner(store)
        val applied = mutableListOf<SavedColorMode>()
        fun select(mode: SavedColorMode): ProgressMutationResult = selectColorMode(progress, mode) {
            applied += progress.state.value.snapshot.preferences.colorMode
        }
        assertEquals(ProgressMutationResult.Unchanged, select(SavedColorMode.SYSTEM))
        assertEquals(0, store.writes)
        assertTrue(applied.isEmpty())
        assertIs<PersistenceStatus.Fresh>(progress.state.value.status)
        assertEquals(ProgressMutationResult.Accepted, select(SavedColorMode.DARK))
        assertEquals(1, store.writes)
        assertEquals(listOf(SavedColorMode.DARK), applied)
        assertIs<PersistenceStatus.Saved>(progress.state.value.status)
        assertEquals(ProgressMutationResult.Unchanged, select(SavedColorMode.DARK))
        assertEquals(1, store.writes)
        assertEquals(ProgressMutationResult.Accepted, select(SavedColorMode.LIGHT))
        assertEquals(2, store.writes)
        assertEquals(ProgressMutationResult.Accepted, select(SavedColorMode.SYSTEM))
        assertEquals(3, store.writes)
        assertEquals(listOf(SavedColorMode.DARK, SavedColorMode.LIGHT, SavedColorMode.SYSTEM), applied)
        assertEquals(SavedColorMode.SYSTEM,
            assertIs<SaveDecodeResult.Valid>(SaveCodec.decodeSave(store.delegate.backing.raw!!)).snapshot.preferences.colorMode)
    }

    @Test fun colorModeFailureAndRetryNeverClaimPrematureSave() {
        val store = CountingStore()
        val progress = owner(store)
        store.delegate.writeFailure = StoreFailure.QUOTA
        var applied = 0
        assertEquals(ProgressMutationResult.Accepted, selectColorMode(progress, SavedColorMode.DARK) { applied++ })
        assertEquals(1, applied) // the chosen mode works in this view even if storage rejects it
        assertEquals(1, store.writes)
        assertNull(store.delegate.backing.raw)
        assertEquals(SavedColorMode.DARK, progress.state.value.snapshot.preferences.colorMode)
        assertTrue("Changes only in memory" in saveStatusMessage(progress.state.value))
        assertTrue("Saved in this browser" !in saveStatusMessage(progress.state.value))
        assertEquals(ProgressMutationResult.Unchanged, selectColorMode(progress, SavedColorMode.DARK) { applied++ })
        assertEquals(1, store.writes)
        assertEquals(1, applied)
        store.delegate.writeFailure = null
        progress.retrySaving()
        assertEquals(2, store.writes)
        assertIs<PersistenceStatus.Saved>(progress.state.value.status)
        assertTrue("Saved in this browser" in saveStatusMessage(progress.state.value))
        assertTrue("latest change was not retained" in saveStatusMessage(
            progress.state.value.copy(rejectedUpdate = SaveProblem.OVERSIZED)))
        assertEquals(SavedColorMode.DARK,
            assertIs<SaveDecodeResult.Valid>(SaveCodec.decodeSave(store.delegate.backing.raw!!)).snapshot.preferences.colorMode)
    }

    @Test fun protectedAndConflictedSavesDoNotClaimPersistenceForPreferences() {
        val protectedStore = CountingStore(MemoryProgressStore(
            com.github.nanaki_93.storage.MemoryProgressBacking("unreadable save")))
        val protected = owner(protectedStore)
        assertTrue("Saving paused" in saveStatusMessage(protected.state.value))
        assertTrue("unsupported version" in saveStatusMessage(protected.state.value.copy(
            status = PersistenceStatus.Protected(SaveProblem.UNSUPPORTED_VERSION))))
        assertEquals(ProgressMutationResult.Accepted, selectColorMode(protected, SavedColorMode.LIGHT) {})
        assertEquals(0, protectedStore.writes)
        assertEquals("unreadable save", protectedStore.delegate.backing.raw)
        val shared = com.github.nanaki_93.storage.MemoryProgressBacking()
        val store = CountingStore(MemoryProgressStore(shared))
        val first = owner(store)
        val second = owner(MemoryProgressStore(shared))
        second.mutate { com.github.nanaki_93.progress.changePreferences(it, it.preferences.copy(colorMode = SavedColorMode.DARK)) }
        assertIs<PersistenceStatus.Conflict>(first.state.value.status)
        assertTrue("Saving paused" in saveStatusMessage(first.state.value))
        assertEquals(ProgressMutationResult.Accepted, selectColorMode(first, SavedColorMode.LIGHT) {})
        assertEquals(0, store.writes)
        assertEquals(SavedColorMode.DARK,
            assertIs<SaveDecodeResult.Valid>(SaveCodec.decodeSave(shared.raw!!)).snapshot.preferences.colorMode)
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
            "text.translation?.let", "text.gloss?.let", "Session outcomes", "typed responses are not retained", "not automatically graded")) {
            assertTrue(required in home, "Home missing feedback/navigation feature $required")
        }
        val saveUi = home.substringAfter("private fun SavePreferencesSection(").substringBefore("/** Keep raw drafts")
        for (required in listOf("progress.state.collectAsState()", "H2 { Text(\"Save & preferences\") }",
            "saveStatusMessage(saved)", "PersistenceStatus.MemoryOnly", "progress.retrySaving()",
            "Fieldset {", "Legend { Text(\"Color mode\") }", "InputType.Radio", "Label(attrs",
            "SavedColorMode.SYSTEM", "SavedColorMode.LIGHT", "SavedColorMode.DARK",
            "selectColorMode(progress, mode)", "colorModeState.value = initialSilkMode(progress)")) {
            assertTrue(required in saveUi, "Home missing save/preference control: $required")
        }
        assertTrue(home.indexOf("SavePreferencesSection(progress)") > home.indexOf("when (val current = state)"),
            "Save status must render independently after every content load state")
        assertTrue("not saved" !in home && "lost when" !in home, "Home still claims checkpoints are never saved")
        assertTrue("ColorMode.current == ColorMode.DARK" in home &&
            "Modifier.backgroundColor(Colors.DarkBackground)" in home &&
            "Modifier.backgroundColor(Colors.DarkCardBackground).color(Colors.DarkText)" in home,
            "Selecting a mode must visibly change the opaque Home surfaces, not just the Silk background")
        val reviewUi = home.substringAfter("is SessionView.Review -> {").substringBefore("SessionView.Complete -> {")
        for (forbidden in listOf("PracticeCommand.Submit(", "PracticeCommand.Retry(", "PracticeCommand.Continue(", "PracticeCommand.Skip(", "PracticeCommand.Reveal(")) {
            assertTrue(forbidden !in reviewUi, "History must be read-only: $forbidden")
        }
        val styles = fs.readFileSync(path.resolve(root, "site/src/jsMain/kotlin/com/github/nanaki_93/components/styles/JpStyles.kt"), "utf8") as String
        val practiceStyles = styles.substringAfter("registerStyleBase(\".practice-answer\")").substringBefore("registerStyleBase(\".practice-choice\")")
        assertTrue(".outline(" !in practiceStyles, "Native practice fields must retain a visible focus outline")
        val saveStyles = styles.substringAfter("registerStyleBase(\".save-preferences\")").substringBefore("object Colors")
        assertTrue("FlexWrap.Wrap" in saveStyles && ".outline(" !in saveStyles,
            "Native save controls need responsive wrapping and visible focus outlines")
        val buttons = fs.readFileSync(path.resolve(root, "site/src/jsMain/kotlin/com/github/nanaki_93/components/widgets/BaseComponents.kt"), "utf8") as String
        val secondary = buttons.substringAfter("fun SecondaryButton(").substringBefore("// Simplified Action Button")
        assertTrue("Styles.ButtonSecondary.toModifier().then(" in secondary &&
            "ColorMode.current == ColorMode.DARK" in secondary &&
            "Modifier.color(Colors.DarkText).border(2.px, LineStyle.Solid, Colors.DarkBorder)" in secondary &&
            ".outline(" !in secondary,
            "Secondary actions, including Retry saving, need legible text/borders on dark cards without removing focus")
    }

    @Test fun activePagesAreLocalOnlyAndLegacyConfigurationIsRetired() {
        val fs: dynamic = js("require('fs')")
        val path: dynamic = js("require('path')")
        val pages = "site/src/jsMain/kotlin/com/github/nanaki_93/pages/"
        var root: String = js("process.cwd()") as String
        while (!(fs.existsSync(path.resolve(root, pages, "Login.kt")) as Boolean)) {
            val parent = path.dirname(root) as String
            check(parent != root) { "Active page sources not found" }
            root = parent
        }
        val forbiddenImports = Regex("^import\\s+com\\.github\\.nanaki_93\\.(?:config|service|models|util\\.launchSafe)(?:\\.|$)")
        for (page in listOf("Index.kt", "Login.kt")) {
            val source = fs.readFileSync(path.resolve(root, pages, page), "utf8") as String
            for (line in source.lines()) {
                assertTrue(!forbiddenImports.containsMatchIn(line), "$page imports a legacy runtime dependency: $line")
            }
            for (legacy in listOf("ConfigLoader", "AuthService", "GameService", "SessionManager", "launchSafe",
                "AppConfig", "GameMode", "LevelListRequest", "GameStatistics")) {
                assertTrue(legacy !in source, "$page uses $legacy")
            }
        }
        val login = fs.readFileSync(path.resolve(root, pages, "Login.kt"), "utf8") as String
        assertTrue("@Page(\"/login\")" in login)
        assertTrue("No account required" in login)
        assertTrue("href = \"/hiragame/\"" in login)
        for (legacy in listOf("window.location", "window.fetch", "LaunchedEffect", "register(", "login(", "logout(",
            "FormField", "SessionExpiredAlert")) {
            assertTrue(legacy !in login, "Login route still performs legacy behavior: $legacy")
        }
        for (resource in listOf("config.json", "config.prod.json", "public/config.json", "public/config.prod.json")) {
            assertTrue(!(fs.existsSync(path.resolve(root, "site/src/jsMain/resources", resource)) as Boolean),
                "Obsolete API configuration still exists: $resource")
        }
        assertTrue(!(fs.existsSync(path.resolve(root, "site/src/jsMain/kotlin/com/github/nanaki_93/config/Config.kt")) as Boolean),
            "ConfigLoader still exists")
    }

    @Test fun failedLoadCanRetryAndEmptyCanReload() = runTest {
        val valid = seed()
        var attempts = 0
        val coordinator = coordinator(this, {
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
        val other = coordinator(this, {
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
            val coordinator = coordinator(this, { throw failure })
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
        val coordinator = coordinator(this, {
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
        val second = coordinator(this, {
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
        val coordinator = coordinator(this, {
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
        val coordinator = coordinator(this, { valid }, reducer = { state, command ->
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
        val coordinator = coordinator(this, { valid }, reducer = { state, command ->
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

    @Test fun onlyCommittedFrontiersWriteAndRestartPreservesOtherProgress() = runTest {
        val content = seed().content
        val store = CountingStore()
        val progress = owner(store)
        val lesson = LessonProgress("unknown-lesson", 1, 1000, LessonStage.SITUATION)
        val review = ReviewItemProgress("unknown-item", "unknown-document", ReviewOutcome.GOOD, 1000,
            lastActionToken = "review-token")
        assertEquals(com.github.nanaki_93.storage.ProgressMutationResult.Accepted, progress.mutate {
            ProgressUpdate.Applied(it.copy(lessonProgress = listOf(lesson), reviewItems = listOf(review)))
        })
        val coordinator = coordinator(this, { CatalogLoad.Ready(content) }, progress)
        coordinator.load()
        runCurrent()
        val initial = store.writes
        assertEquals(1, initial) // seeded unrelated progress, not content load
        coordinator.start("missing-set")
        assertEquals(initial, store.writes)
        coordinator.start("practice-kana-a-i")
        fun session() = assertIs<LocalPracticeState.Ready>(coordinator.state.value).session!!
        fun checkpoint() = progress.state.value.snapshot.practiceProgress.single()
        assertEquals(initial + 1, store.writes)
        assertEquals(CheckpointView.PROMPT, checkpoint().view)
        val first = session()
        coordinator.start(first.setId)
        coordinator.dispatch(PracticeCommand.Submit(first.id, first.revision, PracticeAnswer.Choice("missing")))
        assertEquals(initial + 1, store.writes) // invalid input changes UI validation, not save
        val valid = session()
        val submit = PracticeCommand.Submit(valid.id, valid.revision, PracticeAnswer.Choice("option-kana-a"))
        coordinator.dispatch(submit)
        coordinator.dispatch(submit)
        assertEquals(initial + 2, store.writes)
        assertEquals(CheckpointView.FEEDBACK, checkpoint().view)
        val revision = progress.state.value.snapshot.revision
        coordinator.dispatch(PracticeCommand.Previous(session().id, session().revision)) // first feedback: no history
        assertEquals(revision, progress.state.value.snapshot.revision)
        val retry = PracticeCommand.Retry(session().id, session().revision)
        coordinator.dispatch(retry)
        coordinator.dispatch(retry)
        assertEquals(initial + 3, store.writes)
        assertEquals(emptyList(), checkpoint().outcomes)
        val skip = PracticeCommand.Skip(session().id, session().revision)
        coordinator.dispatch(skip)
        coordinator.dispatch(skip)
        assertEquals(initial + 4, store.writes)
        val advance = PracticeCommand.Continue(session().id, session().revision)
        coordinator.dispatch(advance)
        coordinator.dispatch(advance)
        assertEquals(initial + 5, store.writes)
        assertEquals(CheckpointView.PROMPT, checkpoint().view)
        coordinator.dispatch(PracticeCommand.Previous(session().id, session().revision))
        coordinator.dispatch(PracticeCommand.Next(session().id, session().revision))
        assertEquals(initial + 5, store.writes) // history navigation is not a transition
        val reveal = PracticeCommand.Reveal(session().id, session().revision)
        coordinator.dispatch(reveal)
        assertEquals(initial + 6, store.writes)
        val finish = PracticeCommand.Continue(session().id, session().revision)
        coordinator.dispatch(finish)
        coordinator.dispatch(finish)
        assertEquals(initial + 7, store.writes)
        assertEquals(CheckpointView.COMPLETE, checkpoint().view)
        val completedAt = checkpoint().lastCompletedAtEpochMs
        coordinator.dispatch(PracticeCommand.Previous(session().id, session().revision))
        coordinator.dispatch(PracticeCommand.Return(session().id, session().revision))
        assertEquals(initial + 7, store.writes)
        val old = session()
        val restart = PracticeCommand.Restart(old.id, old.revision, old.id)
        coordinator.dispatch(restart)
        coordinator.dispatch(restart)
        assertEquals(initial + 8, store.writes)
        assertEquals(CheckpointView.PROMPT, checkpoint().view)
        assertEquals(completedAt, checkpoint().lastCompletedAtEpochMs)
        assertEquals(listOf(lesson), progress.state.value.snapshot.lessonProgress)
        assertEquals(listOf(review), progress.state.value.snapshot.reviewItems)
        val latest = checkpoint()
        coordinator.dispatch(PracticeCommand.Leave(session().id, session().revision))
        assertEquals(initial + 8, store.writes)
        assertEquals(latest, checkpoint())
        coordinator.load() // content reload does not erase the stored checkpoint or auto-resume
        runCurrent()
        assertNull(assertIs<LocalPracticeState.Ready>(coordinator.state.value).session)
        assertEquals(latest, checkpoint())
        assertEquals(progress.state.value.snapshot, assertIs<SaveDecodeResult.Valid>(SaveCodec.decodeSave(store.delegate.backing.raw!!)).snapshot)
    }

    @Test fun acceptedActionsRemainInMemoryAfterQuotaFailureWithoutRepeatedWrites() = runTest {
        val content = seed().content
        val store = CountingStore()
        val progress = owner(store)
        val coordinator = coordinator(this, { CatalogLoad.Ready(content) }, progress)
        coordinator.load()
        runCurrent()
        coordinator.start("practice-kana-a-i")
        val storedStart = store.delegate.backing.raw!!
        val revision = progress.state.value.snapshot.revision
        store.delegate.writeFailure = StoreFailure.QUOTA
        var session = assertIs<LocalPracticeState.Ready>(coordinator.state.value).session!!
        coordinator.dispatch(PracticeCommand.Skip(session.id, session.revision))
        assertEquals(2, store.writes)
        assertIs<PersistenceStatus.MemoryOnly>(progress.state.value.status)
        session = assertIs<LocalPracticeState.Ready>(coordinator.state.value).session!!
        coordinator.dispatch(PracticeCommand.Continue(session.id, session.revision))
        assertEquals(2, store.writes)
        assertEquals(revision, progress.state.value.snapshot.revision)
        assertEquals(storedStart, store.delegate.backing.raw)
        assertEquals(CheckpointView.PROMPT, progress.state.value.snapshot.practiceProgress.single().view)
        store.delegate.writeFailure = null
        progress.retrySaving()
        assertEquals(3, store.writes)
        assertEquals(revision + 1, progress.state.value.snapshot.revision)
        assertEquals(progress.state.value.snapshot,
            assertIs<SaveDecodeResult.Valid>(SaveCodec.decodeSave(store.delegate.backing.raw!!)).snapshot)
    }

    @Test fun startingAndRestartingOneSetDoesNotReplaceOtherSet() = runTest {
        val content = seed().content
        val set = content.practiceSets.getValue("practice-kana-a-i")
        val second = set.copy(id = "practice-kana-extra")
        val store = CountingStore()
        val progress = owner(store)
        val coordinator = coordinator(this, { CatalogLoad.Ready(content.copy(practiceSets = content.practiceSets + (second.id to second))) }, progress)
        coordinator.load()
        runCurrent()
        coordinator.start(second.id)
        val first = assertIs<LocalPracticeState.Ready>(coordinator.state.value).session!!
        coordinator.dispatch(PracticeCommand.Skip(first.id, first.revision))
        val otherRecord = progress.state.value.snapshot.practiceProgress.single()
        coordinator.dispatch(PracticeCommand.Leave(first.id, first.revision + 1))
        assertEquals(2, store.writes)
        coordinator.start(set.id)
        var current = assertIs<LocalPracticeState.Ready>(coordinator.state.value).session!!
        coordinator.dispatch(PracticeCommand.Restart(current.id, current.revision, current.id))
        current = assertIs<LocalPracticeState.Ready>(coordinator.state.value).session!!
        assertEquals(4, store.writes)
        assertEquals(setOf(second.id, set.id), progress.state.value.snapshot.practiceProgress.map { it.setId }.toSet())
        assertEquals(otherRecord, progress.state.value.snapshot.practiceProgress.first { it.setId == second.id })
        assertEquals(0, current.counts.completed)
    }

    @Test fun failedReducerAndProductionDraftsNeverWrite() = runTest {
        val content = seed().content
        val original = content.practiceSets.getValue("practice-kana-a-i")
        val production = ProductionExercise("write-response", "Write", listOf(JapaneseText("はい", "はい", translation = "yes")), listOf("Be polite"))
        val set = original.copy(exercises = listOf(production))
        val store = CountingStore()
        val progress = owner(store)
        var fail = true
        val coordinator = coordinator(this, { CatalogLoad.Ready(content.copy(practiceSets = mapOf(set.id to set))) },
            progress, reducer = { session, command ->
                if (fail && command is PracticeCommand.Submit) error("private failure")
                reduce(session, command)
            })
        coordinator.load()
        runCurrent()
        coordinator.start(set.id)
        assertEquals(1, store.writes)
        val first = assertIs<LocalPracticeState.Ready>(coordinator.state.value).session!!
        // Revealing the example in the prompt is UI-local; no reducer command is dispatched.
        assertNull(promptAnswer(production, null, "はい", Assessment.MET_CRITERIA, false))
        assertEquals(1, store.writes)
        val command = PracticeCommand.Submit(first.id, first.revision,
            PracticeAnswer.SelfAssessment("private response", Assessment.MET_CRITERIA))
        coordinator.dispatch(command)
        assertEquals(1, store.writes)
        fail = false
        coordinator.retryOperation(assertIs<LocalPracticeState.Ready>(coordinator.state.value).operationError!!)
        assertEquals(2, store.writes)
        coordinator.dispatch(command)
        assertEquals(2, store.writes)
        assertTrue("private response" !in store.delegate.backing.raw!!)
    }

    @Test fun failedStartCanRetryAndLeaveAndRestartUsesCoordinatorIdentity() = runTest {
        val valid = seed()
        var fail = true
        val coordinator = coordinator(this, { valid }, sessionFactory = { id, set, limit ->
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
