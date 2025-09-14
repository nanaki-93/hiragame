
package com.github.nanaki_93.components.widgets

import androidx.compose.runtime.*
import com.github.nanaki_93.components.styles.*
import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.models.GameState
import com.varabyte.kobweb.silk.components.forms.Button
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.toModifier

@Composable
fun GameModeSelector(
    state: GameState,
    currentMode: GameMode? = null,
    onModeSelected: (GameMode) -> Unit = {}
) {
    if (state == GameState.LOADING) return SpanText(
        "Game mode selection will appear shortly...",
        GameModeLoadingTextStyle.toModifier()
    )
    if (state != GameState.MODE_SELECTION) return

    SpanText("Select a game mode to continue:", GameModeSelectorTitleStyle.toModifier())
    CenterRow {
        GameMode.entries.forEach { mode ->
            Button(
                onClick = { onModeSelected(mode) },
                modifier = GameModeButtonStyle.toModifier()
                    .then(getGameModeButtonBackground(currentMode == mode, currentMode))
            ) {
                SpanText(
                    mode.displayName,
                    LevelButtonTextStyle.toModifier()
                )
            }
        }
    }
}