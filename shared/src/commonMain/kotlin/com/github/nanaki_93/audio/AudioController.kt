package com.github.nanaki_93.audio

sealed interface AudioSource {
    val transcript: String
    data class Recording(val path: String, override val transcript: String) : AudioSource {
        init { require(Regex("audio/[A-Za-z0-9_/-]+\\.(mp3|ogg|wav|m4a)").matches(path) && ".." !in path) }
    }
    data class Synthetic(override val transcript: String) : AudioSource
}
enum class AudioState { IDLE, LOADING, PLAYING, PAUSED, YOUR_TURN, ENDED, UNAVAILABLE, ERROR }
interface AudioPort {
    fun start(source: AudioSource, speed: Double, started: () -> Unit, ended: () -> Unit, failed: (Boolean) -> Unit)
    fun pause()
    fun resume()
    fun stop()
}

/** Exactly one playback; callbacks from a stopped recording never mutate a newer one. */
class AudioController(private val port: AudioPort) {
    var state = AudioState.IDLE; private set
    var changed: () -> Unit = {}
    private var generation = 0
    private var source: AudioSource? = null
    private var speed = 1.0
    private var shadow = false
    fun play(source: AudioSource, speed: Double = 1.0, shadow: Boolean = false) {
        require(speed in listOf(0.75, 1.0, 1.25))
        stop()
        this.source = source; this.speed = speed; this.shadow = shadow
        val token = generation
        set(AudioState.LOADING)
        try {
            port.start(source, speed,
                { if (token == generation) set(AudioState.PLAYING) },
                { if (token == generation) set(if (shadow) AudioState.YOUR_TURN else AudioState.ENDED) },
                { unavailable -> if (token == generation) set(if (unavailable) AudioState.UNAVAILABLE else AudioState.ERROR) })
        } catch (_: Exception) { if (token == generation) set(AudioState.ERROR) }
    }
    fun pause() {
        if (state != AudioState.PLAYING) return
        try { port.pause(); set(AudioState.PAUSED) } catch (_: Exception) { stop(); set(AudioState.ERROR) }
    }
    fun resume() {
        if (state != AudioState.PAUSED) return
        try { port.resume(); set(AudioState.PLAYING) } catch (_: Exception) { stop(); set(AudioState.ERROR) }
    }
    fun repeat() { source?.let { play(it, speed, shadow) } }
    fun stop() { generation++; try { port.stop() } catch (_: Exception) { /* Text remains available. */ }; set(AudioState.IDLE) }
    private fun set(value: AudioState) { state = value; changed() }
}
