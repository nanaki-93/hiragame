package com.github.nanaki_93.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.github.nanaki_93.components.styles.Styles
import com.github.nanaki_93.components.widgets.PrimaryButton
import com.github.nanaki_93.components.widgets.SecondaryButton
import com.github.nanaki_93.content.BrowserContentTextSource
import com.github.nanaki_93.content.BundledContentLoader
import com.github.nanaki_93.content.EmptyContentReason
import com.github.nanaki_93.practice.LocalPracticeCoordinator
import com.github.nanaki_93.practice.LocalPracticeState
import com.github.nanaki_93.practice.PracticeCommand
import com.github.nanaki_93.practice.SessionView
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.silk.style.toModifier
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Main
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

/** Home owns only this page's in-memory coordinator; no account or backend is required. */
@Page
@Composable
fun HomePage() {
    val scope = rememberCoroutineScope()
    val coordinator = remember { LocalPracticeCoordinator(scope, BundledContentLoader(BrowserContentTextSource())) }
    val state by coordinator.state.collectAsState()

    DisposableEffect(coordinator) {
        coordinator.load()
        onDispose { coordinator.dispose() }
    }

    Main {
        Box(Styles.GameContainer.toModifier()) {
            Column(
                modifier = Styles.Card.toModifier(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.cssRem),
            ) {
                H1 { Text("Hiragame") }
                when (val current = state) {
                    LocalPracticeState.Loading -> {
                        H2 { Text("Loading reviewed practice") }
                        P { Text("Reading this site's bundled content. No account is needed.") }
                    }
                    is LocalPracticeState.Empty -> {
                        H2 { Text("No practice available yet") }
                        P {
                            Text(when (current.reason) {
                                EmptyContentReason.EMPTY_CATALOG -> "The content catalog is empty. Reload to check again."
                                EmptyContentReason.NO_PRACTICE -> "No reviewed practice sets are listed yet. Reload to check again."
                                EmptyContentReason.EMPTY_PRACTICE_SETS -> "Reviewed practice sets have no exercises yet. Reload to check again."
                            })
                        }
                        PrimaryButton("Reload practice", onClick = coordinator::retryLoad)
                    }
                    is LocalPracticeState.Error -> {
                        H2 { Text("Practice could not load") }
                        P { Text(current.safeMessage) }
                        PrimaryButton("Retry loading", onClick = coordinator::retryLoad)
                    }
                    is LocalPracticeState.Ready -> {
                        val session = current.session
                        if (current.operationError != null) {
                            P { Text(current.operationError.safeMessage) }
                            SecondaryButton("Retry action", onClick = { coordinator.retryOperation(current.operationError) })
                            SecondaryButton("Back to practice sets", onClick = { coordinator.leave(current.operationError) })
                        } else if (session == null) {
                            H2 { Text("Practice locally") }
                            P { Text("A short session with reviewed exercises. Answers stay in this session only; reloading starts fresh.") }
                            // One clear invitation for the seed; extra sets get their own titled actions.
                            val sets = current.availablePracticeSets.values.toList()
                            for (set in sets) {
                                P { Text("${set.title} · ${set.exercises.size} exercises. ${set.description}") }
                                PrimaryButton(
                                    text = if (sets.size == 1) "Start practice" else "Start ${set.title}",
                                    onClick = { coordinator.start(set.id) },
                                )
                            }
                            if (sets.isEmpty()) P { Text("No exercises are available. Reload to check again.") }
                            SecondaryButton("Reload practice", onClick = coordinator::load)
                        } else {
                            // Prompt controls and feedback are added in the following practice-surface steps.
                            H2 { Text("Practice session") }
                            P { Text("This session is in memory only. ${session.counts.completed} of ${session.plan.size} exercises resolved.") }
                            when (val view = session.view) {
                                is SessionView.Prompt -> P { Text("Exercise ${view.index + 1} of ${session.plan.size} is ready.") }
                                else -> P { Text("Session in progress.") }
                            }
                            SecondaryButton("Back to practice sets", onClick = {
                                coordinator.dispatch(PracticeCommand.Leave(session.id, session.revision))
                            })
                        }
                    }
                }
            }
        }
    }
}
