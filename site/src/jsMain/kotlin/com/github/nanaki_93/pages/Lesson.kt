package com.github.nanaki_93.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import com.github.nanaki_93.LocalProgress
import com.github.nanaki_93.components.styles.Colors
import com.github.nanaki_93.components.styles.Styles
import com.github.nanaki_93.components.widgets.AuthoredFeedbackContent
import com.github.nanaki_93.components.widgets.JapaneseAnswerInput
import com.github.nanaki_93.components.widgets.JapanesePassage
import com.github.nanaki_93.components.widgets.JapaneseResponseArea
import com.github.nanaki_93.components.widgets.JapaneseSubmissionGuard
import com.github.nanaki_93.components.widgets.JapaneseStudyText
import com.github.nanaki_93.components.widgets.PreviewJapaneseText
import com.github.nanaki_93.components.widgets.visibleAids
import com.github.nanaki_93.components.widgets.PrimaryButton
import com.github.nanaki_93.components.widgets.SecondaryButton
import com.github.nanaki_93.content.BrowserContentTextSource
import com.github.nanaki_93.content.BundledContentLoader
import com.github.nanaki_93.lesson.LessonCheckpointMismatch
import com.github.nanaki_93.lesson.LessonCheckpointResolution
import com.github.nanaki_93.lesson.LessonCommand
import com.github.nanaki_93.lesson.LessonCommit
import com.github.nanaki_93.lesson.EmptyLessonStage
import com.github.nanaki_93.lesson.LessonEmptyReason
import com.github.nanaki_93.lesson.LessonPlanItem
import com.github.nanaki_93.lesson.LessonOutcome
import com.github.nanaki_93.lesson.LocalLessonCoordinator
import com.github.nanaki_93.lesson.LocalLessonState
import com.github.nanaki_93.progress.LessonStage
import com.github.nanaki_93.progress.SavePreferences
import com.github.nanaki_93.content.Lesson
import com.github.nanaki_93.content.ChoiceExercise
import com.github.nanaki_93.content.CompletionExercise
import com.github.nanaki_93.content.ProductionExercise
import com.github.nanaki_93.content.ReadingExercise
import com.github.nanaki_93.content.AnswerRepresentation
import com.github.nanaki_93.practice.Assessment
import com.github.nanaki_93.practice.InvalidReason
import com.github.nanaki_93.practice.PracticeAnswer
import com.github.nanaki_93.storage.LocalProgressState
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.silk.components.navigation.Link
import com.varabyte.kobweb.silk.style.toModifier
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.dom.Fieldset
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Legend
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Main
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Section
import org.jetbrains.compose.web.dom.Text

/** Only a single, bounded catalog key is accepted. Query text is never a content path or save ID. */
private fun requestedLessonId(): String? {
    val params = js("new URLSearchParams(window.location.search)")
    val ids = params.getAll("lessonId") as Array<String>
    if (ids.size != 1) return null
    return ids.single().takeIf { it.length in 1..128 && LESSON_ID.matches(it) }
}

private val LESSON_ID = Regex("[A-Za-z0-9][A-Za-z0-9_-]*")

@Page("/lesson")
@Composable
fun LessonPage() {
    val lessonId = requestedLessonId()
    val darkMode = ColorMode.current == ColorMode.DARK
    Main {
        Box(Styles.GameContainer.toModifier().then(
            if (darkMode) Modifier.backgroundColor(Colors.DarkBackground) else Modifier
        )) {
            Column(Styles.Card.toModifier().then(
                if (darkMode) Modifier.backgroundColor(Colors.DarkCardBackground).color(Colors.DarkText) else Modifier
            )) {
                H1 { Text("Workplace lesson") }
                // Logical paths: Kobweb Link applies the configured /hiragame base exactly once.
                Link(path = "/topics") { Text("Back to Topics") }
                Link(path = "/") { Text("Back to Home") }
                if (lessonId == null) Section {
                    H2 { Text("Choose a lesson from Topics") }
                    P(attrs = { attr("role", "alert") }) {
                        Text("The lesson link is missing or invalid. No progress was changed.")
                    }
                } else LessonEntry(lessonId)
            }
        }
    }
}

