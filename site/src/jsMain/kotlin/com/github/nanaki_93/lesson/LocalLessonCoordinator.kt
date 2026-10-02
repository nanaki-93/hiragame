package com.github.nanaki_93.lesson

import com.github.nanaki_93.content.BundledContent
import com.github.nanaki_93.content.BundledContentException
import com.github.nanaki_93.content.BundledContentLoader
import com.github.nanaki_93.content.CatalogLoad
import com.github.nanaki_93.content.ContentHttpException
import com.github.nanaki_93.content.DocumentKind
import com.github.nanaki_93.content.EmptyContentReason
import com.github.nanaki_93.content.Lesson
import com.github.nanaki_93.progress.LessonStage
import com.github.nanaki_93.progress.SaveProblem
import com.github.nanaki_93.progress.completeLesson
import com.github.nanaki_93.progress.visitLesson
import com.github.nanaki_93.storage.LocalProgressOwner
import com.github.nanaki_93.storage.PersistenceStatus
import com.github.nanaki_93.storage.ProgressMutationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

/** Entry and recovery are projections of the current owner snapshot, not save operations. */
sealed interface LocalLessonState {
    data object Loading : LocalLessonState
    data class Entry(val lesson: Lesson, val checkpoint: LessonCheckpointResolution,
                     val operationError: LessonOperationError? = null) : LocalLessonState
    data class RecoveryRequired(val lesson: Lesson, val checkpoint: LessonCheckpointResolution.Incompatible,
                                val operationError: LessonOperationError? = null) : LocalLessonState
    data class Active(val lesson: Lesson, val session: LessonSession,
                      val operationError: LessonOperationError? = null,
                      val commit: LessonCommit? = null,
                      /** True only after Finish was accepted in this run, not merely on Summary entry. */
                      val finished: Boolean = false) : LocalLessonState
    data class Missing(val requestedId: String) : LocalLessonState
    data class Empty(val reason: LessonEmptyReason) : LocalLessonState
    data class Error(val kind: LessonLoadErrorKind, val safeMessage: String) : LocalLessonState
}

enum class LessonEmptyReason { EMPTY_CATALOG, NO_LESSONS }
enum class LessonLoadErrorKind { CONTENT, HTTP, UNEXPECTED, CANCELLED }
data class LessonOperationError(val safeMessage: String, val token: Long)

/** A rejected bounded update never becomes saved progress, even if the transient session advances. */
sealed interface LessonCommit {
    data class Accepted(val status: PersistenceStatus) : LessonCommit
    data class Rejected(val reason: SaveProblem) : LessonCommit
}

/** Runtime identities are never reused after a reload, even when the saved snapshot compares equal. */
private var nextLessonRuntimeId = 1L
private fun lessonRuntimeId(): Long {
    check(nextLessonRuntimeId < Long.MAX_VALUE) { "Session identity exhausted" }
    return nextLessonRuntimeId++
}

