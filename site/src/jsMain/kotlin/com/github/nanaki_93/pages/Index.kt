package com.github.nanaki_93.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.nanaki_93.content.AnswerRepresentation
import com.github.nanaki_93.content.ChoiceExercise
import com.github.nanaki_93.content.CompletionExercise
import com.github.nanaki_93.content.Exercise
import com.github.nanaki_93.content.JapaneseText
import com.github.nanaki_93.content.ProductionExercise
import com.github.nanaki_93.content.ReadingExercise
import com.github.nanaki_93.practice.Assessment
import com.github.nanaki_93.practice.AuthoredFeedback
import com.github.nanaki_93.practice.PracticeOutcome
import com.github.nanaki_93.practice.ReadingExpectedAnswer
import com.github.nanaki_93.practice.InvalidReason
import com.github.nanaki_93.practice.PracticeAnswer
import com.github.nanaki_93.practice.PracticeSession
import com.github.nanaki_93.components.styles.Styles
import com.github.nanaki_93.components.styles.Colors
import com.github.nanaki_93.components.widgets.PrimaryButton
import com.github.nanaki_93.components.widgets.SecondaryButton
import com.github.nanaki_93.content.BrowserContentTextSource
import com.github.nanaki_93.content.BundledContentLoader
import com.github.nanaki_93.content.EmptyContentReason
import com.github.nanaki_93.practice.LocalPracticeCoordinator
import com.github.nanaki_93.LocalProgress
import com.github.nanaki_93.initialSilkMode
import com.github.nanaki_93.progress.CheckpointView
import com.github.nanaki_93.progress.SavedColorMode
import com.github.nanaki_93.progress.SaveProblem
import com.github.nanaki_93.progress.BackupProblem
import com.github.nanaki_93.progress.changePreferences
import com.github.nanaki_93.storage.LocalProgressOwner
import com.github.nanaki_93.storage.LocalProgressState
import com.github.nanaki_93.storage.PersistenceStatus
import com.github.nanaki_93.storage.ProgressMutationResult
import com.github.nanaki_93.storage.StoreFailure
import com.github.nanaki_93.storage.BrowserDownloadSink
import com.github.nanaki_93.storage.ProgressDownloads
import com.github.nanaki_93.storage.DownloadResult
import com.github.nanaki_93.storage.BackupFlowCoordinator
import com.github.nanaki_93.storage.BackupFlowState
import com.github.nanaki_93.storage.BackupFlowError
import com.github.nanaki_93.storage.BackupReadError
import com.github.nanaki_93.storage.BackupConfirmationError
import com.github.nanaki_93.storage.BackupPreview
import com.github.nanaki_93.storage.ReplacementResult
import com.github.nanaki_93.storage.ProtectedReplacementToken
import com.github.nanaki_93.storage.ProtectedReplacementResult
import com.github.nanaki_93.practice.PracticeCheckpointResolution
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import com.github.nanaki_93.practice.LocalPracticeState
import com.github.nanaki_93.practice.PracticeCommand
import com.github.nanaki_93.practice.SessionView
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.silk.style.toModifier
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.dom.Fieldset
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Legend
import org.jetbrains.compose.web.dom.TagElement
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.TextArea
import org.w3c.dom.HTMLElement
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Main
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Section
import org.jetbrains.compose.web.dom.Text

