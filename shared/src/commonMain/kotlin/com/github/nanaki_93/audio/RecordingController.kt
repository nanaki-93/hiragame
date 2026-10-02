package com.github.nanaki_93.audio

enum class RecordingState { IDLE, REQUESTING, RECORDING, READY, PLAYING, ERROR }
interface RecordingPort {
    fun start(started: () -> Unit, ready: () -> Unit, failed: () -> Unit)
    fun stop()
    fun play(ended: () -> Unit, failed: () -> Unit)
    /** Releases tracks, transient blob URLs and pending callbacks, including permission requests. */
    fun release()
}
class RecordingController(private val port: RecordingPort) {
    var state = RecordingState.IDLE; private set
    var changed: () -> Unit = {}
    private var generation = 0
    private fun set(value: RecordingState) { state = value; changed() }
    fun record() {
        if (state == RecordingState.REQUESTING || state == RecordingState.RECORDING) return
        cancel(); val token = generation; set(RecordingState.REQUESTING)
        try { port.start(
            { if (token == generation) set(RecordingState.RECORDING) },
            { if (token == generation) set(RecordingState.READY) },
            { if (token == generation) { port.release(); set(RecordingState.ERROR) } })
        } catch (_: Exception) { cancel(); set(RecordingState.ERROR) }
    }
    fun stop() { if (state == RecordingState.RECORDING) try { port.stop() } catch (_: Exception) { cancel(); set(RecordingState.ERROR) } }
    fun play() {
        if (state != RecordingState.READY) return
        val token = generation; set(RecordingState.PLAYING)
        try { port.play({ if (token == generation) set(RecordingState.READY) },
            { if (token == generation) set(RecordingState.ERROR) }) }
        catch (_: Exception) { set(RecordingState.ERROR) }
    }
    fun cancel() { generation++; try { port.release() } catch (_: Exception) { }; set(RecordingState.IDLE) }
}
