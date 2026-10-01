package com.github.nanaki_93.content

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Only the fixed, hosted static content tree is addressable by this source. */
private const val CONTENT_ROOT = "/hiragame/content/"

internal data class ContentHttpResponse(val status: Int, val ok: Boolean, val body: String)

internal interface ContentHttpTransport {
    suspend fun get(url: String): ContentHttpResponse
}

class ContentHttpException(val status: Int) : IllegalStateException("bundled content HTTP $status")

/** Resolves already validated catalog paths once; also rejects unsafe direct calls. */
class BrowserContentTextSource internal constructor(private val transport: ContentHttpTransport) : ContentTextSource {
    constructor() : this(BrowserFetchTransport)

    override suspend fun readText(relativePath: String): String {
        require(relativePath == "catalog.json" || isSafeDocumentPath(relativePath)) {
            "unsafe bundled content path"
        }
        val response = transport.get(CONTENT_ROOT + relativePath)
        if (!response.ok) throw ContentHttpException(response.status)
        return response.body
    }
}

/** Browser globals are accessed only when get is invoked, never during Node test initialization. */
private object BrowserFetchTransport : ContentHttpTransport {
    override suspend fun get(url: String): ContentHttpResponse = suspendCancellableCoroutine { continuation ->
        val browser: dynamic = js("globalThis")
        val controller: dynamic = if (js("typeof globalThis.AbortController !== 'undefined'") as Boolean)
            js("new globalThis.AbortController()") else null
        val options: dynamic = js("({})")
        if (controller != null) options.signal = controller.signal
        options.credentials = "omit"
        options.mode = "same-origin"
        options.redirect = "error"
        continuation.invokeOnCancellation { if (controller != null) controller.abort() }
        try {
            val request: dynamic = browser.fetch(url, options)
            request.then({ response: dynamic ->
                if (!response.ok) {
                    if (continuation.isActive) continuation.resume(
                        ContentHttpResponse((response.status as Number).toInt(), false, ""))
                } else {
                    response.text().then({ body: dynamic ->
                        if (continuation.isActive) continuation.resume(
                            ContentHttpResponse((response.status as Number).toInt(), true, body as String))
                    }, { _: dynamic ->
                        if (continuation.isActive) continuation.resumeWithException(
                            IllegalStateException("bundled content response failed"))
                    })
                }
            }, { _: dynamic ->
                if (continuation.isActive) continuation.resumeWithException(
                    IllegalStateException("bundled content request failed"))
            })
        } catch (e: Throwable) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }
    }
}
