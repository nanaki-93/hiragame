package com.github.nanaki_93.audio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** Browser objects are created only in response to explicit playback controls. */
class BrowserAudioPort : AudioPort {
    private var media: dynamic = null
    private var speech: dynamic = null
    private var utterance: dynamic = null
    private var failure: ((Boolean) -> Unit)? = null
    override fun start(source: AudioSource, speed: Double, started: () -> Unit, ended: () -> Unit, failed: (Boolean) -> Unit) {
        failure = failed
        when (source) {
            is AudioSource.Recording -> {
                val audio = js("new Audio()")
                media = audio
                audio.preload = "none"
                audio.src = "/hiragame/content/${source.path}"
                audio.playbackRate = speed
                audio.onplaying = { started() }
                audio.onended = { ended() }
                audio.onerror = { failed(false) }
                audio.play().catch { _: dynamic -> failed(false) }
            }
            is AudioSource.Synthetic -> {
                val synth = js("window.speechSynthesis")
                if (synth == null) { failed(true); return }
                val voices = synth.getVoices() as Array<dynamic>
                val voice = voices.firstOrNull { (it.lang as? String)?.startsWith("ja", ignoreCase = true) == true }
                if (voice == null) { failed(true); return }
                val text = js("new SpeechSynthesisUtterance()")
                speech = synth; utterance = text
                text.text = source.transcript; text.lang = "ja-JP"; text.voice = voice; text.rate = speed
                text.onstart = { started() }; text.onend = { ended() }; text.onerror = { failed(false) }
                synth.speak(text)
            }
        }
    }
    override fun pause() { media?.pause(); speech?.pause() }
    override fun resume() { media?.play()?.catch { _: dynamic -> failure?.invoke(false) }; speech?.resume() }
    override fun stop() {
        if (media != null) { media.onplaying = null; media.onended = null; media.onerror = null; media.pause(); media.removeAttribute("src"); media.load() }
        if (utterance != null) { utterance.onstart = null; utterance.onend = null; utterance.onerror = null }
        speech?.cancel(); media = null; speech = null; utterance = null; failure = null
    }
}

/** Shared across all turn/phrase controls, so one activity always stops another. */
internal val browserAudio by lazy { AudioController(BrowserAudioPort()) }

internal var browserAudioOwner by androidx.compose.runtime.mutableStateOf<Any?>(null)