/** Home owns only its practice coordinator; the save owner survives page navigation. */
@Page
@Composable
fun HomePage() {
    val scope = rememberCoroutineScope()
    val progress = LocalProgress.current
    val coordinator = remember(progress) { LocalPracticeCoordinator(scope, progress, BundledContentLoader(BrowserContentTextSource())) }
    val state by coordinator.state.collectAsState()
    val darkMode = ColorMode.current == ColorMode.DARK

    DisposableEffect(coordinator) {
        coordinator.load()
        onDispose { coordinator.dispose() }
    }

    Main {
        Box(Styles.GameContainer.toModifier().then(
            if (darkMode) Modifier.backgroundColor(Colors.DarkBackground) else Modifier
        )) {
            Column(
                modifier = Styles.Card.toModifier().then(
                    if (darkMode) Modifier.backgroundColor(Colors.DarkCardBackground).color(Colors.DarkText) else Modifier
                ),
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
                            P { Text("A short session with reviewed exercises. Progress checkpoints are saved locally when possible; typed responses are not retained.") }
                            val checkpoints = coordinator.savedCheckpoints()
                            // The saved record is never replaced just because a set or exercise is missing.
                            for ((setId, resolution) in checkpoints) {
                                if (resolution is PracticeCheckpointResolution.Unavailable) {
                                    val set = current.availablePracticeSets[setId]
                                    P(attrs = { attr("role", "status") }) {
                                        Text(if (resolution.missingSet) "Saved practice set $setId is unavailable in the current content. Its checkpoint is preserved; there is no run to start here."
                                        else "The saved checkpoint for ${set?.title ?: setId} cannot be resumed with the current exercises. It remains in your local progress until you choose a fresh start.")
                                    }
                                    if (set != null) SecondaryButton("Start fresh: ${set.title} (replace checkpoint)", onClick = {
                                        coordinator.startFreshAfterUnavailable(setId)
                                    })
                                }
                            }
                            // One clear invitation for the seed; extra sets get their own titled actions.
                            val sets = current.availablePracticeSets.values.toList()
                            for (set in sets) {
                                val resolution = checkpoints[set.id]
                                P { Text("${set.title} · ${set.exercises.size} exercises. ${set.description}") }
                                if (resolution is PracticeCheckpointResolution.Available) {
                                    if (resolution.checkpoint.view != CheckpointView.COMPLETE) {
                                        PrimaryButton("Resume ${set.title}", onClick = { coordinator.resume(set.id) })
                                        P { Text("Starting a new run replaces this set's unfinished checkpoint. Other progress is kept.") }
                                    } else P { Text("Previous run completed. You can start a new run.") }
                                }
                                if (resolution !is PracticeCheckpointResolution.Unavailable) {
                                    SecondaryButton(
                                        text = if (sets.size == 1) "Start practice" else "Start ${set.title}",
                                        onClick = { coordinator.start(set.id) },
                                    )
                                }
                            }
                            if (sets.isEmpty()) P { Text("No exercises are available. Reload to check again.") }
                            SecondaryButton("Reload practice", onClick = coordinator::load)
                        } else {
                            // Commands capture the rendered identity/revision. The coordinator applies them
                            // synchronously, so a second click from this render is stale and cannot advance.
                            val send: (PracticeCommand) -> Unit = coordinator::dispatch
                            SessionCounts(session)
                            when (val view = session.view) {
                                is SessionView.Prompt -> PracticePrompt(session, view, send) { answer ->
                                    send(PracticeCommand.Submit(session.id, session.revision, answer))
                                }
                                is SessionView.Feedback -> {
                                    PracticeFeedback(session, view.index, reviewing = false)
                                    SecondaryButton("Retry this exercise", onClick = { send(PracticeCommand.Retry(session.id, session.revision)) })
                                    if (view.index > 0) SecondaryButton("Previous resolved exercise", onClick = {
                                        send(PracticeCommand.Previous(session.id, session.revision))
                                    })
                                    PrimaryButton(if (view.index == session.plan.lastIndex) "Complete session" else "Continue to next exercise", onClick = {
                                        send(PracticeCommand.Continue(session.id, session.revision))
                                    })
                                }
                                is SessionView.Review -> {
                                    PracticeFeedback(session, view.index, reviewing = true)
                                    if (view.index > 0) SecondaryButton("Previous resolved exercise", onClick = {
                                        send(PracticeCommand.Previous(session.id, session.revision))
                                    })
                                    SecondaryButton("Next resolved exercise or return", onClick = {
                                        send(PracticeCommand.Next(session.id, session.revision))
                                    })
                                    SecondaryButton("Return to current place", onClick = {
                                        send(PracticeCommand.Return(session.id, session.revision))
                                    })
                                }
                                SessionView.Complete -> {
                                    H2 { Text("Session complete") }
                                    P { Text("This run's checkpoint is saved locally when possible. Typed responses are not retained; this summary does not measure mastery or proficiency.") }
                                    SecondaryButton("Review previous exercise", onClick = {
                                        send(PracticeCommand.Previous(session.id, session.revision))
                                    })
                                }
                                SessionView.Left -> Unit
                            }
                            SecondaryButton("Restart session", onClick = {
                                send(PracticeCommand.Restart(session.id, session.revision, session.id))
                            })
                            SecondaryButton("Back to practice sets", onClick = {
                                send(PracticeCommand.Leave(session.id, session.revision))
                            })
                        }
                    }
                }
                SavePreferencesSection(progress)
            }
        }
    }
}

/** Select a preference once; the owner returns only after the write attempt has completed.
 * Even a failed write keeps an accepted choice usable in this view, without a saved claim.
 */
internal fun selectColorMode(
    progress: LocalProgressOwner,
    mode: SavedColorMode,
    apply: () -> Unit,
): ProgressMutationResult {
    val result = progress.mutate { changePreferences(it, it.preferences.copy(colorMode = mode)) }
    if (result == ProgressMutationResult.Accepted) apply()
    return result
}

