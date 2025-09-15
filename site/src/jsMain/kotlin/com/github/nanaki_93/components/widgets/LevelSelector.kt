package com.github.nanaki_93.components.widgets

import androidx.compose.runtime.*
import com.github.nanaki_93.components.styles.*
import com.github.nanaki_93.models.GameState
import com.github.nanaki_93.models.Level
import com.varabyte.kobweb.silk.components.forms.Button
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.toModifier

@Composable
fun LevelSelector(
    state: GameState,
    availableLevels: List<Level>,
    currentLevel: Level?,
    onLevelSelected: (Level) -> Unit = {}
) {
    if (state != GameState.LEVEL_SELECTION) return

    SpanText("Select your level:", LevelSelectorStyles.SelectorTitle.toModifier())
    CenterRow {
        for (level in availableLevels) {
            Button(
                onClick = { onLevelSelected(level) },
                modifier = LevelSelectorStyles.Button.toModifier().then(LevelSelectorStyles.getBackground(currentLevel == level))
            ) {
                SpanText("Lv${level.displayName}", CommonStyles.ButtonText.toModifier())
            }
        }
    }
}