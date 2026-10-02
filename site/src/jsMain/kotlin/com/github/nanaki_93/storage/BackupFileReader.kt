package com.github.nanaki_93.storage

import com.github.nanaki_93.progress.SaveBounds

/** File metadata is only a size hint. Neither name, extension nor MIME type authorizes an import. */
internal interface BackupFilePlatform {
    fun byteSize(file: Any): Long?
    fun read(file: Any, completed: (Result<ByteArray>) -> Unit): BackupFileResource
    fun timeout(delayMs: Int, expired: () -> Unit): BackupFileResource
}

/** Release callbacks on every terminal path; abort a pending read on timeout/cancellation. */
internal fun interface BackupFileResource {
    fun close()
}

sealed interface BackupReadResult {
    data class Text(val value: String) : BackupReadResult
    data object Empty : BackupReadResult
    data object TooLarge : BackupReadResult
    data object InvalidUtf8 : BackupReadResult
    data object Failed : BackupReadResult
    data object TimedOut : BackupReadResult
}

fun interface BackupReadHandle {
    /** Silent cancellation: late file/timer callbacks cannot publish a result. */
    fun cancel()
}

/** Bounded, disposable adapter. Validation of JSON is deliberately left to BackupCodec. */
class BackupFileReader internal constructor(
    private val platform: BackupFilePlatform,
    private val timeoutMs: Int = 10_000,
) {
    constructor() : this(WindowBackupFilePlatform)

    private val pending = mutableSetOf<Read>()
    private var disposed = false

    fun read(file: Any, completed: (BackupReadResult) -> Unit): BackupReadHandle {
        if (disposed) {
            completed(BackupReadResult.Failed)
            return BackupReadHandle { }
        }
        val size = try { platform.byteSize(file) } catch (_: Throwable) { null }
        val limit = SaveBounds.MAX_JSON_BYTES + 4 * 1024
        if (size == null || size < 0) {
            completed(BackupReadResult.Failed)
            return BackupReadHandle { }
        }
        if (size == 0L || size > limit) {
            completed(if (size == 0L) BackupReadResult.Empty else BackupReadResult.TooLarge)
            return BackupReadHandle { }
        }
        val request = Read(completed, limit)
        pending.add(request)
        request.start(file)
        return BackupReadHandle { request.cancel() }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        pending.toList().forEach { it.cancel() }
    }

    private inner class Read(
        private val completed: (BackupReadResult) -> Unit,
        private val limit: Int,
    ) {
        private var finished = false
        private var reader: BackupFileResource? = null
        private var timer: BackupFileResource? = null

        fun start(file: Any) {
            try {
                val scheduled = platform.timeout(timeoutMs) { finish(BackupReadResult.TimedOut) }
                if (finished) scheduled.close() else timer = scheduled
                if (finished) return
                val resource = platform.read(file) { result ->
                    if (finished) return@read
                    val bytes = result.getOrNull()
                    val outcome = if (bytes == null) BackupReadResult.Failed else decode(bytes)
                    finish(outcome)
                }
                if (finished) resource.close() else reader = resource
            } catch (_: Throwable) {
                finish(BackupReadResult.Failed)
            }
        }

        private fun decode(bytes: ByteArray): BackupReadResult {
            if (bytes.isEmpty()) return BackupReadResult.Empty
            if (bytes.size > limit) return BackupReadResult.TooLarge
            val text = try { bytes.decodeToString(throwOnInvalidSequence = true) }
                catch (_: Throwable) { return BackupReadResult.InvalidUtf8 }
            // The browser's size hint is not authoritative. Check the decoded UTF-8 representation too.
            return if (text.encodeToByteArray().size > limit) BackupReadResult.TooLarge
                else BackupReadResult.Text(text)
        }

        fun cancel() = finish(null)

        private fun finish(result: BackupReadResult?) {
            if (finished) return
            finished = true
            pending.remove(this)
            try { timer?.close() } catch (_: Throwable) { /* still release the reader */ }
            try { reader?.close() } catch (_: Throwable) { /* best effort platform cleanup */ }
            timer = null
            reader = null
            if (result != null && !disposed) completed(result)
        }
    }
}

/** No DOM or timer APIs are touched until a browser read is requested. */
private object WindowBackupFilePlatform : BackupFilePlatform {
    override fun byteSize(file: Any): Long? {
        val size = file.asDynamic().size as? Double ?: return null
        if (!size.isFinite() || size < 0 || size > Long.MAX_VALUE.toDouble() || size % 1.0 != 0.0) return null
        return size.toLong()
    }

    override fun read(file: Any, completed: (Result<ByteArray>) -> Unit): BackupFileResource {
        val reader: dynamic = js("new FileReader()")
        reader.onload = {
            try {
                val buffer: dynamic = reader.result
                val array: dynamic = js("new Uint8Array(buffer)")
                val length = array.length as Int
                // Avoid allocating a giant Kotlin array even if the reported File.size was dishonest.
                if (length > SaveBounds.MAX_JSON_BYTES + 4 * 1024) {
                    completed(Result.success(ByteArray(SaveBounds.MAX_JSON_BYTES + 4 * 1024 + 1)))
                } else {
                    val bytes = ByteArray(length) { index -> (array[index] as Int).toByte() }
                    completed(Result.success(bytes))
                }
            } catch (error: Throwable) { completed(Result.failure(error)) }
        }
        reader.onerror = { completed(Result.failure(IllegalStateException("File read failed"))) }
        reader.onabort = { completed(Result.failure(IllegalStateException("File read aborted"))) }
        try { reader.readAsArrayBuffer(file) } catch (error: Throwable) {
            reader.onload = null
            reader.onerror = null
            reader.onabort = null
            throw error
        }
        return BackupFileResource {
            reader.onload = null
            reader.onerror = null
            reader.onabort = null
            if (reader.readyState == 1) reader.abort()
        }
    }

    override fun timeout(delayMs: Int, expired: () -> Unit): BackupFileResource {
        val window: dynamic = js("window")
        val handle: dynamic = window.setTimeout(expired, delayMs)
        return BackupFileResource { window.clearTimeout(handle) }
    }
}
