package com.github.nanaki_93.audio

import kotlin.test.*

class RecordingControllerTest {
    private class Fake : RecordingPort {
        var released = 0; var starts = 0
        lateinit var started: () -> Unit; lateinit var ready: () -> Unit; lateinit var failed: () -> Unit
        override fun start(started: () -> Unit, ready: () -> Unit, failed: () -> Unit) { starts++; this.started=started; this.ready=ready; this.failed=failed }
        override fun stop() { ready() }
        override fun play(ended: () -> Unit, failed: () -> Unit) { ended() }
        override fun release() { released++ }
    }
    @Test fun optInStopPlaybackAndDiscard() {
        val port=Fake(); val control=RecordingController(port)
        assertEquals(0,port.starts)
        control.record(); control.record(); assertEquals(1,port.starts)
        port.started(); assertEquals(RecordingState.RECORDING,control.state)
        control.stop(); assertEquals(RecordingState.READY,control.state)
        control.play(); assertEquals(RecordingState.READY,control.state)
        control.cancel(); assertEquals(RecordingState.IDLE,control.state); assertTrue(port.released>=2)
    }
    @Test fun permissionFailureAndLateCallbacksCannotReviveCancelledRecording() {
        val port=Fake(); val control=RecordingController(port)
        control.record(); control.cancel(); port.started(); port.ready(); port.failed()
        assertEquals(RecordingState.IDLE,control.state)
        control.record(); port.failed(); assertEquals(RecordingState.ERROR,control.state)
        assertFailsWith<IllegalArgumentException> { AudioSource.LocalRecording("https://upload.test/audio") }
    }
}
