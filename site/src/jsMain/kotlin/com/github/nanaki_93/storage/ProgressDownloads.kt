package com.github.nanaki_93.storage

import com.github.nanaki_93.progress.SaveEncodeException
import com.github.nanaki_93.progress.SaveProblem
import com.github.nanaki_93.progress.encodeSave

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
        val snapshot = owner.state.value.snapshot
        val text = try {
            encodeSave(snapshot)
        } catch (error: SaveEncodeException) {
            return DownloadResult.InvalidSnapshot(error.reason)
        }
        val label = if (owner.state.value.status == PersistenceStatus.Saved) {
            "Validated progress backup"
        } else {
            "Validated progress export (includes work not confirmed saved in this browser)"
        }
        return deliver("hiragame-state", "json", "application/json;charset=utf-8", text, label)
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

    private fun date(): String = kotlin.js.Date(clock().toDouble()).toISOString().substring(0, 10)

    private fun deliver(prefix: String, extension: String, type: String, text: String, label: String): DownloadResult =
        try {
            val filename = "$prefix-${date()}.$extension"
            sink.download(filename, type, text)
            DownloadResult.Downloaded(filename, label)
        } catch (_: Throwable) {
            // Browser denial, Blob/URL/DOM errors and test-injected failures must not mutate progress.
            DownloadResult.Failed
        }
}
