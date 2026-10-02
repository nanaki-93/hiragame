package com.github.nanaki_93.storage

import com.github.nanaki_93.progress.BackupCodec
import com.github.nanaki_93.progress.BackupEncodeException
import com.github.nanaki_93.progress.BackupProblem
import com.github.nanaki_93.progress.SaveProblem

/** A sink receives complete text; it must not interpret or rewrite protected recovery material. */
fun interface ProgressDownloadSink {
    fun download(filename: String, contentType: String, text: String)
}

sealed interface DownloadResult {
    /** The label distinguishes unsaved work from a confirmed browser save. */
    data class Downloaded(val filename: String, val label: String) : DownloadResult
    data object OriginalUnavailable : DownloadResult
    data class InvalidSnapshot(val reason: SaveProblem) : DownloadResult
    data object Failed : DownloadResult
}

/** Prepares downloads from the owner's current memory, never from a stale storage reread. */
class ProgressDownloads(
    private val owner: LocalProgressOwner,
    private val sink: ProgressDownloadSink,
    private val clock: () -> Long,
) {
    constructor(owner: LocalProgressOwner, sink: ProgressDownloadSink) : this(
        owner, sink, { kotlin.js.Date.now().toLong() },
    )

    fun exportCurrent(): DownloadResult {
        val current = owner.state.value
        val exportedAt = try { clock() } catch (_: Throwable) { return DownloadResult.Failed }
        val text = try {
            BackupCodec.encodeBackup(current.snapshot, SiteBuildInfo.VERSION, exportedAt)
        } catch (error: BackupEncodeException) {
            return DownloadResult.InvalidSnapshot(if (error.reason == BackupProblem.OVERSIZED) SaveProblem.OVERSIZED else SaveProblem.INVALID_SNAPSHOT)
        }
        val label = if (current.status == PersistenceStatus.Saved) {
            "Validated progress backup download requested"
        } else {
            "Validated progress export download requested (includes work not confirmed saved in this browser)"
        }
        return deliver("hiragame-backup", "json", "application/json;charset=utf-8", text, label, exportedAt)
    }

    /** Exact originally read text; explicitly NOT a validated backup or an importable save. */
    fun downloadProtectedOriginal(): DownloadResult {
        val original = owner.originalProtectedRaw ?: return DownloadResult.OriginalUnavailable
        return deliver(
            "hiragame-unvalidated-recovery", "txt", "text/plain;charset=utf-8",
            original,
            "Unvalidated original recovery text (not a validated/importable backup)",
        )
    }

    private fun date(epochMs: Long): String = kotlin.js.Date(epochMs.toDouble()).toISOString().substring(0, 10)

    private fun deliver(prefix: String, extension: String, type: String, text: String, label: String, time: Long? = null): DownloadResult =
        try {
            val filename = "$prefix-${date(time ?: clock())}.$extension"
            sink.download(filename, type, text)
            DownloadResult.Downloaded(filename, label)
        } catch (_: Throwable) {
            // Browser denial, Blob/URL/DOM errors and test-injected failures must not mutate progress.
            DownloadResult.Failed
        }
}
