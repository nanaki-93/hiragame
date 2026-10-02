package com.github.nanaki_93.audio

import androidx.compose.runtime.*
import com.github.nanaki_93.components.widgets.*
import org.jetbrains.compose.web.dom.*

@Composable
fun RecordingControls(example: String?) {
    val controller = remember { RecordingController(BrowserRecordingPort()) }
    var state by remember { mutableStateOf(controller.state) }
    DisposableEffect(controller) {
        controller.changed = { state = controller.state }
        onDispose { controller.cancel(); controller.changed = {} }
    }
    Section {
        H3 { Text("Optional record and compare") }
        P { Text("Record requests microphone permission only when selected. Up to 30 seconds; held in memory, never uploaded or saved in progress. Leaving this activity discards the recording. No automatic pronunciation grading.") }
        example?.let { ListeningControls(it) }
        P(attrs = { attr("role", "status") }) { Text(when (state) {
            RecordingState.IDLE -> "Microphone off. You can also speak without recording or type a response."
            RecordingState.REQUESTING -> "Waiting for microphone permission. Cancel to keep practising with text."
            RecordingState.RECORDING -> "Recording your response… Stop when ready (30-second maximum)."
            RecordingState.READY -> "Recording ready. Compare your voice with the example voluntarily."
            RecordingState.PLAYING -> "Playing your recording."
            RecordingState.ERROR -> "Recording or playback is unavailable, denied or exceeded its limit. Continue with text, or retry."
        }) }
        if (state !in listOf(RecordingState.RECORDING, RecordingState.REQUESTING, RecordingState.PLAYING)) PrimaryButton("Record with microphone", onClick = controller::record)
        if (state == RecordingState.RECORDING) PrimaryButton("Stop recording", onClick = controller::stop)
        if (state == RecordingState.READY) PrimaryButton("Play my recording", onClick = controller::play)
        if (state != RecordingState.IDLE) SecondaryButton("Cancel / discard recording", onClick = controller::cancel)
    }
}