@Composable
private fun LessonEntry(lessonId: String) {
    val scope = rememberCoroutineScope()
    val progress = LocalProgress.current
    val coordinator = remember(scope, progress, lessonId) {
        LocalLessonCoordinator(scope, progress, BundledContentLoader(BrowserContentTextSource()))
    }
    val state by coordinator.state.collectAsState()
    val saved by progress.state.collectAsState()
    DisposableEffect(coordinator, lessonId) {
        coordinator.load(lessonId)
        onDispose { coordinator.dispose() }
    }
    when (val current = state) {
        LocalLessonState.Loading -> Section(attrs = { attr("aria-live", "polite") }) {
            H2 { Text("Loading lesson") }
            P(attrs = { attr("role", "status") }) { Text("Reading reviewed lessons bundled with this site.") }
        }
        is LocalLessonState.Missing -> Section {
            H2 { Text("Lesson unavailable") }
            P(attrs = { attr("role", "status") }) {
                Text("That lesson is not in the current validated catalog. Any saved place is retained; choose another lesson in Topics.")
            }
        }
        is LocalLessonState.Empty -> Section {
            H2 { Text("No lessons available") }
            P { Text(when (current.reason) {
                LessonEmptyReason.EMPTY_CATALOG -> "The content catalog is empty. Your saved progress has not been removed."
                LessonEmptyReason.NO_LESSONS -> "No workplace lessons are listed. Your saved progress has not been removed."
            }) }
            PrimaryButton("Retry loading", onClick = coordinator::retryLoad)
        }
        is LocalLessonState.Error -> Section {
            H2 { Text("Lesson could not load") }
            P(attrs = { attr("role", "alert") }) { Text(current.safeMessage) }
            PrimaryButton("Retry loading", onClick = coordinator::retryLoad)
        }
        is LocalLessonState.Entry -> Section {
            H2 { Text(current.lesson.title) }
            P { Text("Goal: ${current.lesson.communicationGoal}") }
            LessonActionError(current.operationError, coordinator)
            if (current.operationError == null) {
                when (val checkpoint = current.checkpoint) {
                    LessonCheckpointResolution.NoRecord -> {
                        P { Text("Start at Situation. Your place is saved locally when possible; responses are not saved.") }
                        PrimaryButton("Start lesson", onClick = coordinator::start)
                    }
                    is LessonCheckpointResolution.Available -> {
                        P { Text("Saved place: ${stageLabel(checkpoint.record.stage)}${if (checkpoint.item != null) " · item saved" else " · stage entry"}. Previous responses and feedback are not restored.") }
                        PrimaryButton("Resume lesson", onClick = coordinator::resume)
                        P { Text("Revisit starts from Situation and replaces only this lesson's saved place. Earlier completion and reviews are retained.") }
                        SecondaryButton("Revisit from Situation", onClick = coordinator::start)
                    }
                    is LessonCheckpointResolution.Incompatible -> { // Projected as RecoveryRequired by the coordinator.
                        P { Text("Saved place needs recovery. Return to Topics and reopen this lesson.") }
                    }
                }
            }
        }
        is LocalLessonState.RecoveryRequired -> Section {
            H2 { Text(current.lesson.title) }
            P(attrs = { attr("role", "status") }) {
                Text("Your saved ${stageLabel(current.checkpoint.record.stage)} checkpoint cannot be resumed: ${mismatchMessage(current.checkpoint.reason)} The old place remains unchanged until you choose recovery.")
            }
            LessonActionError(current.operationError, coordinator)
            if (current.operationError == null) {
                PrimaryButton("Continue from Situation", onClick = coordinator::recoverToSituation)
            }
        }
        is LocalLessonState.Active -> LessonPlayer(current, saved, coordinator)
    }
}

