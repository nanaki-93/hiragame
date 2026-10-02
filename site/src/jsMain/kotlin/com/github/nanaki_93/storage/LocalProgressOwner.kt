package com.github.nanaki_93.storage

import com.github.nanaki_93.progress.ProgressUpdate
import com.github.nanaki_93.progress.ResetScope
import com.github.nanaki_93.progress.resetLearnerState
import com.github.nanaki_93.progress.ValidatedBackup
import com.github.nanaki_93.progress.SaveBounds
import com.github.nanaki_93.progress.SaveCodec
import com.github.nanaki_93.progress.SaveDecodeResult
import com.github.nanaki_93.progress.SaveEncodeException
import com.github.nanaki_93.progress.SaveEnvelope
import com.github.nanaki_93.progress.SavePreferences
import com.github.nanaki_93.progress.SaveProblem
import com.github.nanaki_93.progress.SavedColorMode
import com.github.nanaki_93.progress.encodeSave
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

/** A protected original is never replaced by an ordinary learning update. */
sealed interface PersistenceStatus {
    data object Fresh : PersistenceStatus
    data object Saved : PersistenceStatus
    data class MemoryOnly(val reason: StoreFailure) : PersistenceStatus
    data class Protected(val reason: SaveProblem) : PersistenceStatus
    data object Conflict : PersistenceStatus
}

data class LocalProgressState(
    val snapshot: SaveEnvelope,
    val status: PersistenceStatus,
    /** A rejected update did not become learning state. Never include saved text in this value. */
    val rejectedUpdate: SaveProblem? = null,
)

sealed interface ProgressMutationResult {
    data object Accepted : ProgressMutationResult
    data object Unchanged : ProgressMutationResult
    data class Rejected(val reason: SaveProblem) : ProgressMutationResult
}

/** Opaque, one-use authorization; only the issuing owner can consume it. */
class ReplacementToken internal constructor()

sealed interface ReplacementPreparation {
    data class Ready(val token: ReplacementToken) : ReplacementPreparation
    data object Blocked : ReplacementPreparation
}

sealed interface ReplacementResult {
    data object Replaced : ReplacementResult
    data object Stale : ReplacementResult
    data object Conflict : ReplacementResult
    data class Failure(val reason: StoreFailure) : ReplacementResult
    data class InvalidReplacement(val reason: SaveProblem) : ReplacementResult
}

// Keep the existing Home recovery API while other replacement operations are introduced.
typealias ProtectedReplacementToken = ReplacementToken

sealed interface ProtectedReplacementResult {
    data object Replaced : ProtectedReplacementResult
    data object Stale : ProtectedReplacementResult
    data object Conflict : ProtectedReplacementResult
    data class Failure(val reason: StoreFailure) : ProtectedReplacementResult
    data class InvalidReplacement(val reason: SaveProblem) : ProtectedReplacementResult
}

