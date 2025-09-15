package com.github.nanaki_93.components.widgets

import androidx.compose.runtime.*
import com.github.nanaki_93.components.styles.*
import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.models.GameState
import com.github.nanaki_93.models.GameStatisticsUi
import com.github.nanaki_93.models.Level
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.flex
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.toModifier

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
    Column(
        Modifier.flex(1).then(GameStatsStyles.Item.toModifier()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SpanText(label, GameStatsStyles.Label.toModifier())
        SpanText(value, GameStatsStyles.Value.toModifier())
    }
}