/** One requested catalog lookup and at most one transient session. The owner alone owns saved state. */
class LocalLessonCoordinator(
    private val scope: CoroutineScope,
    private val progress: LocalProgressOwner,
    private val loadContent: suspend () -> CatalogLoad,
    private val now: () -> Long = { kotlin.js.Date.now().toLong() },
    private val reducer: (LessonSession, LessonCommand) -> LessonSession = ::reduceLesson,
) {
    constructor(scope: CoroutineScope, progress: LocalProgressOwner, loader: BundledContentLoader) :
        this(scope, progress, loader::load)

    private val mutableState = MutableStateFlow<LocalLessonState>(LocalLessonState.Loading)
    val state: StateFlow<LocalLessonState> get() {
        reconcileOwner()
        return mutableState
    }
    private var requestedId: String? = null
    private var content: BundledContent? = null
    val audioAssets get() = content?.catalog?.audioAssets.orEmpty()
    private var generation = 0L
    private var ownerGeneration = progress.generation
    private var loadJob: Job? = null
    private var disposed = false
    private var errorToken = 0L
    private var pending: LessonOperation? = null
    private val stopObservingOwner = progress.observeGeneration { reconcileOwner() }

    private sealed interface LessonOperation {
        data object Start : LessonOperation
        data object Resume : LessonOperation
        data object Recover : LessonOperation
        data class Finish(val sessionId: Long, val revision: Long) : LessonOperation
        data class Dispatch(val command: LessonCommand) : LessonOperation
    }

    /** A new ID or retry supersedes even a source which ignores coroutine cancellation. */
    fun load(lessonId: String) {
        if (disposed) return
        reconcileOwner()
        if (!progress.isCurrentGeneration(ownerGeneration)) return
        requestedId = lessonId
        beginLoad()
    }

    fun retryLoad() {
        if (disposed) return
        reconcileOwner()
        if (!progress.isCurrentGeneration(ownerGeneration)) return
        // A cancelled parent scope cannot execute another load; its error still offers an exit.
        if (!scope.isActive) return
        if (mutableState.value is LocalLessonState.Error || mutableState.value is LocalLessonState.Empty)
            requestedId?.let(::beginLoadFor)
    }

    private fun beginLoadFor(id: String) {
        requestedId = id
        beginLoad()
    }

    private fun beginLoad() {
        generation++
        loadJob?.cancel()
        content = null
        pending = null
        mutableState.value = LocalLessonState.Loading
        val token = generation
        val job = scope.launch {
            try {
                val result = loadContent()
                currentCoroutineContext().ensureActive()
                reconcileOwner()
                if (disposed || token != generation || !progress.isCurrentGeneration(ownerGeneration)) return@launch
                mutableState.value = when (result) {
                    is CatalogLoad.Empty -> when (result.reason) {
                        EmptyContentReason.EMPTY_CATALOG -> LocalLessonState.Empty(LessonEmptyReason.EMPTY_CATALOG)
                        else -> LocalLessonState.Empty(LessonEmptyReason.NO_LESSONS)
                    }
                    is CatalogLoad.Ready -> if (result.content.lessons.isEmpty()) {
                        LocalLessonState.Empty(LessonEmptyReason.NO_LESSONS)
                    } else {
                        content = result.content
                        projectEntry(result.content)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                reconcileOwner()
                if (disposed || token != generation || !progress.isCurrentGeneration(ownerGeneration)) return@launch
                val bundled = e as? BundledContentException
                val kind = when {
                    e is ContentHttpException || bundled?.cause is ContentHttpException -> LessonLoadErrorKind.HTTP
                    bundled != null -> LessonLoadErrorKind.CONTENT
                    else -> LessonLoadErrorKind.UNEXPECTED
                }
                content = null
                mutableState.value = LocalLessonState.Error(kind, when (kind) {
                    LessonLoadErrorKind.HTTP -> "Unable to read bundled content from this site. Retry the load."
                    LessonLoadErrorKind.CONTENT -> "Unable to load reviewed content. Retry the load."
                    LessonLoadErrorKind.UNEXPECTED -> "Unable to load local content. Retry the load."
                    LessonLoadErrorKind.CANCELLED -> "Lesson loading stopped. Retry or return to Topics."
                })
            }
        }
        loadJob = job
        // A cancelled scope may prevent the coroutine body from running at all. Completion
        // also covers sources which throw CancellationException while the scope stays active.
        job.invokeOnCompletion { cause ->
            if (cause !is CancellationException || disposed || token != generation ||
                !progress.isCurrentGeneration(ownerGeneration) || mutableState.value !is LocalLessonState.Loading) return@invokeOnCompletion
            mutableState.value = LocalLessonState.Error(LessonLoadErrorKind.CANCELLED,
                if (scope.isActive) "Lesson loading stopped. Retry or return to Topics."
                else "Lesson loading stopped. Return to Topics or Home.")
        }
    }

    /** Never treat the URL key as a path, save ID, or fallback to another lesson. */
    private fun projectEntry(bundle: BundledContent): LocalLessonState {
        val id = requestedId ?: return LocalLessonState.Missing("")
        if (!Regex("[A-Za-z0-9][A-Za-z0-9_-]*").matches(id)) return LocalLessonState.Missing(id)
        val lesson = bundle.lessons[id] ?: return LocalLessonState.Missing(id)
        if (bundle.catalog.entries.none { it.id == id && it.kind == DocumentKind.LESSON && it.topicId == lesson.topicId } ||
            lesson.id != id) return LocalLessonState.Missing(id)
        val record = progress.state.value.snapshot.lessonProgress.firstOrNull { it.lessonId == id }
        return when (val checkpoint = resolveLessonCheckpoint(lesson, record)) {
            is LessonCheckpointResolution.Incompatible -> LocalLessonState.RecoveryRequired(lesson, checkpoint)
            else -> LocalLessonState.Entry(lesson, checkpoint)
        }
    }

    /** A reload/reset/restore synchronously invalidates the session, including equal-state reloads. */
    private fun reconcileOwner() {
        if (disposed || ownerGeneration == progress.generation) return
        ownerGeneration = progress.generation
        generation++
        pending = null
        loadJob?.cancel()
        if (!progress.isCurrentGeneration(ownerGeneration)) {
            content = null
            mutableState.value = LocalLessonState.Error(LessonLoadErrorKind.UNEXPECTED,
                "Local progress is unavailable. Return to Topics or Home.")
        } else if (mutableState.value is LocalLessonState.Loading) {
            beginLoad()
        } else {
            mutableState.value = content?.let(::projectEntry) ?: LocalLessonState.Loading
        }
    }

    fun start() = perform(LessonOperation.Start)
    /** Resolving a saved place and opening its prompt never rewrites the save. */
    fun resume() = perform(LessonOperation.Resume)
    fun recoverToSituation() = perform(LessonOperation.Recover)
    fun dispatch(command: LessonCommand) = perform(LessonOperation.Dispatch(command))
    /** Captured Summary identity/revision; repeat Finish for this run is a no-op. */
    fun finish(sessionId: Long, revision: Long) = perform(LessonOperation.Finish(sessionId, revision))

    fun retryOperation(error: LessonOperationError) {
        if (disposed) return
        reconcileOwner()
        val current = mutableState.value
        val shown = when (current) {
            is LocalLessonState.Entry -> current.operationError
            is LocalLessonState.RecoveryRequired -> current.operationError
            is LocalLessonState.Active -> current.operationError
            else -> null
        }
        if (shown == error) pending?.let { perform(it, retrying = true) }
    }

    /** Exit after a failed action without replaying it. Normal Leave uses a guarded command. */
    fun leaveAfterError(error: LessonOperationError) {
        if (disposed) return
        reconcileOwner()
        val current = mutableState.value
        if (current is LocalLessonState.Active && current.operationError == error) {
            pending = null
            content?.let { mutableState.value = projectEntry(it) }
        }
    }

    private fun perform(operation: LessonOperation, retrying: Boolean = false) {
        if (disposed) return
        reconcileOwner()
        if (!progress.isCurrentGeneration(ownerGeneration)) return
        val current = mutableState.value
        val error = when (current) {
            is LocalLessonState.Entry -> current.operationError
            is LocalLessonState.Active -> current.operationError
            is LocalLessonState.RecoveryRequired -> current.operationError
            else -> return
        }
        if (error != null && (!retrying || pending != operation)) return
        try {
            val next = when (operation) {
                LessonOperation.Start -> {
                    val entry = current as? LocalLessonState.Entry ?: return
                    startLessonSession(lessonRuntimeId(), entry.lesson)
                }
                LessonOperation.Resume -> {
                    val entry = current as? LocalLessonState.Entry ?: return
                    val available = entry.checkpoint as? LessonCheckpointResolution.Available ?: return
                    val restored = resumeLessonSession(lessonRuntimeId(), entry.lesson, available.record)
                    (restored as? LessonResumeResult.Available)?.session ?: return
                }
                LessonOperation.Recover -> startLessonSession(lessonRuntimeId(),
                    (current as? LocalLessonState.RecoveryRequired)?.lesson ?: return)
                is LessonOperation.Finish -> {
                    val active = current as? LocalLessonState.Active ?: return
                    if (active.finished || active.session.id != operation.sessionId ||
                        active.session.revision != operation.revision || active.session.stage != LessonStage.SUMMARY ||
                        active.session.left) return
                    active.session
                }
                is LessonOperation.Dispatch -> {
                    val active = current as? LocalLessonState.Active ?: return
                    val command = operation.command
                    if (command.sessionId != active.session.id || command.revision != active.session.revision) return
                    reducer(active.session, if (command is LessonCommand.Restart)
                        command.copy(newSessionId = lessonRuntimeId()) else command)
                }
            }
            // Owner generation may change synchronously inside an injected reducer or initializer.
            reconcileOwner()
            if (disposed || !progress.isCurrentGeneration(ownerGeneration) || mutableState.value !== current) return
            if (operation is LessonOperation.Dispatch && next === current.sessionOrNull()) return
            val lesson = when (current) {
                is LocalLessonState.Entry -> current.lesson
                is LocalLessonState.RecoveryRequired -> current.lesson
                is LocalLessonState.Active -> current.lesson
                else -> return
            }
            // Sample time before entering the owner. A clock callback can itself replace the owner.
            val shouldCommit = !next.left && operation != LessonOperation.Resume &&
                !(operation is LessonOperation.Dispatch && operation.command is LessonCommand.Submit && next.validation != null)
            val timestamp = if (shouldCommit) now() else null
            reconcileOwner()
            if (disposed || !progress.isCurrentGeneration(ownerGeneration) || mutableState.value !== current) return
            // Only a changed, committed cursor/action is saved. Invalid input and Leave are local.
            val commit = when {
                next.left || operation == LessonOperation.Resume -> null
                operation is LessonOperation.Finish -> save { completeLesson(it, lesson.id, lesson.contentVersion, timestamp!!) }
                !shouldCommit -> null
                else -> save { visitLesson(it, lesson.id, lesson.contentVersion,
                    next.stage, next.checkpointId, timestamp!!) }
            }
            reconcileOwner() // The owner may be replaced synchronously during mutate.
            if (disposed || !progress.isCurrentGeneration(ownerGeneration) || mutableState.value !== current) return
            pending = null
            mutableState.value = if (next.left) content?.let(::projectEntry) ?: LocalLessonState.Loading
                else LocalLessonState.Active(lesson, next, commit = commit,
                    finished = (operation !is LessonOperation.Dispatch || operation.command !is LessonCommand.Restart) &&
                        ((current as? LocalLessonState.Active)?.finished == true ||
                            (operation is LessonOperation.Finish && commit is LessonCommit.Accepted)))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            reconcileOwner()
            if (disposed || mutableState.value !== current || !progress.isCurrentGeneration(ownerGeneration)) return
            pending = operation
            val failure = LessonOperationError("Unable to complete this lesson action. Retry or return to Topics.", ++errorToken)
            mutableState.value = when (current) {
                is LocalLessonState.Entry -> current.copy(operationError = failure)
                is LocalLessonState.RecoveryRequired -> current.copy(operationError = failure)
                is LocalLessonState.Active -> current.copy(operationError = failure)
                else -> return
            }
        }
    }

    private fun LocalLessonState.sessionOrNull(): LessonSession? = (this as? LocalLessonState.Active)?.session

    private fun save(update: (com.github.nanaki_93.progress.SaveEnvelope) -> com.github.nanaki_93.progress.ProgressUpdate): LessonCommit =
        when (val result = progress.mutate(update)) {
            ProgressMutationResult.Accepted, ProgressMutationResult.Unchanged ->
                LessonCommit.Accepted(progress.state.value.status)
            is ProgressMutationResult.Rejected -> LessonCommit.Rejected(result.reason)
        }

    fun dispose() {
        if (disposed) return
        disposed = true
        generation++
        stopObservingOwner()
        loadJob?.cancel()
        loadJob = null
        content = null
        pending = null
    }
}
