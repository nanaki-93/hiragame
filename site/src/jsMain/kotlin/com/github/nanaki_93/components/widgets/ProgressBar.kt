package com.github.nanaki_93.components.widgets

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.styleModifier
import com.github.nanaki_93.models.GameStateReq
import org.jetbrains.compose.web.css.*

@Composable
fun ProgressBar(gameStateReq: GameStateReq) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.px)
            .background(rgba(255, 255, 255, 0.2))
            .borderRadius(5.px)
    ) {
        val progress = ((gameStateReq.correctAnswers % 10) * 10)
        Box(
            Modifier
                .width(progress.percent)
                .height(100.percent)
                .styleModifier {
                    background("linear-gradient(90deg, rgba(76, 175, 80, 1.0) 0%, rgba(139, 195, 74, 1.0) 100%)")
                }
                .borderRadius(5.px)
        )
    }
}