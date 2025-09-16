package com.github.nanaki_93.components.sections

import androidx.compose.runtime.Composable
import com.github.nanaki_93.components.widgets.ItemColumn
import com.github.nanaki_93.components.widgets.LabelText
import com.github.nanaki_93.components.widgets.SpacedRow
import com.github.nanaki_93.components.widgets.ValueText
import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.models.GameState
import com.github.nanaki_93.models.GameStatisticsUi
import com.github.nanaki_93.models.Level

@Composable
fun GameStats(state: GameState, gameMode: GameMode?, level: Level?, statsUi: GameStatisticsUi) {
    if (state == GameState.LOADING) return

    SpacedRow {
        StatItem("GameMode", gameMode?.displayName ?: "N/A")
        StatItem("Level", level?.displayName ?: "N/A")
        StatItem("Score", "${statsUi.score}")
    }

    SpacedRow {
        StatItem("Correct Answers", "${statsUi.correctAnswers}")
        StatItem("Total Attempts", "${statsUi.totalAnswered}")
        StatItem("Streak", "${statsUi.streak}")
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    ItemColumn {
        LabelText(label)
        ValueText(value)
    }
}