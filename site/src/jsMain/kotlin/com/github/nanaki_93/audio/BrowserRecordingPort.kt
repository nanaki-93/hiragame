package com.github.nanaki_93.audio

import kotlinx.browser.window

/** Opt-in, memory-only MediaRecorder. No network/upload or progress-store access. */
class BrowserRecordingPort : RecordingPort {
    private var generation = 0
    private var stream: dynamic = null
    private var recorder: dynamic = null
    private var parts: dynamic = js("[]")
    private var objectUrl: String? = null
    private var timer: Int? = null
    override fun start(started: () -> Unit, ready: () -> Unit, failed: () -> Unit) {
        release(); browserAudio.stop()
        val token = generation
        val navigator = window.navigator.asDynamic()
        if (navigator.mediaDevices == null || js("typeof MediaRecorder === 'undefined'") as Boolean) { failed(); return }
        navigator.mediaDevices.getUserMedia(js("({audio:true})")).then({ media: dynamic ->
            if (token != generation) stopTracks(media)
            else try {
                stream = media
                val factory = js("(function(stream) { return new MediaRecorder(stream); })")
                val current = factory(media); recorder = current; parts = js("[]")
                var bytes = 0
                current.ondataavailable = { event: dynamic ->
                    if (token == generation) {
                        bytes += (event.data.size as Number).toInt()
                        if (bytes > 5 * 1024 * 1024) { release(); failed() }
                        else if ((event.data.size as Number).toInt() > 0) parts.push(event.data)
                    }
                }
                current.onerror = { if (token == generation) { release(); failed() } }
                current.onstop = {
                    if (token == generation) {
                        timer?.let(window::clearTimeout); timer = null
                        stopTracks(media); stream = null
                        if (bytes == 0) failed()
                        else {
                            val makeBlob = js("(function(parts, type) { return new Blob(parts, {type:type}); })")
                            val blob = makeBlob(parts, current.mimeType)
                            val url = js("URL")
                            objectUrl = url.createObjectURL(blob) as String
                            parts = js("[]"); ready()
                        }
                    }
                }
                current.start(250); started()
                timer = window.setTimeout({ if (token == generation) stop() }, 30_000)
            } catch (_: Throwable) { release(); failed() }
        }, { _: dynamic -> if (token == generation) failed() })
    }
    private fun stopTracks(media: dynamic) {
        val tracks = media.getTracks() as Array<dynamic>
        for (track in tracks) track.stop()
    }
    override fun stop() { if (recorder != null && recorder.state == "recording") recorder.stop() }
    override fun play(ended: () -> Unit, failed: () -> Unit) {
        val url = objectUrl ?: return failed()
        browserAudio.stop(); browserAudioOwner = this
        var playbackStarted = false
        browserAudio.changed = {
            when (browserAudio.state) {
                AudioState.PLAYING -> playbackStarted = true
                AudioState.IDLE -> if (playbackStarted) ended()
                AudioState.ENDED -> ended()
                AudioState.ERROR, AudioState.UNAVAILABLE -> failed()
                else -> Unit
            }
        }
        browserAudio.play(AudioSource.LocalRecording(url))
    }
    override fun release() {
        generation++
        timer?.let(window::clearTimeout); timer = null
        if (recorder != null) {
            recorder.ondataavailable = null; recorder.onstop = null; recorder.onerror = null
            if (recorder.state == "recording") recorder.stop()
        }
        if (stream != null) stopTracks(stream)
        if (browserAudioOwner === this) { browserAudio.stop(); browserAudio.changed = {}; browserAudioOwner = null }
        objectUrl?.let { value -> val url = js("URL"); url.revokeObjectURL(value) }
        objectUrl = null; stream = null; recorder = null; parts = js("[]")
    }
}
