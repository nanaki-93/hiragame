package com.github.nanaki_93.components.widgets

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.toModifier
import com.github.nanaki_93.StatItemStyle
import com.github.nanaki_93.models.GameStateReq
import com.varabyte.kobweb.compose.css.FontWeight
import org.jetbrains.compose.web.css.*

@Composable
fun GameStats(gameStateReq: GameStateReq) {
    Row(
        Modifier.fillMaxWidth().flexWrap(FlexWrap.Wrap),
        horizontalArrangement = Arrangement.spacedBy(0.5.cssRem)
    ) {
        StatItem("Score", "${gameStateReq.score}")
        StatItem("Streak", "${gameStateReq.streak}")
        StatItem("Level", "${1}")

        val accuracy = if (gameStateReq.totalAnswered > 0) {
            (gameStateReq.correctAnswers * 100 / gameStateReq.totalAnswered)
        } else 0
        StatItem("Accuracy", "$accuracy%")
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(
        StatItemStyle.toModifier(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SpanText(label, Modifier.fontSize(0.8.cssRem).opacity(0.8))
        SpanText(value, Modifier.fontSize(1.2.cssRem).fontWeight(FontWeight.Bold))
    }
}