/** One stage entry or authored item at a time; stage names are orientation, not navigation shortcuts. */
@Composable
private fun LessonPlayer(
    active: LocalLessonState.Active,
    saved: LocalProgressState,
    coordinator: LocalLessonCoordinator,
) {
    val preferences = saved.snapshot.preferences
    val lesson = active.lesson
    val session = active.session
    val stage = session.plan.stage(session.stage)
    val stageNumber = session.plan.stages.indexOf(stage) + 1
    Section(attrs = { style { property("overflow-wrap", "anywhere"); property("min-width", "0") } }) {
        H2 { Text(lesson.title) }
        P { Text("Stage $stageNumber of ${session.plan.stages.size}: ${stageLabel(session.stage)}") }
        P { Text(session.plan.stages.joinToString(" → ") { stageLabel(it.stage) }) }
        P { Text(if (session.itemIndex == null) "Stage introduction" else
            "Item ${session.itemIndex!! + 1} of ${stage.items.size}") }
        session.notice?.let { P(attrs = { attr("role", "status") }) { Text(it) } }
        when (session.stage) {
            LessonStage.SITUATION -> {
                H3 { Text("Situation") }
                P { Text(lesson.situation) }
                P { Text("Communication goal: ${lesson.communicationGoal}") }
                P { Text("Suggested difficulty: ${lesson.difficulty.name.lowercase()}; about ${lesson.durationMinutes} minutes. These are guidance, not requirements.") }
                P { Text("Suggested prerequisite lessons (optional): ${lesson.prerequisiteLessonIds.joinToString().ifEmpty { "none" }}") }
            }
            LessonStage.DIALOGUE -> when (val item = session.item) {
                null -> {
                    H3 { Text("Dialogue") }
                    P { Text(if (stage.empty == EmptyLessonStage.NO_DIALOGUE_TURNS)
                        "No dialogue turns are authored for this lesson. Continue when ready."
                        else "Read the exchange one turn at a time. Select Next to begin; audio is not needed.") }
                    val generalPhrases = lesson.phrases.filter { it.sourceTurnId == null }
                    if (generalPhrases.isNotEmpty()) {
                        H3 { Text("Useful phrases for this situation") }
                        for (phrase in generalPhrases) {
                            PreviewJapaneseText(phrase.text, preferences)
                            P { Text("Use: ${phrase.usage} · Register: ${phrase.register}") }
                        }
                    }
                }
                is LessonPlanItem.Turn -> DialogueTask(lesson, item, preferences)
                else -> Unit
            }
            LessonStage.UNDERSTANDING, LessonStage.GUIDED_PRACTICE -> when (val item = session.item) {
                null -> {
                    H3 { Text(stageLabel(session.stage)) }
                    P { Text(if (stage.empty == EmptyLessonStage.NO_EXERCISES)
                        "No exercises are authored for this stage. Continue when ready."
                        else "Work through each authored prompt in order. Select Next to begin.") }
                }
                is LessonPlanItem.Prompt -> key(session.id, session.stage, item.checkpointId, session.outcome != null) {
                    LessonPrompt(lesson, session, item, preferences, coordinator)
                }
                else -> Unit
            }
            LessonStage.ROLE_PLAY -> {
                H3 { Text("Role-play") }
                P { Text(lesson.rolePlay.task) }
                if (stage.empty == EmptyLessonStage.ROLE_PLAY_OBJECTIVE_ONLY) {
                    P { Text("No production prompt is authored here. Reflect on the authored objective instead.") }
                    key(session.id, session.stage, "objective", session.outcome != null) {
                        LessonPrompt(lesson, session, null, preferences, coordinator)
                    }
                } else if (session.item is LessonPlanItem.Prompt) {
                    val item = session.item as LessonPlanItem.Prompt
                    key(session.id, session.stage, item.checkpointId, session.outcome != null) {
                        LessonPrompt(lesson, session, item, preferences, coordinator)
                    }
                } else P { Text("Select Next to begin the role-play prompts.") }
            }
            LessonStage.SUMMARY -> LessonSummary(active, saved, coordinator)
        }
        LessonActionError(active.operationError, coordinator, canLeave = true)
        if (active.operationError == null) {
            if (stageNumber > 1 || session.itemIndex != null)
                SecondaryButton("Previous", onClick = {
                    coordinator.dispatch(LessonCommand.Previous(session.id, session.revision))
                })
            val prompt = session.item is LessonPlanItem.Prompt ||
                (session.stage == LessonStage.ROLE_PLAY && stage.empty == EmptyLessonStage.ROLE_PLAY_OBJECTIVE_ONLY)
            when {
                prompt && session.outcome != null -> {
                    PrimaryButton("Continue", onClick = {
                        coordinator.dispatch(LessonCommand.Continue(session.id, session.revision))
                    })
                    SecondaryButton("Retry this prompt", onClick = {
                        coordinator.dispatch(LessonCommand.Retry(session.id, session.revision))
                    })
                }
                prompt -> {
                    val objectiveOnly = session.stage == LessonStage.ROLE_PLAY &&
                        stage.empty == EmptyLessonStage.ROLE_PLAY_OBJECTIVE_ONLY
                    P { Text(if (objectiveOnly) "Skip the reflection to continue. This is recorded as skipped, not self-assessed."
                        else "To move past unresolved prompts, choose Skip remaining in this stage. Skipped prompts are not credited as correct.") }
                    SecondaryButton(if (objectiveOnly) "Skip reflection" else "Skip remaining in this stage", onClick = {
                        coordinator.dispatch(LessonCommand.SkipRemaining(session.id, session.revision))
                    })
                }
                !prompt && session.stage != LessonStage.SUMMARY -> PrimaryButton(
                    if (session.itemIndex == null && stage.items.isEmpty()) "Continue to next stage" else "Next",
                    onClick = { coordinator.dispatch(LessonCommand.Next(session.id, session.revision)) },
                )
            }
            SecondaryButton("Leave lesson", onClick = {
                coordinator.dispatch(LessonCommand.Leave(session.id, session.revision))
            })
        }
    }
}