/** Recovery can change the saved preference without going through the radio control. */
internal fun replaceProtectedAndApplyMode(
    progress: LocalProgressOwner,
    token: ProtectedReplacementToken,
    apply: () -> Unit,
): ProtectedReplacementResult = progress.confirmProtectedReplacement(token).also {
    if (it == ProtectedReplacementResult.Replaced) apply()
}

internal fun reloadSavedAndApplyMode(progress: LocalProgressOwner, apply: () -> Unit): Boolean =
    progress.reloadSavedState().also { if (it) apply() }

/** Only a newly committed restore may change the visible theme. */
internal fun confirmRestoreAndApplyMode(flow: BackupFlowCoordinator, apply: () -> Unit): BackupFlowState {
    val wasConfirming = flow.state.value is BackupFlowState.Confirming
    if (wasConfirming) flow.confirm()
    return flow.state.value.also { if (wasConfirming && it is BackupFlowState.Success) apply() }
}

internal fun saveStatusMessage(state: LocalProgressState): String = when (val status = state.status) {
    PersistenceStatus.Fresh -> "No local snapshot yet. Progress checkpoints will be saved in this browser when possible."
    PersistenceStatus.Saved -> if (state.rejectedUpdate != null)
        "Earlier progress is saved in this browser. The latest change was not retained."
    else "Saved in this browser. Progress checkpoints and preferences are stored locally; typed responses are not retained."
    is PersistenceStatus.MemoryOnly -> when (status.reason) {
        StoreFailure.DENIED -> "Changes only in memory: browser storage is unavailable or denied. Unsaved work may be lost on reload."
        StoreFailure.QUOTA -> "Changes only in memory: storage is full. The earlier stored snapshot was not replaced; unsaved work may be lost on reload."
        StoreFailure.OTHER -> "Changes only in memory: saving failed. Unsaved work may be lost on reload."
    }
    is PersistenceStatus.Protected -> when (status.reason) {
        SaveProblem.UNSUPPORTED_VERSION -> "Saving paused: this local save uses an unsupported version. The original is preserved; changes in this view are only in memory."
        else -> "Saving paused: this local save cannot be read safely. The original is preserved; changes in this view are only in memory."
    }
    PersistenceStatus.Conflict -> "Saving paused: another tab changed the local snapshot. Changes in this view are only in memory and may be lost on reload."
}

