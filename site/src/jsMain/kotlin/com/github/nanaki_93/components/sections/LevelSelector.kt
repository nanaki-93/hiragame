package com.github.nanaki_93.components.sections

import androidx.compose.runtime.*
import com.github.nanaki_93.components.widgets.ActionButton
import com.github.nanaki_93.components.widgets.CenterRow
import com.github.nanaki_93.components.widgets.TitleText
import com.github.nanaki_93.models.GameState
import com.github.nanaki_93.models.Level

@Composable
fun LevelSelector(
    state: GameState,
    availableLevels: List<Level>,
    onLevelSelected: (Level) -> Unit = {}
) {
    if (state != GameState.LEVEL_SELECTION) return

    TitleText("Select your level:")
    CenterRow {
        for (level in availableLevels) {
            ActionButton(
                text = "Lv-${level.displayName}",
                onClick = { onLevelSelected(level) },
            )
        }
    }
}