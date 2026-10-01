package com.github.nanaki_93.practice

import com.github.nanaki_93.content.BundledContent
import com.github.nanaki_93.content.BundledContentException
import com.github.nanaki_93.content.BundledContentLoader
import com.github.nanaki_93.content.CatalogLoad
import com.github.nanaki_93.content.EmptyContentReason
import com.github.nanaki_93.content.ContentHttpException
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

/** Owns one load attempt and one session. No browser globals, backend, or persistence. */
class LocalPracticeCoordinator(
    private val scope: CoroutineScope,
    private val loadContent: suspend () -> CatalogLoad,
    private val reducer: (PracticeSession, PracticeCommand) -> PracticeSession = ::reduce,
    private val sessionFactory: (Long, com.github.nanaki_93.content.PracticeSet, Int) -> PracticeSession = ::startSession,
) {
    constructor(scope: CoroutineScope, loader: BundledContentLoader) : this(scope, loader::load)

    private val mutableState = MutableStateFlow<LocalPracticeState>(LocalPracticeState.Loading)
    val state: StateFlow<LocalPracticeState> = mutableState
    private var generation = 0L
    private var loadJob: Job? = null
    private var nextSessionId = 1L
    private var nextErrorToken = 1L
    private var pendingOperation: PendingOperation? = null
    private var disposed = false

    private sealed interface PendingOperation {
        data class Start(val setId: String, val limit: Int) : PendingOperation
        data class Command(val command: PracticeCommand) : PendingOperation
    }

    /** Reload and retry always supersede the previous generation, even if a source ignores cancellation. */
    fun load() {
        if (disposed) return
        generation++
        loadJob?.cancel()
        pendingOperation = null
        mutableState.value = LocalPracticeState.Loading
        val token = generation
        loadJob = scope.launch {
            try {
                val result = loadContent()
                if (token != generation || disposed) return@launch
                mutableState.value = when (result) {
                    is CatalogLoad.Ready -> LocalPracticeState.Ready(result.content)
                    is CatalogLoad.Empty -> LocalPracticeState.Empty(result.reason)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
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

    /** The selected set must be in the latest validated snapshot, not a caller-provided document. */
    fun start(setId: String, limit: Int = 10) = perform(PendingOperation.Start(setId, limit))

    /** Synchronous update: callbacks captured with an older revision cannot act twice. */
    fun dispatch(command: PracticeCommand) = perform(PendingOperation.Command(command))

    /** Capture the error shown by the UI; an old retry cannot act on a subsequent failure. */
    fun retryOperation(error: PracticeOperationError) {
        val ready = state.value as? LocalPracticeState.Ready ?: return
        if (ready.operationError != error) return
        pendingOperation?.let { perform(it, retrying = true) }
    }

    /** Leave after an operation failure without re-entering the failing reducer. Normal exit uses guarded Leave. */
    fun leave(error: PracticeOperationError) {
        val ready = state.value as? LocalPracticeState.Ready ?: return
        if (ready.operationError != error) return
        pendingOperation = null
        mutableState.value = ready.copy(session = null, operationError = null)
    }

    /** Page disposal invalidates even transports that complete after cancellation. */
    fun dispose() {
        if (disposed) return
        disposed = true
        generation++
        loadJob?.cancel()
        loadJob = null
        pendingOperation = null
    }

    private fun perform(operation: PendingOperation, retrying: Boolean = false) {
        if (disposed) return
        val ready = state.value as? LocalPracticeState.Ready ?: return
        // An operation error must be explicitly retried or left, not silently overwritten.
        if (ready.operationError != null && (!retrying || pendingOperation != operation)) return
        try {
            val updated = when (operation) {
                is PendingOperation.Start -> {
                    if (ready.session != null) return
                    val set = ready.availablePracticeSets[operation.setId] ?: return
                    check(nextSessionId < Long.MAX_VALUE) { "Session identity exhausted" }
                    sessionFactory(nextSessionId, set, operation.limit).also { nextSessionId++ }
                }
                is PendingOperation.Command -> {
                    val current = ready.session ?: return
                    val command = operation.command
                    if (command.sessionId != current.id || command.revision != current.revision) return
                    val guarded = if (command is PracticeCommand.Restart) {
                        check(nextSessionId < Long.MAX_VALUE) { "Session identity exhausted" }
                        command.copy(newSessionId = nextSessionId)
                    } else command
                    reducer(current, guarded).also {
                        if (it !== current && guarded is PracticeCommand.Restart) nextSessionId++
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
            val kind = if (operation is PendingOperation.Start) PracticeOperation.START else PracticeOperation.DISPATCH
            mutableState.value = ready.copy(operationError = PracticeOperationError(
                kind, if (kind == PracticeOperation.START) "Unable to start this session. Retry or leave."
                else "Unable to complete this action. Retry or leave.",
                nextErrorToken++,
            ))
        }
    }
}