/** Independent of the bundled catalog: render status and preferences even if practice fails to load. */
@Composable
private fun SavePreferencesSection(progress: LocalProgressOwner) {
    val saved by progress.state.collectAsState()
    val colorModeState = ColorMode.currentState
    val downloads = remember(progress) { ProgressDownloads(progress, BrowserDownloadSink()) }
    val restore = remember(progress) { BackupFlowCoordinator(progress) }
    val restoreState by restore.state.collectAsState()
    DisposableEffect(restore) { onDispose { restore.dispose() } }
    var actionMessage by remember(progress) { mutableStateOf<String?>(null) }
    var pendingReplacement by remember(progress) { mutableStateOf<ProtectedReplacementToken?>(null) }
    var confirmReload by remember(progress) { mutableStateOf(false) }
    // A reload or successful replacement invalidates confirmations captured by this view.
    val ownerGeneration = progress.generation
    val persistenceStatus = saved.status
    // State and baseline changes can expire a review without advancing generation.
    SideEffect { restore.ownerChanged() }
    DisposableEffect(progress, ownerGeneration, persistenceStatus) {
        onDispose {
            pendingReplacement?.let { progress.cancelProtectedReplacement(it) }
            pendingReplacement = null
            confirmReload = false
        }
    }
    Section(attrs = { classes("save-preferences") }) {
        H2 { Text("Save & preferences") }
        P(attrs = { attr("role", "status") }) { Text(saveStatusMessage(saved)) }
        if (saved.rejectedUpdate != null) {
            P(attrs = { attr("role", "alert") }) {
                Text("The latest change exceeded save limits or was invalid and was not retained. Earlier progress remains available.")
            }
        }
        if (saved.status is PersistenceStatus.MemoryOnly) {
            SecondaryButton("Retry saving", onClick = { progress.retrySaving() })
        }
        H2 { Text("Local progress actions") }
        P { Text("Export the current validated progress in this view. When saving is paused or unavailable, this export includes work not confirmed saved in this browser. Typed responses are never included.") }
        SecondaryButton("Export current progress", onClick = {
            actionMessage = downloadMessage(downloads.exportCurrent())
        })
        if (progress.originalProtectedRaw != null) {
            P { Text("The original unreadable save can be downloaded separately as unvalidated recovery text. It is not a validated or importable backup.") }
            SecondaryButton("Download unvalidated original", onClick = {
                actionMessage = downloadMessage(downloads.downloadProtectedOriginal())
            })
        } else if (persistenceStatus is PersistenceStatus.MemoryOnly && persistenceStatus.reason == StoreFailure.DENIED) {
            P { Text("Browser storage could not be read; the unavailable original cannot be downloaded. You can still export current memory.") }
        }
        RestoreSection(restore, restoreState, saved, downloads) { colorModeState.value = initialSilkMode(progress) }
        if (saved.status is PersistenceStatus.Protected) {
            P(attrs = { attr("role", "alert") }) {
                Text("Replacing the unreadable local save permanently discards its original stored text and any changes only in this view's memory. Download unvalidated recovery text and export current progress first if needed. This does not clear offline content.")
            }
            if (pendingReplacement == null) {
                SecondaryButton("Replace unreadable local save…", onClick = {
                    pendingReplacement = progress.beginProtectedReplacement()
                    if (pendingReplacement == null) actionMessage = "Replacement is no longer available. No save was changed."
                })
            } else {
                P { Text("Confirm replacement of the unreadable local save? This cannot be undone in this browser.") }
                SecondaryButton("Cancel replacement", onClick = {
                    pendingReplacement?.let { progress.cancelProtectedReplacement(it) }
                    pendingReplacement = null
                })
                PrimaryButton("Confirm replace unreadable save", onClick = {
                    val token = pendingReplacement
                    pendingReplacement = null
                    if (token != null) actionMessage = replacementMessage(replaceProtectedAndApplyMode(progress, token) {
                        colorModeState.value = initialSilkMode(progress)
                    })
                })
            }
        }
        if (saved.status == PersistenceStatus.Conflict) {
            P(attrs = { attr("role", "alert") }) {
                Text("Another tab changed or removed the saved snapshot. Saving is paused. Keep this view to continue only in memory, or export it before reloading. Reload discards unsaved changes and drafts; no automatic merge is available.")
            }
            SecondaryButton("Keep this view (saving paused)", onClick = {
                progress.keepThisView()
                actionMessage = "Keeping this view in memory. Saving remains paused; export before leaving."
            })
            if (!confirmReload) SecondaryButton("Reload saved state…", onClick = { confirmReload = true })
            else {
                P { Text("Discard changes and drafts in this view and reload the latest local save?") }
                SecondaryButton("Cancel reload", onClick = { confirmReload = false })
                PrimaryButton("Confirm discard and reload", onClick = {
                    confirmReload = false
                    actionMessage = if (reloadSavedAndApplyMode(progress) { colorModeState.value = initialSilkMode(progress) })
                        "Reloaded local state. Unsaved work in this view was discarded."
                    else "Could not read the saved state. This view and its unsaved work remain available."
                })
            }
        }
        actionMessage?.let { message -> P(attrs = { attr("role", "status") }) { Text(message) } }
        Fieldset {
            Legend { Text("Color mode") }
            Span(attrs = { classes("save-color-options") }) {
                for ((mode, label) in listOf(
                    SavedColorMode.SYSTEM to "System", SavedColorMode.LIGHT to "Light", SavedColorMode.DARK to "Dark",
                )) {
                    Label(attrs = { classes("save-mode-choice") }) {
                        Input(type = InputType.Radio, attrs = {
                            attr("name", "save-color-mode")
                            checked(saved.snapshot.preferences.colorMode == mode)
                            onChange {
                                selectColorMode(progress, mode) { colorModeState.value = initialSilkMode(progress) }
                            }
                        })
                        Text(label)
                    }
                }
            }
        }
    }
}

private fun utcDate(epochMs: Long): String = kotlin.js.Date(epochMs.toDouble()).toISOString().substring(0, 10)

internal fun restoreErrorMessage(error: BackupFlowError): String = when (error) {
    is BackupFlowError.Read -> when (error.reason) {
        BackupReadError.EMPTY -> "The file is empty. Choose a JSON save or backup."
        BackupReadError.TOO_LARGE -> "The file exceeds the backup size limit. Choose a smaller save."
        BackupReadError.INVALID_UTF8 -> "The file is not valid UTF-8 text. Choose a JSON save."
        BackupReadError.TIMED_OUT -> "Reading timed out. Choose the file again."
        BackupReadError.FAILED -> "The file could not be read. Choose it again."
    }
    is BackupFlowError.Validation -> when (error.reason) {
        BackupProblem.UNSUPPORTED_FORMAT, BackupProblem.UNSUPPORTED_SAVE_VERSION ->
            "This backup or save version is not supported. Nothing was replaced."
        BackupProblem.WRONG_APP -> "This backup belongs to another app. Nothing was replaced."
        BackupProblem.OVERSIZED, BackupProblem.TOO_DEEP -> "This save exceeds import limits. Nothing was replaced."
        else -> "This file is not a valid Hiragame save or backup. Nothing was replaced."
    }
}

