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
import com.github.nanaki_93.components.widgets.PrimaryButton
import com.github.nanaki_93.components.widgets.SecondaryButton
import com.github.nanaki_93.content.BrowserContentTextSource
import com.github.nanaki_93.content.BundledContentLoader
import com.github.nanaki_93.lesson.LessonCheckpointMismatch
import com.github.nanaki_93.lesson.LessonCheckpointResolution
import com.github.nanaki_93.lesson.LessonCommand
import com.github.nanaki_93.lesson.LessonEmptyReason
import com.github.nanaki_93.lesson.LocalLessonCoordinator
import com.github.nanaki_93.lesson.LocalLessonState
import com.github.nanaki_93.progress.LessonStage
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
        is LocalLessonState.Active -> Section {
            H2 { Text(current.lesson.title) }
            P { Text("Current stage: ${stageLabel(current.session.stage)}") }
            current.session.notice?.let { P(attrs = { attr("role", "status") }) { Text(it) } }
            LessonActionError(current.operationError, coordinator, canLeave = true)
            if (current.operationError == null) {
                // The stage controls are added in the following player tasks. Leave is already guarded.
                SecondaryButton("Leave lesson", onClick = {
                    coordinator.dispatch(LessonCommand.Leave(current.session.id, current.session.revision))
                })
            }
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
    if (canLeave) SecondaryButton("Leave this action", onClick = { coordinator.leaveAfterError(error) })
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