/** Synchronous, application-lifetime owner. Browser APIs live only in the injected store. */
class LocalProgressOwner(
    private val store: ProgressStore,
    private val clock: () -> Long,
    private val newSnapshotId: () -> String,
    private val legacyColorMode: () -> SavedColorMode? = { null },
) {
    constructor(store: ProgressStore, legacyColorMode: () -> SavedColorMode? = { null }) : this(
        store,
        { kotlin.js.Date.now().toLong() },
        { "snapshot_${Random.nextInt().toUInt().toString(16)}_${Random.nextInt().toUInt().toString(16)}" },
        legacyColorMode,
    )

    private var acknowledgedRaw: String? = null
    private var baselineKnown = false
    private var protectedRaw: String? = null
    private var disposed = false
    private var subscription: StoreSubscription? = null
    private sealed interface ReplacementOperation {
        data object ProtectedFresh : ReplacementOperation
        data class Restore(val candidate: ValidatedBackup) : ReplacementOperation
        data class Reset(val scope: ResetScope) : ReplacementOperation
    }
    private data class PendingReplacement(
        val token: ReplacementToken,
        val operation: ReplacementOperation,
        val baseline: String?,
        val generation: Long,
        val snapshot: SaveEnvelope,
        val status: PersistenceStatus,
    )
    private var pendingReplacement: PendingReplacement? = null
    /** Changes only on explicit reload (or disposal); consumers can reject stale callbacks. */
    var generation: Long = 0
        private set
    private val generationListeners = mutableSetOf<(Long) -> Unit>()

    /** Synchronous notification, including reloads whose resulting state compares equal. */
    fun observeGeneration(listener: (Long) -> Unit): () -> Unit {
        if (!disposed) generationListeners.add(listener)
        return { generationListeners.remove(listener) }
    }

    private fun notifyGenerationChanged() {
        generationListeners.toList().forEach { it(generation) }
    }

    private val mutableState: MutableStateFlow<LocalProgressState>
    val state: StateFlow<LocalProgressState> get() = mutableState
    /** Exact unvalidated recovery material, if the initial stored value was protected. */
    val originalProtectedRaw: String? get() = protectedRaw
    /** The raw text last successfully read or written, not a serialization of the current memory state. */
    val savedBaseline: String? get() = acknowledgedRaw
    /** Distinguishes a confirmed missing key from an unreadable startup baseline (both have null raw). */
    val hasKnownBaseline: Boolean get() = baselineKnown

    init {
        val initial = when (val read = store.read()) {
            StoreReadResult.Missing -> {
                baselineKnown = true
                // Only a confirmed missing main key may import the old preference. This is
                // memory-only; startup never writes or removes either storage key.
                val mode = try { legacyColorMode() } catch (_: Throwable) { null }
                LocalProgressState(fresh(mode), PersistenceStatus.Fresh)
            }
            is StoreReadResult.Failure -> LocalProgressState(fresh(), PersistenceStatus.MemoryOnly(read.reason))
            is StoreReadResult.Raw -> {
                baselineKnown = true
                acknowledgedRaw = read.value
                when (val decoded = SaveCodec.decodeSave(read.value, newSnapshotId)) {
                    is SaveDecodeResult.Valid -> LocalProgressState(decoded.snapshot, PersistenceStatus.Saved)
                    is SaveDecodeResult.Protected -> {
                        protectedRaw = read.value
                        LocalProgressState(fresh(), PersistenceStatus.Protected(decoded.reason))
                    }
                }
            }
        }
        mutableState = MutableStateFlow(initial)
        subscription = store.subscribe {
            if (!disposed) onExternalChange()
        }
    }

    private fun onExternalChange() {
        // Events signal a need to read; their payload is not trusted as the current value.
        val observed = when (val read = store.read()) {
            StoreReadResult.Missing -> null
            is StoreReadResult.Raw -> read.value
            is StoreReadResult.Failure -> {
                // Until a successful read or explicit retry, equality cannot be established.
                pendingReplacement = null
                if (mutableState.value.status != PersistenceStatus.Conflict &&
                    mutableState.value.status !is PersistenceStatus.Protected
                ) {
                    mutableState.value = mutableState.value.copy(status = PersistenceStatus.MemoryOnly(read.reason))
                }
                return
            }
        }
        if (!baselineKnown || observed != acknowledgedRaw) {
            pendingReplacement = null
            mutableState.value = mutableState.value.copy(status = PersistenceStatus.Conflict)
        }
    }

    /** Capture at consumer creation; callbacks from before reload/disposal must not act on new state. */
    fun isCurrentGeneration(expected: Long): Boolean = !disposed && expected == generation

    /** Keep accepted in-memory work. A known conflict cannot resume automatic saving. */
    fun keepThisView(): PersistenceStatus = mutableState.value.status

    /** Discard memory only after a successful read; even a protected replacement stays recoverable. */
    fun reloadSavedState(): Boolean {
        if (disposed) return false
        val read = store.read()
        if (read is StoreReadResult.Failure) return false
        val loaded = when (read) {
            StoreReadResult.Missing -> LocalProgressState(fresh(), PersistenceStatus.Fresh)
            is StoreReadResult.Raw -> when (val decoded = SaveCodec.decodeSave(read.value, newSnapshotId)) {
                is SaveDecodeResult.Valid -> LocalProgressState(decoded.snapshot, PersistenceStatus.Saved)
                is SaveDecodeResult.Protected -> LocalProgressState(fresh(), PersistenceStatus.Protected(decoded.reason))
            }
            is StoreReadResult.Failure -> error("Handled above")
        }
        pendingReplacement = null
        acknowledgedRaw = (read as? StoreReadResult.Raw)?.value
        protectedRaw = if (loaded.status is PersistenceStatus.Protected) acknowledgedRaw else null
        baselineKnown = true
        generation++
        mutableState.value = loaded
        notifyGenerationChanged()
        return true
    }

    /** Listener disposal is idempotent, and even a queued callback cannot affect this owner. */
    fun dispose() {
        if (disposed) return
        disposed = true
        pendingReplacement = null
        generation++
        subscription?.dispose()
        subscription = null
        notifyGenerationChanged()
        generationListeners.clear()
    }

    private fun fresh(colorMode: SavedColorMode? = null) = SaveEnvelope(
        savedAtEpochMs = clock(),
        snapshotId = newSnapshotId(),
        revision = 0,
        preferences = SavePreferences(colorMode = colorMode ?: SavedColorMode.SYSTEM),
    ).also { encodeSave(it) }

    /** Transform only the current validated snapshot. Rejected candidates leave it unchanged. */
    fun mutate(transform: (SaveEnvelope) -> ProgressUpdate): ProgressMutationResult {
        if (disposed) return ProgressMutationResult.Rejected(SaveProblem.INVALID_SNAPSHOT)
        val previous = mutableState.value
        val update = transform(previous.snapshot)
        val candidate = when (update) {
            is ProgressUpdate.Rejected -> return reject(SaveProblem.INVALID_SNAPSHOT)
            is ProgressUpdate.Unchanged -> return ProgressMutationResult.Unchanged
            is ProgressUpdate.Applied -> update.snapshot
        }
        // Metadata belongs to the owner, not to callers of the pure progress APIs.
        if (candidate.schemaVersion != previous.snapshot.schemaVersion ||
            candidate.snapshotId != previous.snapshot.snapshotId ||
            candidate.revision != previous.snapshot.revision ||
            candidate.savedAtEpochMs != previous.snapshot.savedAtEpochMs || candidate == previous.snapshot
        ) return reject(SaveProblem.INVALID_SNAPSHOT)
        // Validate the entire candidate, including its serialized size, before publishing it.
        try {
            encodeSave(candidate)
        } catch (error: SaveEncodeException) {
            return reject(error.reason)
        }
        if (candidate.revision >= SaveBounds.MAX_SAFE_INTEGER) return reject(SaveProblem.INVALID_SNAPSHOT)
        // Protected and conflicted originals must never be replaced by an ordinary mutation.
        // After a failed write, retain accepted work with its old revision and stop automatic attempts.
        var accepted = candidate
        val status = when (previous.status) {
            PersistenceStatus.Fresh, PersistenceStatus.Saved -> {
                val prepared = try {
                    prepareWrite(candidate)
                } catch (error: SaveEncodeException) {
                    return reject(error.reason)
                }
                val outcome = write(prepared.second)
                if (outcome == PersistenceStatus.Saved) accepted = prepared.first
                outcome
            }
            else -> previous.status
        }
        pendingReplacement = null // changed learning state requires a new, explicit confirmation
        mutableState.value = LocalProgressState(accepted, status)
        return ProgressMutationResult.Accepted
    }

    private fun prepareWrite(snapshot: SaveEnvelope): Pair<SaveEnvelope, String> {
        if (snapshot.revision >= SaveBounds.MAX_SAFE_INTEGER) throw SaveEncodeException(SaveProblem.INVALID_SNAPSHOT)
        val next = snapshot.copy(
            revision = snapshot.revision + 1,
            snapshotId = newSnapshotId(),
            savedAtEpochMs = clock(),
        )
        return next to encodeSave(next)
    }

    private fun reject(reason: SaveProblem): ProgressMutationResult.Rejected {
        mutableState.value = mutableState.value.copy(rejectedUpdate = reason)
        return ProgressMutationResult.Rejected(reason)
    }

    private fun write(wire: String): PersistenceStatus {
        // No unknown baseline can be used to replace a stored value.
        if (!baselineKnown) return PersistenceStatus.MemoryOnly(StoreFailure.DENIED)
        return when (val result = store.write(acknowledgedRaw, wire)) {
            StoreWriteResult.Written -> {
                acknowledgedRaw = wire
                PersistenceStatus.Saved
            }
            is StoreWriteResult.Failure -> PersistenceStatus.MemoryOnly(result.reason)
            is StoreWriteResult.Conflict -> PersistenceStatus.Conflict
        }
    }

    /** Issuance never reads or writes storage. A new preparation supersedes the last one. */
    private fun beginReplacement(operation: ReplacementOperation): ReplacementPreparation {
        // A new request supersedes any older authorization, even if it cannot be issued.
        pendingReplacement = null
        if (disposed || !baselineKnown) return ReplacementPreparation.Blocked
        val current = mutableState.value
        if (current.status == PersistenceStatus.Conflict ||
            (operation == ReplacementOperation.ProtectedFresh &&
                (current.status !is PersistenceStatus.Protected || protectedRaw == null || protectedRaw != acknowledgedRaw)) ||
            (operation is ReplacementOperation.Reset && operation.scope == ResetScope.PROGRESS_ONLY &&
                current.status is PersistenceStatus.Protected) ||
            (current.status is PersistenceStatus.Protected && protectedRaw != acknowledgedRaw)
        ) return ReplacementPreparation.Blocked
        val token = ReplacementToken()
        pendingReplacement = PendingReplacement(token, operation, acknowledgedRaw, generation,
            current.snapshot, current.status)
        return ReplacementPreparation.Ready(token)
    }

    /** Prepare explicit recovery without reading storage or publishing a fresh snapshot. */
    fun prepareProtectedReplacement(): ReplacementPreparation =
        beginReplacement(ReplacementOperation.ProtectedFresh)

    /** An unknown startup baseline must be reconciled before any explicit replacement. */
    private fun beginReconciledReplacement(operation: ReplacementOperation): ReplacementPreparation {
        pendingReplacement = null
        if (disposed) return ReplacementPreparation.Blocked
        if (!baselineKnown && mutableState.value.status is PersistenceStatus.MemoryOnly) {
            when (val read = store.read()) {
                StoreReadResult.Missing -> {
                    baselineKnown = true
                    acknowledgedRaw = null
                }
                is StoreReadResult.Raw -> {
                    // Never replace newly discovered data without an explicit reload/reconciliation.
                    mutableState.value = mutableState.value.copy(status = PersistenceStatus.Conflict)
                    return ReplacementPreparation.Blocked
                }
                is StoreReadResult.Failure -> {
                    mutableState.value = mutableState.value.copy(status = PersistenceStatus.MemoryOnly(read.reason))
                    return ReplacementPreparation.Blocked
                }
            }
        }
        return beginReplacement(operation)
    }

    /** A preview is not authorization. */
    fun beginRestore(candidate: ValidatedBackup): ReplacementPreparation =
        beginReconciledReplacement(ReplacementOperation.Restore(candidate))

    /** Scope is bound to the one-use token; even an ineligible request supersedes older tokens. */
    fun beginReset(scope: ResetScope): ReplacementPreparation =
        beginReconciledReplacement(ReplacementOperation.Reset(scope))

    /** Compatibility entry point for the existing explicitly warned protected recovery control. */
    fun beginProtectedReplacement(): ProtectedReplacementToken? =
        (prepareProtectedReplacement() as? ReplacementPreparation.Ready)?.token

    /** Cancel is idempotent and never touches the store. */
    fun cancelReplacement(token: ReplacementToken): Boolean {
        if (disposed || pendingReplacement?.token !== token) return false
        pendingReplacement = null
        return true
    }

    fun cancelProtectedReplacement(token: ProtectedReplacementToken): Boolean = cancelReplacement(token)

    fun confirmProtectedReplacement(token: ProtectedReplacementToken): ProtectedReplacementResult =
        when (val result = confirmReplacement(token)) {
            ReplacementResult.Replaced -> ProtectedReplacementResult.Replaced
            ReplacementResult.Stale -> ProtectedReplacementResult.Stale
            ReplacementResult.Conflict -> ProtectedReplacementResult.Conflict
            is ReplacementResult.Failure -> ProtectedReplacementResult.Failure(result.reason)
            is ReplacementResult.InvalidReplacement -> ProtectedReplacementResult.InvalidReplacement(result.reason)
        }

    /** Supplier failures are invalid replacement metadata, not exceptions escaping confirmation. */
    private fun replacementTime(): Long = try {
        clock()
    } catch (_: Throwable) {
        throw SaveEncodeException(SaveProblem.INVALID_SNAPSHOT)
    }

    private fun replacementId(): String = try {
        newSnapshotId()
    } catch (_: Throwable) {
        throw SaveEncodeException(SaveProblem.INVALID_SNAPSHOT)
    }

    /** The only destructive path: prepare, reread, compare, then one guarded write. */
    fun confirmReplacement(token: ReplacementToken): ReplacementResult {
        val pending = pendingReplacement
        if (disposed || pending == null || pending.token !== token) return ReplacementResult.Stale
        pendingReplacement = null // consume before even attempting a read
        if (pending.generation != generation || !baselineKnown ||
            mutableState.value.snapshot != pending.snapshot ||
            mutableState.value.status != pending.status ||
            acknowledgedRaw != pending.baseline ||
            (pending.status is PersistenceStatus.Protected && protectedRaw != pending.baseline)
        ) return ReplacementResult.Stale

        // Validate the complete replacement before touching storage. Never publish a candidate
        // through mutate: mutate intentionally accepts memory-only changes after failed writes.
        val replacement: SaveEnvelope
        val wire: String
        try {
            replacement = when (val operation = pending.operation) {
                ReplacementOperation.ProtectedFresh -> SaveEnvelope(
                    savedAtEpochMs = replacementTime(), snapshotId = replacementId(), revision = 0,
                )
                is ReplacementOperation.Reset -> {
                    val cleared = resetLearnerState(pending.snapshot, operation.scope)
                    val revision = if (pending.status is PersistenceStatus.Protected) 0L else {
                        if (pending.snapshot.revision >= SaveBounds.MAX_SAFE_INTEGER) {
                            throw SaveEncodeException(SaveProblem.INVALID_SNAPSHOT)
                        }
                        pending.snapshot.revision + 1
                    }
                    val localId = replacementId()
                    if (localId == pending.snapshot.snapshotId) {
                        throw SaveEncodeException(SaveProblem.INVALID_SNAPSHOT)
                    }
                    cleared.copy(snapshotId = localId, savedAtEpochMs = replacementTime(), revision = revision)
                }
                is ReplacementOperation.Restore -> {
                    val imported = operation.candidate.snapshot
                    // Reject fabricated/stale candidates before copying them; do not trust their
                    // identity or revision as authority for the local write sequence.
                    encodeSave(imported)
                    val revision = if (pending.status is PersistenceStatus.Protected) 0L else {
                        if (pending.snapshot.revision >= SaveBounds.MAX_SAFE_INTEGER) {
                            throw SaveEncodeException(SaveProblem.INVALID_SNAPSHOT)
                        }
                        pending.snapshot.revision + 1
                    }
                    val localId = replacementId()
                    if (localId == imported.snapshotId || localId == pending.snapshot.snapshotId) {
                        throw SaveEncodeException(SaveProblem.INVALID_SNAPSHOT)
                    }
                    imported.copy(snapshotId = localId, savedAtEpochMs = replacementTime(), revision = revision)
                }
            }
            wire = encodeSave(replacement)
        } catch (error: SaveEncodeException) {
            return ReplacementResult.InvalidReplacement(error.reason)
        }

        // The owner reread catches missed events; the store compares again before its write.
        // These comparisons are not an atomic cross-tab compare-and-swap.
        val observed = when (val read = store.read()) {
            is StoreReadResult.Failure -> return ReplacementResult.Failure(read.reason)
            StoreReadResult.Missing -> null
            is StoreReadResult.Raw -> read.value
        }
        if (observed != pending.baseline || pending.generation != generation ||
            mutableState.value.snapshot != pending.snapshot || mutableState.value.status != pending.status ||
            acknowledgedRaw != pending.baseline
        ) {
            mutableState.value = mutableState.value.copy(status = PersistenceStatus.Conflict)
            return ReplacementResult.Conflict
        }
        return when (val result = store.write(pending.baseline, wire)) {
            StoreWriteResult.Written -> {
                acknowledgedRaw = wire
                protectedRaw = null
                generation++ // only a committed replacement invalidates practice callbacks
                mutableState.value = LocalProgressState(replacement, PersistenceStatus.Saved)
                notifyGenerationChanged()
                ReplacementResult.Replaced
            }
            is StoreWriteResult.Conflict -> {
                mutableState.value = mutableState.value.copy(status = PersistenceStatus.Conflict)
                ReplacementResult.Conflict
            }
            is StoreWriteResult.Failure -> ReplacementResult.Failure(result.reason)
        }
    }

    /** Re-encode latest accepted memory, without replaying the action that produced it. */
    fun retrySaving(): PersistenceStatus {
        if (disposed) return mutableState.value.status
        val current = mutableState.value
        if (current.status !is PersistenceStatus.MemoryOnly) return current.status
        if (!baselineKnown) {
            when (val read = store.read()) {
                StoreReadResult.Missing -> baselineKnown = true
                is StoreReadResult.Raw -> {
                    // The first read was denied: there is no safe expected baseline for this value.
                    mutableState.value = current.copy(status = PersistenceStatus.Conflict)
                    return PersistenceStatus.Conflict
                }
                is StoreReadResult.Failure -> {
                    mutableState.value = current.copy(status = PersistenceStatus.MemoryOnly(read.reason))
                    return mutableState.value.status
                }
            }
        }
        val prepared = try {
            prepareWrite(current.snapshot)
        } catch (error: SaveEncodeException) {
            reject(error.reason)
            return mutableState.value.status
        }
        val status = write(prepared.second)
        if (status == PersistenceStatus.Saved || status == PersistenceStatus.Conflict) pendingReplacement = null
        mutableState.value = current.copy(
            snapshot = if (status == PersistenceStatus.Saved) prepared.first else current.snapshot,
            status = status,
            rejectedUpdate = null,
        )
        return status
    }
}
