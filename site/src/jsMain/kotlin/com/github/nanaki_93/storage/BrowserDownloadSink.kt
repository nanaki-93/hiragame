package com.github.nanaki_93.storage

/** Platform seam keeps the native Blob, URL, and DOM APIs out of Node imports. */
internal interface BrowserDownloadPlatform {
    fun createUrl(text: String, contentType: String): String
    fun trigger(url: String, filename: String)
    fun release(url: String)
}

class BrowserDownloadSink internal constructor(private val platform: BrowserDownloadPlatform) : ProgressDownloadSink {
    constructor() : this(WindowDownloadPlatform)

    override fun download(filename: String, contentType: String, text: String) {
        val url = platform.createUrl(text, contentType)
        try {
            platform.trigger(url, filename)
        } finally {
            platform.release(url)
        }
    }
}

private object WindowDownloadPlatform : BrowserDownloadPlatform {
    override fun createUrl(text: String, contentType: String): String {
        val blob: dynamic = js("new Blob([text], { type: contentType })")
        val urlApi: dynamic = js("URL")
        return urlApi.createObjectURL(blob) as String
    }

    override fun trigger(url: String, filename: String) {
        val document: dynamic = js("window.document")
        val anchor: dynamic = document.createElement("a")
        anchor.href = url
        anchor.download = filename
        document.body.appendChild(anchor)
        try {
            anchor.click()
        } finally {
            anchor.remove()
        }
    }

    override fun release(url: String) {
        val urlApi: dynamic = js("URL")
        urlApi.revokeObjectURL(url)
    }
}
