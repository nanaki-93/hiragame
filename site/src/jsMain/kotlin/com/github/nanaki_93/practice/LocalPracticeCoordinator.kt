package com.github.nanaki_93.practice

import com.github.nanaki_93.content.BundledContent
import com.github.nanaki_93.content.BundledContentException
import com.github.nanaki_93.content.BundledContentLoader
import com.github.nanaki_93.content.CatalogLoad
import com.github.nanaki_93.content.EmptyContentReason
import com.github.nanaki_93.content.ContentHttpException
import com.github.nanaki_93.progress.CheckpointView
import com.github.nanaki_93.progress.PracticeCheckpoint
import com.github.nanaki_93.progress.PracticeRestoreResult
import com.github.nanaki_93.progress.ProgressUpdate
import com.github.nanaki_93.progress.restorePractice
import com.github.nanaki_93.progress.projectPracticeCheckpoint
import com.github.nanaki_93.storage.LocalProgressOwner
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A single observable runtime state. Operation failures retain the last valid ready state. */
sealed interface LocalPracticeState {
    data object Loading : LocalPracticeState
    data class Ready(
        val content: BundledContent,
        val session: PracticeSession? = null,
        val operationError: PracticeOperationError? = null,
    ) : LocalPracticeState {
        val availablePracticeSets get() = content.practiceSets.filterValues { it.exercises.isNotEmpty() }
    }
    data class Empty(val reason: EmptyContentReason) : LocalPracticeState
    data class Error(
        val kind: LoadErrorKind,
        val safeMessage: String,
        val affectedPath: String? = null,
    ) : LocalPracticeState
}

/** Categories are based on exception types, never on untrusted response or diagnostic text. */
enum class LoadErrorKind { CONTENT, HTTP, UNEXPECTED }

enum class PracticeOperation { START, DISPATCH }

/** Messages are deliberately fixed: never display untrusted exception or response text. */
data class PracticeOperationError(val operation: PracticeOperation, val safeMessage: String, val token: Long)

/** Runtime IDs are never reused by another coordinator in the same application instance. */
private var nextRuntimePracticeId = 1L
private fun runtimePracticeId(): Long {
    check(nextRuntimePracticeId < Long.MAX_VALUE) { "Session identity exhausted" }
    return nextRuntimePracticeId++
}

/** Resolution is a read-only projection: missing or changed content never edits the save. */
sealed interface PracticeCheckpointResolution {
    val checkpoint: PracticeCheckpoint
    data class Available(override val checkpoint: PracticeCheckpoint) : PracticeCheckpointResolution {
        val completed: Boolean get() = checkpoint.view == CheckpointView.COMPLETE
    }
    data class Unavailable(override val checkpoint: PracticeCheckpoint, val missingSet: Boolean) : PracticeCheckpointResolution
}

