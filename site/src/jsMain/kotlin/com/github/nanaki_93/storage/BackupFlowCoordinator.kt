package com.github.nanaki_93.storage

import com.github.nanaki_93.progress.BackupCodec
import com.github.nanaki_93.progress.BackupDecodeResult
import com.github.nanaki_93.progress.BackupProblem
import com.github.nanaki_93.progress.SavePreferences
import com.github.nanaki_93.progress.ValidatedBackup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Presentation-only values. Dates are epoch milliseconds; the UI formats them for its locale. */
data class BackupPreview(
    val filename: String,
    val exportedAtEpochMs: Long?,
    val snapshotAtEpochMs: Long,
    val appVersion: String?,
    val sourceSchemaVersion: Int,
    val migratedFrom: Int?,
    val preferences: SavePreferences,
    val lessonCount: Int,
    val completedLessonCount: Int,
    val practiceCheckpointCount: Int,
    val reviewItemCount: Int,
)

/** Fixed categories only: no raw input, platform exception or parser diagnostic escapes. */
sealed interface BackupFlowError {
    data class Read(val reason: BackupReadError) : BackupFlowError
    data class Validation(val reason: BackupProblem) : BackupFlowError
}

enum class BackupReadError { EMPTY, TOO_LARGE, INVALID_UTF8, FAILED, TIMED_OUT }

sealed interface BackupFlowState {
    data object Idle : BackupFlowState
    data class Reading(val filename: String) : BackupFlowState
    data class Preview(val details: BackupPreview) : BackupFlowState
    data class Error(val reason: BackupFlowError) : BackupFlowState
}

/** One transient candidate at a time. Selection and preview never authorize a learner write. */
class BackupFlowCoordinator(
    private val reader: BackupFileReader = BackupFileReader(),
    private val newSnapshotId: () -> String = {
        "migration_${kotlin.random.Random.nextInt().toUInt().toString(16)}_${kotlin.random.Random.nextInt().toUInt().toString(16)}"
    },
) {
    private val mutableState = MutableStateFlow<BackupFlowState>(BackupFlowState.Idle)
    val state: StateFlow<BackupFlowState> get() = mutableState
    private var candidate: ValidatedBackup? = null
    private var pending: BackupReadHandle? = null
    private var selection = 0L
    private var disposed = false

    /** Name is display-only and never used as a decoder hint, path, URL or export filename. */
    fun select(file: Any, filename: String) {
        if (disposed) return
        invalidate()
        val request = selection
        val displayName = filename.take(255).let {
            if (it.isNotEmpty() && it.last().isHighSurrogate()) it.dropLast(1) else it
        }
        mutableState.value = BackupFlowState.Reading(displayName)
        // BackupFileReader may finish synchronously (size rejection or a fake platform).
        // Do not retain a completed handle, or overwrite a newer selection from a callback.
        val handle = reader.read(file) { result ->
            if (disposed || selection != request) return@read
            pending = null
            when (result) {
                is BackupReadResult.Text -> {
                    val decoded = try { BackupCodec.decodeBackup(result.value, newSnapshotId) }
                        catch (_: Throwable) { BackupDecodeResult.Rejected(BackupProblem.INVALID_SAVE) }
                    if (disposed || selection != request) return@read
                    when (decoded) {
                        is BackupDecodeResult.Rejected -> mutableState.value =
                            BackupFlowState.Error(BackupFlowError.Validation(decoded.reason))
                        is BackupDecodeResult.Valid -> {
                            candidate = decoded.backup
                            mutableState.value = BackupFlowState.Preview(decoded.backup.preview(displayName))
                        }
                    }
                }
                else -> mutableState.value = BackupFlowState.Error(BackupFlowError.Read(when (result) {
                    BackupReadResult.Empty -> BackupReadError.EMPTY
                    BackupReadResult.TooLarge -> BackupReadError.TOO_LARGE
                    BackupReadResult.InvalidUtf8 -> BackupReadError.INVALID_UTF8
                    BackupReadResult.Failed -> BackupReadError.FAILED
                    BackupReadResult.TimedOut -> BackupReadError.TIMED_OUT
                    is BackupReadResult.Text -> error("Handled above")
                }))
            }
        }
        if (selection == request && mutableState.value is BackupFlowState.Reading && !disposed) pending = handle
        else handle.cancel()
    }

    /** Also handles a dismissed picker; a subsequent selection may use the same file. */
    fun cancel() {
        if (disposed) return
        invalidate()
        mutableState.value = BackupFlowState.Idle
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        invalidate()
        reader.dispose()
        mutableState.value = BackupFlowState.Idle
    }

    private fun invalidate() {
        selection++
        val old = pending
        pending = null
        candidate = null
        old?.cancel()
    }

    private fun ValidatedBackup.preview(filename: String) = BackupPreview(
        filename = filename,
        exportedAtEpochMs = exportedAtEpochMs,
        snapshotAtEpochMs = snapshot.savedAtEpochMs,
        appVersion = appVersion,
        sourceSchemaVersion = sourceSchemaVersion,
        migratedFrom = migratedFrom,
        preferences = snapshot.preferences,
        lessonCount = snapshot.lessonProgress.size,
        completedLessonCount = snapshot.lessonProgress.count { it.completedAtEpochMs != null },
        practiceCheckpointCount = snapshot.practiceProgress.size,
        reviewItemCount = snapshot.reviewItems.size,
    )
}
