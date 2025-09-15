package com.github.nanaki_93.components.widgets

import androidx.compose.runtime.Composable
import com.github.nanaki_93.components.styles.FeedbackStyles
import com.github.nanaki_93.models.GameState
import com.github.nanaki_93.models.GameStateUi
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.toModifier

@Composable
fun Feedback(state: GameState, gameStateUi: GameStateUi) {
    if (state != GameState.SHOWING_FEEDBACK) return

    CenterRow {
        SpanText("Feedback:", FeedbackStyles.Title.toModifier())
    }
    CenterRow {
        Spinner(isVisible = state == GameState.SHOWING_FEEDBACK)
    }
    CenterRow {
        SpanText(
            gameStateUi.feedback,
            FeedbackStyles.Message.toModifier().color(FeedbackStyles.getFeedbackColor(gameStateUi.isCorrect))
        )
    }
}