internal fun restoreFailureMessage(error: BackupConfirmationError): String = when (error) {
    BackupConfirmationError.Expired -> "Local progress changed since review. Review the file and current save again before confirming."
    BackupConfirmationError.Blocked -> "Replacement is blocked until the local save can be read or a conflict is reconciled. Export current memory first."
    is BackupConfirmationError.Replacement -> when (error.result) {
        ReplacementResult.Stale -> "Confirmation expired. No save was replaced; review again."
        ReplacementResult.Conflict -> "Another tab changed the saved text. Replacement stopped; reconcile before reviewing again."
        is ReplacementResult.Failure -> "Replacement failed because browser storage is unavailable or full. Current progress remains active; review again."
        is ReplacementResult.InvalidReplacement -> "Replacement could not be prepared safely. Current progress remains active; review again."
        ReplacementResult.Replaced -> "Replacement completed."
    }
}

/** A separate confirmation is required after preview. Imported values remain plain text nodes. */
@Composable
private fun RestoreSection(
    restore: BackupFlowCoordinator,
    current: BackupFlowState,
    saved: LocalProgressState,
    downloads: ProgressDownloads,
    applyMode: () -> Unit,
) {
    var exportMessage by remember(restore) { mutableStateOf<String?>(null) }
    H2 { Text("Restore a backup") }
    Label(attrs = { attr("for", "restore-file") }) { Text("Choose a Hiragame JSON backup or save") }
    Input(type = InputType.File, attrs = {
        id("restore-file")
        attr("accept", ".json,application/json") // hint only; decoding validates the contents
        // Opening the picker supersedes an open confirmation even if the chooser is dismissed.
        onClick { restore.cancel() }
        onChange { event ->
            val input: dynamic = event.target.asDynamic()
            val file: dynamic = input.files?.item(0)
            input.value = "" // permit choosing the same file after cancellation or rejection
            if (file == null) restore.cancel() else restore.select(file, file.name as String)
        }
    })
    when (current) {
        BackupFlowState.Idle -> Unit
        is BackupFlowState.Reading -> {
            P(attrs = { attr("role", "status") }) { Text("Reading ${current.filename}…") }
            SecondaryButton("Cancel file reading", onClick = restore::cancel)
        }
        is BackupFlowState.Error -> {
            P(attrs = { attr("role", "alert") }) { Text(restoreErrorMessage(current.reason)) }
            SecondaryButton("Dismiss import error", onClick = restore::cancel)
        }
        is BackupFlowState.Preview, is BackupFlowState.Confirming, is BackupFlowState.Failure -> {
            val details = when (current) {
                is BackupFlowState.Preview -> current.details
                is BackupFlowState.Confirming -> current.details
                is BackupFlowState.Failure -> current.details
                else -> error("Handled above")
            }
            H2 { Text("Review replacement") }
            RestorePreview(details)
            P(attrs = { attr("role", "alert") }) {
                Text("Replace, not merge. This replaces all current learner progress and preferences, including work only in this view's memory and any active practice or draft. This cannot be undone here. Offline content is not cleared.")
            }
            P { Text("Export your current validated memory before replacing it. If the stored original is unreadable, download its separate unvalidated recovery text above as well.") }
            SecondaryButton("Export current progress before restore", onClick = {
                exportMessage = downloadMessage(downloads.exportCurrent())
            })
            exportMessage?.let { P(attrs = { attr("role", "status") }) { Text(it) } }
            when (current) {
                is BackupFlowState.Preview -> SecondaryButton("Continue to restore confirmation", onClick = restore::requestConfirmation)
                is BackupFlowState.Confirming -> {
                    P { Text("Confirm replacing the local save with this reviewed file? Unsaved changes and the active session will be lost only if the replacement succeeds.") }
                    PrimaryButton("Confirm restore and replace", onClick = { confirmRestoreAndApplyMode(restore, applyMode) })
                }
                is BackupFlowState.Failure -> {
                    P(attrs = { attr("role", "alert") }) { Text(restoreFailureMessage(current.reason)) }
                    SecondaryButton("Review again before retry", onClick = restore::renewReview)
                }
                else -> Unit
            }
            SecondaryButton("Cancel restore", onClick = restore::cancel)
        }
        is BackupFlowState.Success -> {
            P(attrs = { attr("role", "status") }) { Text("Backup restored in this browser. Previous progress was replaced; saved checkpoints can be resumed from the practice list.") }
            SecondaryButton("Dismiss restore result", onClick = restore::cancel)
        }
    }
}