/** All counts are bounded by the plan's one outcome per item, and never reconstructed from a save. */
@Composable
private fun LessonSummary(
    active: LocalLessonState.Active,
    saved: LocalProgressState,
    coordinator: LocalLessonCoordinator,
) {
    val session = active.session
    val outcomes = session.outcomes
    H3 { Text("Summary") }
    P { Text("Communication goal: ${active.lesson.communicationGoal}") }
    if (active.lesson.phrases.isNotEmpty()) {
        H3 { Text("Useful authored phrases") }
        active.lesson.phrases.forEach { phrase ->
            PreviewJapaneseText(phrase.text, saved.snapshot.preferences)
            P { Text("Use: ${phrase.usage} · Register: ${phrase.register}") }
        }
    }
    H3 { Text("Current-session outcomes only") }
    P { Text("Attempted: ${outcomes.count { it == LessonOutcome.CORRECT || it == LessonOutcome.INCORRECT }} · " +
        "Skipped: ${outcomes.count { it == LessonOutcome.SKIPPED }} · " +
        "Revealed: ${outcomes.count { it == LessonOutcome.REVEALED }} · " +
        "Self-assessed: ${outcomes.count { it == LessonOutcome.SELF_MET_CRITERIA || it == LessonOutcome.SELF_NEEDS_PRACTICE }}") }
    if (session.resumed) P { Text("You resumed a saved place. Earlier responses and feedback are unknown and are not included in these counts.") }
    P { Text("A skip or reveal is not a correct attempt. Self-assessment is your judgment, not an automatic grade.") }
    P(attrs = { attr("role", "status") }) {
        Text(if (active.finished) "Finished this lesson sequence. This is not mastery or a scheduled review."
            else "Not finished in this session. Arriving at Summary does not record completion; choose Finish lesson.")
    }
    P { Text("Local save status: ${saveStatusMessage(saved)}") }
    if (active.commit is LessonCommit.Rejected) {
        P(attrs = { attr("role", "alert") }) {
            Text(if (!active.finished) "The latest lesson update was rejected. This session remains usable, but that update was not saved and Finish was not accepted. Retry Finish when ready."
                else "The latest lesson update was rejected; earlier accepted Finish is unchanged. This update was not saved.")
        }
    }
    if (active.operationError == null) {
        if (!active.finished) PrimaryButton("Finish lesson", onClick = {
            coordinator.finish(session.id, session.revision)
        })
        Link(path = "/topics") { Text("Back to Topics") }
        P { Text("Revisit starts again at Situation. Earlier completion, other lessons and reviews are retained; current-session outcomes are cleared.") }
        SecondaryButton("Revisit lesson", onClick = {
            coordinator.dispatch(LessonCommand.Restart(session.id, session.revision, session.id))
        })
    }
}

