package com.github.nanaki_93.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.github.nanaki_93.LocalProgress
import com.github.nanaki_93.components.styles.Colors
import com.github.nanaki_93.components.styles.Styles
import com.github.nanaki_93.components.widgets.PreviewJapaneseText
import com.github.nanaki_93.components.widgets.PrimaryButton
import com.github.nanaki_93.components.widgets.SecondaryButton
import com.github.nanaki_93.content.BrowserContentTextSource
import com.github.nanaki_93.content.BundledContentLoader
import com.github.nanaki_93.lesson.LessonCheckpointMismatch
import com.github.nanaki_93.lesson.LessonCheckpointResolution
import com.github.nanaki_93.lesson.LessonCommand
import com.github.nanaki_93.lesson.EmptyLessonStage
import com.github.nanaki_93.lesson.LessonEmptyReason
import com.github.nanaki_93.lesson.LessonPlanItem
import com.github.nanaki_93.lesson.LocalLessonCoordinator
import com.github.nanaki_93.lesson.LocalLessonState
import com.github.nanaki_93.progress.LessonStage
import com.github.nanaki_93.progress.SavePreferences
import com.github.nanaki_93.content.Lesson
import com.github.nanaki_93.content.ChoiceExercise
import com.github.nanaki_93.content.CompletionExercise
import com.github.nanaki_93.content.ProductionExercise
import com.github.nanaki_93.content.ReadingExercise
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.silk.components.navigation.Link
import com.varabyte.kobweb.silk.style.toModifier
import com.varabyte.kobweb.silk.theme.colors.ColorMode
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
        is LocalLessonState.Active -> LessonPlayer(current, saved.snapshot.preferences, coordinator)
    }
}

/** One stage entry or authored item at a time; stage names are orientation, not navigation shortcuts. */
@Composable
private fun LessonPlayer(
    active: LocalLessonState.Active,
    preferences: SavePreferences,
    coordinator: LocalLessonCoordinator,
) {
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
                is LessonPlanItem.Prompt -> {
                    H3 { Text("Current exercise") }
                    P { Text(when (val exercise = item.exercise) {
                        is ChoiceExercise -> exercise.prompt
                        is ReadingExercise -> exercise.prompt
                        is CompletionExercise -> exercise.prompt
                        is ProductionExercise -> exercise.prompt
                    }) }
                    P { Text("Response controls for this exercise follow in the next player step. You can go back or explicitly skip the remaining prompts in this stage.") }
                }
                else -> Unit
            }
            LessonStage.ROLE_PLAY -> {
                H3 { Text("Role-play") }
                P { Text(lesson.rolePlay.task) }
                if (stage.empty == EmptyLessonStage.ROLE_PLAY_OBJECTIVE_ONLY)
                    P { Text("No production prompt is authored here. Reflect on the objective before continuing; reflection controls follow in the next player step.") }
                else if (session.item is LessonPlanItem.Prompt) {
                    val exercise = (session.item as LessonPlanItem.Prompt).exercise as ProductionExercise
                    P { Text(exercise.prompt) }
                    P { Text("Role-play response controls follow in the next player step.") }
                } else P { Text("Select Next to begin the role-play prompts.") }
            }
            LessonStage.SUMMARY -> {
                H3 { Text("Summary") }
                P { Text("Communication goal: ${lesson.communicationGoal}") }
                P { Text("Arriving here does not finish the lesson. Finish and session summary controls follow in the next player step.") }
            }
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
                prompt && session.outcome != null -> PrimaryButton("Continue", onClick = {
                    coordinator.dispatch(LessonCommand.Continue(session.id, session.revision))
                })
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