@Composable
private fun RestorePreview(details: BackupPreview) {
    P { Text("File: ${details.filename}") }
    P { Text("Export date (UTC): ${details.exportedAtEpochMs?.let(::utcDate) ?: "Unavailable (bare save)"}") }
    P { Text("Snapshot saved date (UTC): ${utcDate(details.snapshotAtEpochMs)}") }
    P { Text("Source app version: ${details.appVersion ?: "Unavailable (bare save)"}; source save schema: ${details.sourceSchemaVersion}") }
    details.migratedFrom?.let { P { Text("Migrated from save schema $it in memory before review.") } }
    val prefs = details.preferences
    P { Text("Preferences: color mode ${prefs.colorMode.name.lowercase()}, readings ${prefs.showReadings}, translation ${prefs.showTranslation}, romaji ${prefs.showRomaji}.") }
    P { Text("Lessons: ${details.lessonCount} (${details.completedLessonCount} completed); practice checkpoints: ${details.practiceCheckpointCount}; review items: ${details.reviewItemCount}.") }
    P { Text("Unknown content IDs are retained. Availability against the current catalog was not checked; some saved records or checkpoints may not be usable until matching content returns. No due-review count was calculated.") }
}

internal fun downloadMessage(result: DownloadResult): String = when (result) {
    is DownloadResult.Downloaded -> "${result.label}: ${result.filename}"
    DownloadResult.OriginalUnavailable -> "The original saved text is unavailable. No recovery file was downloaded."
    is DownloadResult.InvalidSnapshot -> "Current progress could not be exported safely. No file was downloaded."
    DownloadResult.Failed -> "Download failed. Your local progress and saved text were not changed."
}

internal fun replacementMessage(result: ProtectedReplacementResult): String = when (result) {
    ProtectedReplacementResult.Replaced -> "Unreadable local save replaced with fresh progress in this browser."
    ProtectedReplacementResult.Stale -> "Replacement confirmation expired. No save was changed."
    ProtectedReplacementResult.Conflict -> "Another tab changed the saved text. Replacement stopped; saving is paused."
    is ProtectedReplacementResult.Failure -> "Replacement failed. The unreadable original remains stored."
    is ProtectedReplacementResult.InvalidReplacement -> "Replacement could not be prepared. The unreadable original remains stored."
}

/** Keep raw drafts local to the prompt. The evaluator, not the UI, judges blank and authored answers. */
internal fun promptAnswer(
    exercise: Exercise, choiceId: String?, draft: String, assessment: Assessment?, exampleRevealed: Boolean,
): PracticeAnswer? = when (exercise) {
    is ChoiceExercise -> PracticeAnswer.Choice(choiceId ?: "")
    is ReadingExercise, is CompletionExercise -> PracticeAnswer.Text(draft)
    // The session reducer accepts typed self-assessments; the prompt must require a prior example reveal.
    is ProductionExercise -> if (exampleRevealed) assessment?.let { PracticeAnswer.SelfAssessment(draft, it) } else null
}

