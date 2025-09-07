package com.github.nanaki_93.components.widgets

import androidx.compose.runtime.Composable
import com.github.nanaki_93.models.GameState
import com.github.nanaki_93.models.GameStateUi
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.silk.components.text.SpanText
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba


@Composable
fun Feedback(state: GameState, gameStateUi: GameStateUi) {
    if (state != GameState.SHOWING_FEEDBACK) return
    Row(
        modifier = Modifier.fillMaxWidth().gap(0.5.cssRem),
        horizontalArrangement = Arrangement.Center
    ) {
        SpanText("Feedback:", Modifier.fontSize(1.2.cssRem).fontWeight(FontWeight.Bold))
    }
    Row(
        modifier = Modifier.fillMaxWidth().gap(0.5.cssRem),
        horizontalArrangement = Arrangement.Center
    ) {
        Spinner(isVisible = state == GameState.SHOWING_FEEDBACK)
    }
    Row(
        modifier = Modifier.fillMaxWidth().gap(0.5.cssRem),
        horizontalArrangement = Arrangement.Center
    ) {
        SpanText(
            gameStateUi.feedback,
            Modifier
                .fontSize(1.1.cssRem)
                .fontWeight(FontWeight.Bold)
                .color(
                    when (gameStateUi.isCorrect) {
                        true -> rgba(76, 175, 80, 1.0)
                        false -> rgba(244, 67, 54, 1.0)
                        null -> Colors.White
                    }
                )
                .margin(1.cssRem, 0.px)
        )
    }

}