/** Prompt-local state is discarded when the keyed cursor leaves composition or its session changes.
 * Revision is intentionally not a key: invalid submissions retain the draft at this prompt. */
@Composable
private fun LessonPrompt(
    lesson: Lesson,
    session: com.github.nanaki_93.lesson.LessonSession,
    item: LessonPlanItem.Prompt?,
    preferences: SavePreferences,
    coordinator: LocalLessonCoordinator,
) {
    val exercise = item?.exercise
    val production = exercise is ProductionExercise || item == null
    var draft by remember { mutableStateOf("") }
    var choice by remember { mutableStateOf<String?>(null) }
    var assessment by remember { mutableStateOf<Assessment?>(null) }
    var missingAssessment by remember { mutableStateOf(false) }
    var hintsVisible by remember { mutableStateOf(false) }
    var phrasesVisible by remember { mutableStateOf(false) }
    var examplesVisible by remember { mutableStateOf(false) }
    val guard = remember { JapaneseSubmissionGuard() }
    val id = session.id
    val revision = session.revision
    fun submit(nativeComposing: Boolean = false) {
        if (!production && exercise !is ChoiceExercise && !guard.canSubmit(nativeComposing)) return
        val answer = when (exercise) {
            is ChoiceExercise -> PracticeAnswer.Choice(choice ?: "")
            is ReadingExercise, is CompletionExercise -> PracticeAnswer.Text(draft)
            is ProductionExercise, null -> assessment?.let { PracticeAnswer.SelfAssessment(draft, it) }
        }
        if (answer == null) missingAssessment = true
        else coordinator.dispatch(LessonCommand.Submit(id, revision, answer))
    }

    if (exercise != null) P { Text(when (exercise) {
        is ChoiceExercise -> exercise.prompt
        is ReadingExercise -> exercise.prompt
        is CompletionExercise -> exercise.prompt
        is ProductionExercise -> exercise.prompt
    }) }
    if (production) {
        P { Text("Role-play objective: ${lesson.rolePlay.task}") }
        P { Text("Compare your response against these criteria; this is your judgment, not an automatic grade.") }
        for ((index, criterion) in (if (exercise is ProductionExercise) exercise.criteria else lesson.rolePlay.criteria).withIndex())
            P { Text("Criterion ${index + 1}: $criterion") }
    }
    val outcome = session.outcome
    if (outcome == null) {
        when (exercise) {
            is ChoiceExercise -> Fieldset {
                Legend { Text("Choose one answer") }
                for (option in exercise.options) Label(attrs = { classes("practice-choice") }) {
                    Input(type = InputType.Radio, attrs = {
                        attr("name", "lesson-choice-$id-${exercise.id}")
                        checked(choice == option.id)
                        onChange { choice = option.id }
                    })
                    option.text?.let { JapanesePassage(it, visibleAids(it, preferences, answerHidden = true).ruby, practiceTypography = false) }
                        ?: Text(option.label.orEmpty())
                }
            }
            is ReadingExercise -> {
                P { JapanesePassage(exercise.stimulus, visibleAids(exercise.stimulus, preferences, answerHidden = true).ruby, practiceTypography = false) }
                Label(attrs = { attr("for", "lesson-response") }) {
                    Text(if (exercise.answerRepresentation == AnswerRepresentation.KANA) "Reading in kana" else "Reading in romaji")
                }
                JapaneseAnswerInput(draft, { draft = it }, guard, ::submit, inputId = "lesson-response")
            }
            is CompletionExercise -> {
                val parts = exercise.template.split("{blank}")
                P { Span(attrs = { attr("lang", "ja"); style { property("overflow-wrap", "anywhere") } }) {
                    Text(parts[0]); Text("＿＿＿"); Text(parts[1])
                } }
                Label(attrs = { attr("for", "lesson-response") }) { Text("Fill the blank in Japanese") }
                JapaneseAnswerInput(draft, { draft = it }, guard, ::submit, inputId = "lesson-response")
            }
            is ProductionExercise, null -> {
                Label(attrs = { attr("for", "lesson-response") }) { Text("Your response in Japanese") }
                JapaneseResponseArea(draft, { draft = it }, inputId = "lesson-response")
                if (lesson.rolePlay.hints.isNotEmpty()) {
                    if (!hintsVisible) SecondaryButton("Show role-play hints", onClick = { hintsVisible = true })
                    else lesson.rolePlay.hints.forEach { P { Text("Hint: $it") } }
                }
                if (lesson.phrases.isNotEmpty()) {
                    if (!phrasesVisible) SecondaryButton("Show useful phrases", onClick = { phrasesVisible = true })
                    else lesson.phrases.forEach { phrase ->
                        JapaneseStudyText(phrase.text, preferences)
                        P { Text("Use: ${phrase.usage} · Register: ${phrase.register}") }
                    }
                }
                val examples = if (exercise is ProductionExercise) exercise.exampleResponses else lesson.rolePlay.examples
                if (examples.isNotEmpty()) {
                    if (!examplesVisible) SecondaryButton("Show possible responses", onClick = { examplesVisible = true })
                    else examples.forEach { example ->
                        P { Text("Possible response, not the only valid Japanese:") }
                        JapaneseStudyText(example, preferences)
                    }
                }
                Fieldset {
                    Legend { Text("Your self-assessment (required to submit)") }
                    for ((rating, label) in listOf(Assessment.MET_CRITERIA to "I met the criteria",
                        Assessment.NEEDS_PRACTICE to "I need more practice")) {
                        Label(attrs = { classes("practice-choice") }) {
                            Input(type = InputType.Radio, attrs = {
                                attr("name", "lesson-assessment-$id-${item?.checkpointId ?: "objective"}")
                                checked(assessment == rating)
                                onChange { assessment = rating; missingAssessment = false }
                            })
                            Text(label)
                        }
                    }
                }
            }
        }
        if (!production) P { Text("Answer-identifying readings, meanings, and fills are hidden until this prompt is resolved.") }
        session.validation?.let { reason -> P(attrs = { attr("role", "alert") }) {
            Text(when (reason) {
                InvalidReason.BLANK_INPUT -> "Enter a response or choose an answer before submitting."
                InvalidReason.UNKNOWN_CHOICE -> "Choose one of the available answers."
                InvalidReason.WRONG_ANSWER_TYPE -> "This answer type does not match this prompt."
            })
        } }
        if (missingAssessment) P(attrs = { attr("role", "alert") }) { Text("Choose a self-assessment before submitting.") }
        PrimaryButton(if (production) "Submit self-assessment" else "Check answer", onClick = { submit() })
        SecondaryButton("Skip this prompt", onClick = {
            coordinator.dispatch(LessonCommand.Skip(id, revision))
        })
        SecondaryButton(if (production) "Reveal examples and end attempt" else "Reveal answer and end attempt", onClick = {
            coordinator.dispatch(LessonCommand.Reveal(id, revision))
        })
    } else {
        P(attrs = { attr("role", "status") }) { Text(when (outcome) {
            LessonOutcome.CORRECT -> "Correct"
            LessonOutcome.INCORRECT -> "Not quite; review the authored explanation below."
            LessonOutcome.SKIPPED -> "Skipped · not a correct answer"
            LessonOutcome.REVEALED -> "Revealed · not a correct answer"
            LessonOutcome.SELF_MET_CRITERIA -> "Self-assessed: I met the criteria (not automatically graded)"
            LessonOutcome.SELF_NEEDS_PRACTICE -> "Self-assessed: I need more practice (not automatically graded)"
        }) }
        session.feedback?.let { AuthoredFeedbackContent(it, preferences) }
        P { Text("Situation: ${lesson.situation}") }
        if (lesson.phrases.isNotEmpty()) {
            H3 { Text("Useful phrases in context") }
            lesson.phrases.forEach { phrase ->
                PreviewJapaneseText(phrase.text, preferences)
                P { Text("Use: ${phrase.usage} · Register: ${phrase.register}") }
            }
        }
        if (lesson.grammarNotes.isNotEmpty()) {
            H3 { Text("Grammar support") }
            lesson.grammarNotes.forEach { note ->
                P { Text(note.explanation) }
                note.examples.forEach { PreviewJapaneseText(it, preferences) }
            }
        }
    }
}

