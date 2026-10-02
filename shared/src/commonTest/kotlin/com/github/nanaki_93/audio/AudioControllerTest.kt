package com.github.nanaki_93.audio

import kotlin.test.*

class AudioControllerTest {
    private class Fake : AudioPort {
        val events = mutableListOf<String>()
        lateinit var started: () -> Unit; lateinit var ended: () -> Unit; lateinit var failed: (Boolean) -> Unit
        override fun start(source: AudioSource, speed: Double, started: () -> Unit, ended: () -> Unit, failed: (Boolean) -> Unit) {
            events += "play:$speed"; this.started = started; this.ended = ended; this.failed = failed
        }
        override fun pause() { events += "pause" }
        override fun resume() { events += "resume" }
        override fun stop() { events += "stop" }
    }
    @Test fun stopsBeforeStartAndIgnoresOldCallbacks() {
        val fake = Fake(); val controller = AudioController(fake)
        controller.play(AudioSource.Synthetic("はい")); val oldEnd = fake.ended
        controller.play(AudioSource.Recording("audio/yes.mp3", "はい"), 0.75, shadow = true)
        assertEquals(listOf("stop", "play:${1.0}", "stop", "play:0.75"), fake.events)
        oldEnd(); assertEquals(AudioState.LOADING, controller.state)
        fake.started(); controller.pause(); assertEquals(AudioState.PAUSED, controller.state)
        controller.resume(); assertEquals(AudioState.PLAYING, controller.state)
        fake.ended(); assertEquals(AudioState.YOUR_TURN, controller.state)
        controller.repeat(); assertEquals(AudioState.LOADING, controller.state)
        controller.stop(); fake.started(); assertEquals(AudioState.IDLE, controller.state)
    }
    @Test fun failuresPreserveTextOnlyPathAndPathsAreOwned() {
        val fake = Fake(); val controller = AudioController(fake)
        controller.play(AudioSource.Synthetic("はい")); fake.failed(true); assertEquals(AudioState.UNAVAILABLE, controller.state)
        controller.repeat(); fake.failed(false); assertEquals(AudioState.ERROR, controller.state)
        assertFailsWith<IllegalArgumentException> { AudioSource.Recording("https://third.party/audio.mp3", "はい") }
        assertFailsWith<IllegalArgumentException> { AudioSource.Recording("audio/../../secret.mp3", "はい") }
    }
}