/** Owns one load attempt and one session; only committed reducer frontiers reach the save owner. */
class LocalPracticeCoordinator(
    private val scope: CoroutineScope,
    private val progress: LocalProgressOwner,
    private val loadContent: suspend () -> CatalogLoad,
    private val reducer: (PracticeSession, PracticeCommand) -> PracticeSession = ::reduce,
    private val sessionFactory: (Long, com.github.nanaki_93.content.PracticeSet, Int) -> PracticeSession = ::startSession,
    private val now: () -> Long = { kotlin.js.Date.now().toLong() },
    private val tokenPrefix: String = "practice_${Random.nextInt().toUInt().toString(16)}_${Random.nextInt().toUInt().toString(16)}",
) {
    constructor(scope: CoroutineScope, progress: LocalProgressOwner, loader: BundledContentLoader) : this(scope, progress, loader::load)

    private val mutableState = MutableStateFlow<LocalPracticeState>(LocalPracticeState.Loading)
    val state: StateFlow<LocalPracticeState> get() {
        reconcileOwner()
        return mutableState
    }
    private var generation = 0L
    private var loadJob: Job? = null
    private var ownerGeneration = progress.generation
    private var nextErrorToken = 1L
    private var nextTransition = 0L
    private var runToken: String? = null
    private var pendingOperation: PendingOperation? = null
    private var disposed = false
    // Owner reloads must invalidate the published flow even if nobody calls a coordinator method.
    private val stopObservingOwner = progress.observeGeneration { reconcileOwner() }

    private sealed interface PendingOperation {
        data class Start(val setId: String, val limit: Int, val recovering: Boolean = false) : PendingOperation
        data class Resume(val setId: String) : PendingOperation
        data class Command(val command: PracticeCommand) : PendingOperation
    }

    /** Reload and retry always supersede the previous generation, even if a source ignores cancellation. */
    fun load() {
        if (disposed) return
        reconcileOwner()
        generation++
        loadJob?.cancel()
        pendingOperation = null
        mutableState.value = LocalPracticeState.Loading
        val token = generation
        loadJob = scope.launch {
            try {
                val result = loadContent()
                reconcileOwner()
                if (token != generation || disposed) return@launch
                mutableState.value = when (result) {
                    is CatalogLoad.Ready -> LocalPracticeState.Ready(result.content)
                    is CatalogLoad.Empty -> LocalPracticeState.Empty(result.reason)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reconcileOwner()
                if (token != generation || disposed) return@launch
                val bundled = e as? BundledContentException
                val kind = when {
                    e is ContentHttpException || bundled?.cause is ContentHttpException -> LoadErrorKind.HTTP
                    bundled != null -> LoadErrorKind.CONTENT
                    else -> LoadErrorKind.UNEXPECTED
                }
                mutableState.value = LocalPracticeState.Error(
                    kind,
                    when (kind) {
                        LoadErrorKind.HTTP -> "Unable to read bundled content from this site. Retry the load."
                        LoadErrorKind.CONTENT -> "Unable to load reviewed content. Retry the load."
                        LoadErrorKind.UNEXPECTED -> "Unable to load local content. Retry the load."
                    },
                    bundled?.affectedPath,
                )
            }
        }
    }

    fun retryLoad() {
        if (state.value is LocalPracticeState.Error || state.value is LocalPracticeState.Empty) load()
    }

    /** Read against the latest catalog and save; never remove records when content is missing. */
    fun savedCheckpoints(): Map<String, PracticeCheckpointResolution> {
        reconcileOwner()
        val ready = state.value as? LocalPracticeState.Ready ?: return emptyMap()
        return progress.state.value.snapshot.practiceProgress.associate { checkpoint ->
            val set = ready.availablePracticeSets[checkpoint.setId]
            val resolution = if (set == null) PracticeCheckpointResolution.Unavailable(checkpoint, missingSet = true)
            else if (restorePractice(1, set, checkpoint) is PracticeRestoreResult.Restored)
                PracticeCheckpointResolution.Available(checkpoint)
            else PracticeCheckpointResolution.Unavailable(checkpoint, missingSet = false)
            checkpoint.setId to resolution
        }
    }

    /** Start a new run for a resolvable set, even if a prior run was completed. */
    fun start(setId: String, limit: Int = 10) = perform(PendingOperation.Start(setId, limit))

    /** Explicitly replace an incompatible checkpoint, only when its set is currently available. */
    fun startFreshAfterUnavailable(setId: String, limit: Int = 10) =
        perform(PendingOperation.Start(setId, limit, recovering = true))

    /** Resume does not save or advance; feedback waits for an explicit learner action. */
    fun resume(setId: String) = perform(PendingOperation.Resume(setId))

    /** Synchronous update: callbacks captured with an older revision cannot act twice. */
    fun dispatch(command: PracticeCommand) = perform(PendingOperation.Command(command))

    /** Capture the error shown by the UI; an old retry cannot act on a subsequent failure. */
    fun retryOperation(error: PracticeOperationError) {
        reconcileOwner()
        val ready = state.value as? LocalPracticeState.Ready ?: return
        if (ready.operationError != error) return
        pendingOperation?.let { perform(it, retrying = true) }
    }

    /** Leave after an operation failure without re-entering the failing reducer. Normal exit uses guarded Leave. */
    fun leave(error: PracticeOperationError) {
        reconcileOwner()
        val ready = state.value as? LocalPracticeState.Ready ?: return
        if (ready.operationError != error) return
        pendingOperation = null
        mutableState.value = ready.copy(session = null, operationError = null)
    }

    /** Page disposal invalidates even transports that complete after cancellation. */
    fun dispose() {
        if (disposed) return
        disposed = true
        stopObservingOwner()
        generation++
        loadJob?.cancel()
        loadJob = null
        pendingOperation = null
    }

    private fun reconcileOwner() {
        if (disposed || ownerGeneration == progress.generation) return
        ownerGeneration = progress.generation
        generation++
        pendingOperation = null
        runToken = null
        val current = mutableState.value
        if (current is LocalPracticeState.Ready) mutableState.value = current.copy(session = null, operationError = null)
        else if (current is LocalPracticeState.Loading) {
            loadJob?.cancel()
            if (progress.isCurrentGeneration(ownerGeneration)) {
                load() // a load started before reload must not publish against the new save
            }
        }
    }

    private fun perform(operation: PendingOperation, retrying: Boolean = false) {
        if (disposed) return
        reconcileOwner()
        val ready = state.value as? LocalPracticeState.Ready ?: return
        // An operation error must be explicitly retried or left, not silently overwritten.
        if (ready.operationError != null && (!retrying || pendingOperation != operation)) return
        try {
            val updated = when (operation) {
                is PendingOperation.Start -> {
                    if (ready.session != null) return
                    val set = ready.availablePracticeSets[operation.setId] ?: return
                    val resolution = savedCheckpoints()[operation.setId]
                    if (operation.recovering != (resolution is PracticeCheckpointResolution.Unavailable)) return
                    sessionFactory(runtimePracticeId(), set, operation.limit)
                }
                is PendingOperation.Resume -> {
                    if (ready.session != null) return
                    val resolution = savedCheckpoints()[operation.setId] as? PracticeCheckpointResolution.Available ?: return
                    val set = ready.availablePracticeSets[operation.setId] ?: return
                    val restored = restorePractice(runtimePracticeId(), set, resolution.checkpoint)
                    if (restored !is PracticeRestoreResult.Restored) return
                    runToken = resolution.checkpoint.runToken
                    restored.session
                }
                is PendingOperation.Command -> {
                    val current = ready.session ?: return
                    val command = operation.command
                    if (command.sessionId != current.id || command.revision != current.revision) return
                    val guarded = if (command is PracticeCommand.Restart) {
                        command.copy(newSessionId = runtimePracticeId())
                    } else command
                    reducer(current, guarded)
                }
            }
            // Reducer validation and read-only navigation may change its runtime revision but
            // not the committed frontier. Never observe the StateFlow as a save trigger.
            val committed = when (operation) {
                is PendingOperation.Start -> true
                is PendingOperation.Resume -> false
                is PendingOperation.Command -> updated !== ready.session && when (operation.command) {
                    is PracticeCommand.Submit -> updated.view is SessionView.Feedback
                    is PracticeCommand.Skip, is PracticeCommand.Reveal, is PracticeCommand.Retry,
                    is PracticeCommand.Continue, is PracticeCommand.Restart -> updated.view !is SessionView.Review
                    else -> false
                }
            }
            if (committed) {
                val set = ready.availablePracticeSets[updated.setId]
                if (set != null) {
                    val newRun = operation is PendingOperation.Start ||
                        (operation is PendingOperation.Command && operation.command is PracticeCommand.Restart)
                    if (newRun || runToken == null) runToken = "${tokenPrefix}_run_${++nextTransition}"
                    val transitionToken = "${tokenPrefix}_transition_${++nextTransition}"
                    // A rejected bounded mutation leaves the previous valid snapshot in the owner;
                    // the accepted practice action still remains usable in this runtime session.
                    val timestamp = now()
                    progress.mutate { snapshot ->
                        val previous = snapshot.practiceProgress.firstOrNull { it.setId == set.id }
                        val checkpoint = projectPracticeCheckpoint(
                            updated, set, timestamp, runToken!!, transitionToken, previous,
                        )
                        if (checkpoint == previous) ProgressUpdate.Unchanged(snapshot)
                        else {
                            val records = snapshot.practiceProgress
                            val index = records.indexOfFirst { it.setId == set.id }
                            ProgressUpdate.Applied(snapshot.copy(practiceProgress =
                                if (index < 0) records + checkpoint
                                else records.toMutableList().also { it[index] = checkpoint }))
                        }
                    }
                }
            }
            pendingOperation = null
            mutableState.value = ready.copy(
                session = if (updated.view == SessionView.Left) null else updated,
                operationError = null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            pendingOperation = operation
            val kind = if (operation is PendingOperation.Start || operation is PendingOperation.Resume) PracticeOperation.START else PracticeOperation.DISPATCH
            mutableState.value = ready.copy(operationError = PracticeOperationError(
                kind, if (kind == PracticeOperation.START) "Unable to start this session. Retry or leave."
                else "Unable to complete this action. Retry or leave.",
                nextErrorToken++,
            ))
        }
    }
}
