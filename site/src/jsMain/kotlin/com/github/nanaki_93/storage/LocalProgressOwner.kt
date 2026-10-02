package com.github.nanaki_93.storage

import com.github.nanaki_93.progress.ProgressUpdate
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

/** Opaque, one-use authorization for replacing only the protected original seen at issuance. */
class ProtectedReplacementToken internal constructor()

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
    private data class PendingReplacement(
        val token: ProtectedReplacementToken,
        val baseline: String,
        val generation: Long,
        val snapshot: SaveEnvelope,
    )
    private var pendingReplacement: PendingReplacement? = null
    /** Changes only on explicit reload (or disposal); consumers can reject stale callbacks. */
    var generation: Long = 0
        private set

    private val mutableState: MutableStateFlow<LocalProgressState>
    val state: StateFlow<LocalProgressState> get() = mutableState
    /** Exact unvalidated recovery material, if the initial stored value was protected. */
    val originalProtectedRaw: String? get() = protectedRaw
    /** The raw text last successfully read or written, not a serialization of the current memory state. */
    val savedBaseline: String? get() = acknowledgedRaw

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

    /** Present the data-loss warning before requesting this token; no read or write occurs here. */
    fun beginProtectedReplacement(): ProtectedReplacementToken? {
        if (disposed || mutableState.value.status !is PersistenceStatus.Protected || !baselineKnown) return null
        val raw = protectedRaw ?: return null
        if (raw != acknowledgedRaw) return null
        val token = ProtectedReplacementToken()
        pendingReplacement = PendingReplacement(token, raw, generation, mutableState.value.snapshot)
        return token
    }

    /** Cancel is idempotent and never touches the store. */
    fun cancelProtectedReplacement(token: ProtectedReplacementToken): Boolean {
        if (disposed || pendingReplacement?.token !== token) return false
        pendingReplacement = null
        return true
    }

    /** Recovery is storage-first: only a successful baseline-checked write publishes fresh state. */
    fun confirmProtectedReplacement(token: ProtectedReplacementToken): ProtectedReplacementResult {
        val pending = pendingReplacement
        if (disposed || pending == null || pending.token !== token) return ProtectedReplacementResult.Stale
        pendingReplacement = null // consume even when a read or write fails
        if (pending.generation != generation ||
            mutableState.value.status !is PersistenceStatus.Protected ||
            mutableState.value.snapshot != pending.snapshot ||
            protectedRaw != pending.baseline || acknowledgedRaw != pending.baseline
        ) return ProtectedReplacementResult.Stale

        // An explicit reread catches changes even when the storage event was missed. The store
        // also compares immediately before its single setItem, for a change between read and write.
        when (val read = store.read()) {
            is StoreReadResult.Failure -> return ProtectedReplacementResult.Failure(read.reason)
            StoreReadResult.Missing -> {
                mutableState.value = mutableState.value.copy(status = PersistenceStatus.Conflict)
                return ProtectedReplacementResult.Conflict
            }
            is StoreReadResult.Raw -> if (read.value != pending.baseline) {
                mutableState.value = mutableState.value.copy(status = PersistenceStatus.Conflict)
                return ProtectedReplacementResult.Conflict
            }
        }
        val replacement = try {
            fresh()
        } catch (error: SaveEncodeException) {
            return ProtectedReplacementResult.InvalidReplacement(error.reason)
        }
        val wire = encodeSave(replacement)
        return when (val result = store.write(pending.baseline, wire)) {
            StoreWriteResult.Written -> {
                acknowledgedRaw = wire
                protectedRaw = null
                generation++ // invalidate callbacks against the old learning state
                mutableState.value = LocalProgressState(replacement, PersistenceStatus.Saved)
                ProtectedReplacementResult.Replaced
            }
            is StoreWriteResult.Conflict -> {
                mutableState.value = mutableState.value.copy(status = PersistenceStatus.Conflict)
                ProtectedReplacementResult.Conflict
            }
            is StoreWriteResult.Failure -> ProtectedReplacementResult.Failure(result.reason)
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
        mutableState.value = current.copy(
            snapshot = if (status == PersistenceStatus.Saved) prepared.first else current.snapshot,
            status = status,
            rejectedUpdate = null,
        )
        return status
    }
}