@Composable
private fun DialogueTask(lesson: Lesson, item: LessonPlanItem.Turn, preferences: SavePreferences) {
    val speaker = lesson.dialogue.speakers.first { it.id == item.turn.speakerId }
    H3 { Text("${speaker.name} · ${speaker.role}") }
    PreviewJapaneseText(item.turn.text, preferences) // Normal reading size, never practice's oversized glyphs.
    val phrases = lesson.phrases.filter { it.sourceTurnId == item.turn.id }
    if (phrases.isNotEmpty()) {
        H3 { Text("Useful phrases in this turn") }
        for (phrase in phrases) {
            PreviewJapaneseText(phrase.text, preferences)
            P { Text("Use: ${phrase.usage} · Register: ${phrase.register}") }
        }
    }
    if (lesson.grammarNotes.isNotEmpty()) {
        H3 { Text("Grammar support") }
        for (note in lesson.grammarNotes) {
            P { Text(note.explanation) }
            note.examples.forEach { PreviewJapaneseText(it, preferences) }
        }
    }
}

@Composable
private fun LessonActionError(
    error: com.github.nanaki_93.lesson.LessonOperationError?,
    coordinator: LocalLessonCoordinator,
    canLeave: Boolean = false,
) {
    if (error == null) return
    P(attrs = { attr("role", "alert") }) { Text(error.safeMessage) }
    SecondaryButton("Retry action", onClick = { coordinator.retryOperation(error) })
    if (canLeave) SecondaryButton("Leave lesson", onClick = { coordinator.leaveAfterError(error) })
}

private fun mismatchMessage(reason: LessonCheckpointMismatch): String = when (reason) {
    LessonCheckpointMismatch.DIFFERENT_LESSON -> "it belongs to a different lesson."
    LessonCheckpointMismatch.MOVED_TO_ANOTHER_STAGE -> "the item moved to another stage."
    LessonCheckpointMismatch.UNSUPPORTED_GRAPH_NODE -> "this conversation-graph item is not part of this lesson player."
    LessonCheckpointMismatch.REMOVED_ITEM -> "the item is no longer in this stage."
}

private fun stageLabel(stage: LessonStage): String = when (stage) {
    LessonStage.SITUATION -> "Situation"
    LessonStage.DIALOGUE -> "Dialogue"
    LessonStage.UNDERSTANDING -> "Understanding"
    LessonStage.GUIDED_PRACTICE -> "Guided practice"
    LessonStage.ROLE_PLAY -> "Role-play"
    LessonStage.SUMMARY -> "Summary"
}
