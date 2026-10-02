package com.github.nanaki_93.ai

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object BrowserModelTransport : LocalModelTransport {
    override suspend fun request(endpoint: String, path: String, body: String?): String = suspendCancellableCoroutine { continuation ->
        require(path == "/api/tags" && body == null || path == "/api/chat" && body != null)
        val url = validateLocalEndpoint(endpoint) + path
        val controller = js("new AbortController()")
        val options = js("({})")
        options.signal = controller.signal; options.credentials = "omit"; options.redirect = "error"
        options.method = if (body == null) "GET" else "POST"
        if (body != null) { options.body = body; options.headers = js("({'Content-Type':'application/json'})") }
        continuation.invokeOnCancellation { controller.abort() }
        fun fail() { if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Local model unavailable")) }
        try {
            val browser = js("globalThis")
            browser.fetch(url, options).then({ response: dynamic ->
                if (!response.ok) fail()
                else response.text().then({ value: dynamic ->
                    val text = value as String
                    if (text.length > 512_000) fail()
                    else if (continuation.isActive) continuation.resume(text)
                }, { _: dynamic -> fail() })
            }, { _: dynamic -> fail() })
        } catch (_: Throwable) { fail() }
    }
}
