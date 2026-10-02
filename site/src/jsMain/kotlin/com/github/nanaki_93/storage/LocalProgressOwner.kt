package com.github.nanaki_93.storage

import com.github.nanaki_93.progress.ProgressUpdate
import com.github.nanaki_93.progress.SaveBounds
import com.github.nanaki_93.progress.SaveCodec
import com.github.nanaki_93.progress.SaveDecodeResult
import com.github.nanaki_93.progress.SaveEncodeException
import com.github.nanaki_93.progress.SaveEnvelope
import com.github.nanaki_93.progress.SaveProblem
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

/** Synchronous, application-lifetime owner. Browser APIs live only in the injected store. */
class LocalProgressOwner(
    private val store: ProgressStore,
    private val clock: () -> Long,
    private val newSnapshotId: () -> String,
) {
    constructor(store: ProgressStore) : this(
        store,
        { kotlin.js.Date.now().toLong() },
        { "snapshot_${Random.nextInt().toUInt().toString(16)}_${Random.nextInt().toUInt().toString(16)}" },
    )

    private var acknowledgedRaw: String? = null
    private var baselineKnown = false
    private var protectedRaw: String? = null

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
                LocalProgressState(fresh(), PersistenceStatus.Fresh)
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
    }

    private fun fresh() = SaveEnvelope(savedAtEpochMs = clock(), snapshotId = newSnapshotId(), revision = 0).also {
        encodeSave(it)
    }

    /** Transform only the current validated snapshot. Rejected candidates leave it unchanged. */
    fun mutate(transform: (SaveEnvelope) -> ProgressUpdate): ProgressMutationResult {
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

    /** Re-encode latest accepted memory, without replaying the action that produced it. */
    fun retrySaving(): PersistenceStatus {
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