@Composable
private fun PracticePrompt(
    session: PracticeSession, view: SessionView.Prompt, send: (PracticeCommand) -> Unit, submit: (PracticeAnswer) -> Unit,
) {
    val exercise = session.plan[view.index].exercise
    // This composable leaves composition on feedback; retry or another exercise gets fresh drafts.
    var draft by remember(session.id, exercise.id) { mutableStateOf("") }
    var selectedChoice by remember(session.id, exercise.id) { mutableStateOf<String?>(null) }
    var assessment by remember(session.id, exercise.id) { mutableStateOf<Assessment?>(null) }
    var missingAssessment by remember(session.id, exercise.id) { mutableStateOf(false) }
    var exampleRevealed by remember(session.id, exercise.id) { mutableStateOf(false) }

    H2 { Text("Exercise ${view.index + 1} of ${session.plan.size}") }
    P { Text("${session.counts.completed} resolved of ${session.plan.size}. Checkpoints are saved when possible; typed responses are not retained.") }
    P { Text(when (exercise) {
        is ChoiceExercise -> exercise.prompt
        is ReadingExercise -> exercise.prompt
        is CompletionExercise -> exercise.prompt
        is ProductionExercise -> exercise.prompt
    }) }
    when (exercise) {
        is ChoiceExercise -> Fieldset {
            Legend { Text("Choose one answer") }
            for (option in exercise.options) {
                Label(attrs = { classes("practice-choice") }) {
                    Input(type = InputType.Radio, attrs = {
                        attr("name", "choice-${session.id}-${exercise.id}")
                        checked(selectedChoice == option.id)
                        onChange { selectedChoice = option.id }
                    })
                    val japanese = option.text
                    if (japanese != null) JapanesePassage(japanese) else Text(option.label.orEmpty())
                }
            }
        }
        is ReadingExercise -> {
            P { JapanesePassage(exercise.stimulus) }
            Label(attrs = { attr("for", "practice-response") }) {
                Text(if (exercise.answerRepresentation == AnswerRepresentation.KANA) "Reading in kana" else "Reading in romaji")
            }
            Input(type = InputType.Text, attrs = {
                id("practice-response")
                classes("practice-answer")
                attr("maxlength", "200")
                value(draft)
                onInput { draft = it.value }
            })
        }
        is CompletionExercise -> {
            val parts = exercise.template.split("{blank}")
            P { Span(attrs = { attr("lang", "ja"); classes("practice-japanese"); style { property("overflow-wrap", "anywhere") } }) {
                Text(parts[0]); Text("＿＿＿"); Text(parts[1])
            } }
            Label(attrs = { attr("for", "practice-response") }) { Text("Fill the blank in Japanese") }
            Input(type = InputType.Text, attrs = {
                id("practice-response")
                classes("practice-answer")
                attr("maxlength", "200")
                value(draft)
                onInput { draft = it.value }
            })
        }
        is ProductionExercise -> {
            Label(attrs = { attr("for", "practice-response") }) { Text("Your response in Japanese") }
            TextArea(value = draft, attrs = {
                id("practice-response")
                classes("practice-answer")
                attr("maxlength", "1000")
                onInput { draft = it.value }
            })
            if (!exampleRevealed) {
                P { Text("Reveal the authored example before assessing your response. This is not automatically graded.") }
                PrimaryButton("Reveal example and criteria", onClick = { exampleRevealed = true })
            } else {
                // A prompt-local reveal leaves the response available for self-assessment. The reducer's
                // Reveal command resolves an item as revealed instead, so it cannot serve this step.
                for ((index, example) in exercise.exampleResponses.withIndex()) {
                    P { Text("Example response ${index + 1} (not your answer):") }
                    P { JapanesePassage(example) }
                    val translation = example.translation
                    if (translation != null) P { Text(translation) }
                }
                P { Text("Compare your response with the authored criteria. Your assessment is not an automatic grade.") }
                for ((index, criterion) in exercise.criteria.withIndex()) {
                    P { Text("Criterion ${index + 1}: $criterion") }
                }
                Fieldset {
                    Legend { Text("Your self-assessment") }
                    for ((rating, label) in listOf(Assessment.MET_CRITERIA to "I met the criteria", Assessment.NEEDS_PRACTICE to "I need more practice")) {
                        Label(attrs = { classes("practice-choice") }) {
                            Input(type = InputType.Radio, attrs = {
                                attr("name", "assessment-${session.id}-${exercise.id}")
                                checked(assessment == rating)
                                onChange { assessment = rating; missingAssessment = false }
                            })
                            Text(label)
                        }
                    }
                }
            }
        }
    }
    val validation = view.validation
    if (validation != null) P(attrs = { attr("role", "alert") }) {
        Text(when (validation) {
            InvalidReason.BLANK_INPUT -> "Enter a response or choose an answer before submitting."
            InvalidReason.UNKNOWN_CHOICE -> "Choose one of the available answers."
            InvalidReason.WRONG_ANSWER_TYPE -> "This answer type does not match the exercise."
        })
    }
    if (missingAssessment) P(attrs = { attr("role", "alert") }) { Text("Choose a self-assessment before submitting.") }
    if (exercise !is ProductionExercise || exampleRevealed) {
        PrimaryButton("Submit answer", onClick = {
            val answer = promptAnswer(exercise, selectedChoice, draft, assessment, exampleRevealed)
            if (answer == null) missingAssessment = true else submit(answer)
        })
    }
    SecondaryButton("Skip this exercise", onClick = { send(PracticeCommand.Skip(session.id, session.revision)) })
    SecondaryButton("Reveal answer and end attempt", onClick = { send(PracticeCommand.Reveal(session.id, session.revision)) })
    if (view.index > 0) SecondaryButton("Previous resolved exercise", onClick = {
        send(PracticeCommand.Previous(session.id, session.revision))
    })
}

