package com.github.nanaki_93.audio

import androidx.compose.runtime.*
import com.github.nanaki_93.content.AudioAsset
import com.github.nanaki_93.components.widgets.*
import org.jetbrains.compose.web.dom.*

@Composable
fun ListeningControls(transcript: String, recording: AudioAsset? = null) {
    var speed by remember(transcript) { mutableStateOf(1.0) }
    var shadow by remember(transcript) { mutableStateOf(false) }
    var state by remember(transcript) { mutableStateOf(AudioState.IDLE) }
    val identity = remember(transcript) { Any() }
    val active = browserAudioOwner === identity
    DisposableEffect(transcript) {
        onDispose { if (browserAudioOwner === identity) { browserAudio.stop(); browserAudio.changed = {}; browserAudioOwner = null } }
    }
    Section {
        P { Text(if (recording == null) "No reviewed recording. Optional Japanese device voice is synthetic, device-dependent, and may require a connection. Text is always available."
            else "Reviewed recording available. Transcript stays visible; audio is optional.") }
        P(attrs = { attr("lang", "ja") }) { Text(transcript) }
        for (rate in listOf(0.75, 1.0, 1.25)) SecondaryButton("${rate}×${if (speed == rate) " selected" else ""}", onClick = { speed = rate })
        SecondaryButton(if (shadow) "Shadowing on" else "Enable shadowing", onClick = { shadow = !shadow })
        PrimaryButton(if (recording == null) "Play Japanese device voice" else "Play recording", onClick = {
            browserAudio.stop()
            browserAudio.changed = { state = browserAudio.state }
            browserAudioOwner = identity
            browserAudio.play(recording?.let { AudioSource.Recording(it.path, transcript) } ?: AudioSource.Synthetic(transcript), speed, shadow)
        })
        if (active) {
            if (state == AudioState.PLAYING) SecondaryButton("Pause", onClick = browserAudio::pause)
            if (state == AudioState.PAUSED) SecondaryButton("Resume", onClick = browserAudio::resume)
            SecondaryButton("Repeat", onClick = browserAudio::repeat)
            SecondaryButton("Stop audio", onClick = browserAudio::stop)
            P(attrs = { attr("role", "status") }) { Text(when (state) {
                AudioState.IDLE -> "Audio stopped."
                AudioState.LOADING -> "Loading audio; you can keep reading or stop."
                AudioState.PLAYING -> "Listen to the phrase."
                AudioState.PAUSED -> "Audio paused."
                AudioState.YOUR_TURN -> "Your turn: say the phrase aloud. Select Repeat when ready to compare."
                AudioState.ENDED -> "Playback complete."
                AudioState.UNAVAILABLE -> "A Japanese device voice is unavailable. Continue with the transcript, or try again after voices load."
                AudioState.ERROR -> "Playback unavailable or blocked. Retry, or continue with the transcript."
            }) }
        }
    }
}
