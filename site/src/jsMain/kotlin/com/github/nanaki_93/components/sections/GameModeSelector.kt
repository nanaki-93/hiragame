package com.github.nanaki_93.components.sections

import androidx.compose.runtime.*
import com.github.nanaki_93.components.styles.*
import com.github.nanaki_93.components.widgets.ActionButton
import com.github.nanaki_93.components.widgets.CenterRow
import com.github.nanaki_93.components.widgets.TitleText
import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.models.GameState
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.toModifier

@Composable
fun GameModeSelector(
    state: GameState,
    onModeSelected: (GameMode) -> Unit = {}
) {
    //todo component for the loading message
    if (state == GameState.LOADING) return SpanText(
        "Game mode selection will appear shortly...",
        GameModeStyles.LoadingText.toModifier()
    )
    if (state != GameState.MODE_SELECTION) return

    TitleText("Select a game mode to continue:")
    CenterRow {
        GameMode.entries.forEach { mode ->
            ActionButton(text = mode.displayName, onClick = { onModeSelected(mode) })
        }
    }
}