@Composable
private fun SessionCounts(session: PracticeSession) {
    val counts = session.counts
    P { Text("Session outcomes · ${counts.completed} of ${session.plan.size} resolved: ${counts.correct} correct, ${counts.incorrect} incorrect, ${counts.skipped} skipped, ${counts.revealed} revealed, ${counts.selfAssessed} self-assessed.") }
}

/** The stored outcome is read-only in review; only the active feedback view offers Retry/Continue. */
@Composable
private fun PracticeFeedback(session: PracticeSession, index: Int, reviewing: Boolean) {
    val outcome = session.outcomes[index] ?: return
    H2 { Text(if (reviewing) "Review exercise ${index + 1} of ${session.plan.size}" else "Feedback · exercise ${index + 1} of ${session.plan.size}") }
    val exercise = session.plan[index].exercise
    P { Text(when (exercise) {
        is ChoiceExercise -> exercise.prompt
        is ReadingExercise -> exercise.prompt
        is CompletionExercise -> exercise.prompt
        is ProductionExercise -> exercise.prompt
    }) }
    if (exercise is CompletionExercise) P { Span(attrs = { attr("lang", "ja"); classes("practice-japanese") }) {
        Text(exercise.template.replace("{blank}", "＿＿＿"))
    } }
    P { Text(when (outcome) {
        is PracticeOutcome.Correct -> "Correct"
        is PracticeOutcome.Incorrect -> "Not quite"
        is PracticeOutcome.Skipped -> "Skipped · not correct"
        is PracticeOutcome.Revealed -> "Revealed · not correct"
        is PracticeOutcome.SelfAssessed -> when (outcome.assessment) {
            Assessment.MET_CRITERIA -> "Self-assessed: met criteria (not automatically graded)"
            Assessment.NEEDS_PRACTICE -> "Self-assessed: needs practice (not automatically graded)"
        }
    }) }
    when (val feedback = outcome.feedback) {
        is AuthoredFeedback.Choice -> {
            P { Text("Correct option:") }
            val option = feedback.correctOption
            val japanese = option.text
            if (japanese != null) JapaneseFeedbackText(japanese) else P { Text(option.label.orEmpty()) }
            P { Text("Explanation: ${feedback.explanation}") }
        }
        is AuthoredFeedback.Reading -> {
            P { Text("Reading stimulus:") }
            JapaneseFeedbackText(feedback.stimulus)
            for ((number, answer) in feedback.acceptedAnswers.withIndex()) {
                P { Text("Accepted reading ${number + 1} (${feedback.representation.name.lowercase()}):") }
                when (answer) {
                    is ReadingExpectedAnswer.Kana -> JapaneseFeedbackText(answer.text)
                    is ReadingExpectedAnswer.Romaji -> P { Text(answer.text) }
                }
            }
            P { Text("Explanation: ${feedback.explanation}") }
        }
        is AuthoredFeedback.Completion -> {
            for ((number, fill) in feedback.acceptedFills.withIndex()) {
                P { Text("Accepted fill ${number + 1}:") }
                JapaneseFeedbackText(fill)
            }
            P { Text("Completed example:") }
            JapaneseFeedbackText(feedback.completedExample)
            P { Text("Explanation: ${feedback.explanation}") }
        }
        is AuthoredFeedback.Production -> {
            P { Text("Authored examples, not a unique correct answer:") }
            for ((number, example) in feedback.examples.withIndex()) {
                P { Text("${example.label} ${number + 1}:") }
                JapaneseFeedbackText(example.text)
            }
            for ((number, criterion) in feedback.criteria.withIndex()) {
                P { Text("${criterion.label} ${number + 1}: ${criterion.text}") }
            }
        }
    }
}

@Composable
private fun JapaneseFeedbackText(text: JapaneseText) {
    P { JapanesePassage(text) }
    // A label-only choice never passes through here; do not infer a reading or meaning.
    P { Text("Reading: ${text.reading}") }
    text.translation?.let { P { Text("Meaning: $it") } }
    text.gloss?.let { P { Text("Gloss: $it") } }
}

/** Text nodes only: authored segments with readings become real ruby, never injected markup. */
@Composable
private fun JapanesePassage(text: JapaneseText) {
    Span(attrs = { attr("lang", "ja"); classes("practice-japanese"); style { property("overflow-wrap", "anywhere") } }) {
        if (text.segments.isEmpty()) Text(text.surface)
        else for (segment in text.segments) {
            val reading = segment.reading
            if (reading == null) Text(segment.surface)
            else TagElement<HTMLElement>("ruby", applyAttrs = null) {
                Text(segment.surface)
                TagElement<HTMLElement>("rt", applyAttrs = null) { Text(reading) }
            }
        }
    }
}
