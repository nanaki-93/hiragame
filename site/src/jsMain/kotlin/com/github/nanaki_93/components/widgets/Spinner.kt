package com.github.nanaki_93.components.widgets

import androidx.compose.runtime.*
import com.github.nanaki_93.models.GameState
import com.varabyte.kobweb.compose.css.*
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.animation.Keyframes
import com.varabyte.kobweb.silk.style.animation.toAnimation
import com.varabyte.kobweb.silk.style.base

import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div

// Spinner styles
val SpinnerStyle = CssStyle.base {
    Modifier
        .width(40.px)
        .height(40.px)
        .border(4.px, LineStyle.Solid, rgba(0, 0, 0, 0.1))
        .borderTop(4.px, LineStyle.Solid, rgb(59, 130, 246)) // Blue color
        .borderRadius(50.percent)
        .animation(
            SpinnerKeyframes.toAnimation(
                duration = 1.s,
                iterationCount = AnimationIterationCount.Infinite,
                timingFunction = AnimationTimingFunction.Linear
            )
        )
}

val SpinnerKeyframes = Keyframes {
    0.percent {
        Modifier.rotate(0.deg)
    }
    100.percent {
        Modifier.rotate(360.deg)
    }
}

// Size variants
enum class SpinnerSize(val size: CSSLengthValue, val borderWidth: CSSLengthValue) {
    Small(24.px, 3.px),
    Medium(40.px, 4.px),
    Large(56.px, 5.px),
    ExtraLarge(72.px, 6.px)
}

// Color variants
enum class SpinnerColor(val color: CSSColorValue) {
    Blue(rgb(59, 130, 246)),
    Green(rgb(34, 197, 94)),
    Red(rgb(239, 68, 68)),
    Yellow(rgb(245, 158, 11)),
    Purple(rgb(168, 85, 247)),
    Gray(rgb(107, 114, 128)),
    White(rgb(255, 255, 255))
}
@Composable
fun Spinner(
    isVisible: Boolean = true,
    modifier: Modifier = Modifier,
    size: SpinnerSize = SpinnerSize.Medium,
    color: SpinnerColor = SpinnerColor.Blue,
    centered: Boolean = false
) {
    if (!isVisible) return
    val spinnerModifier = modifier
        .width(size.size)
        .height(size.size)
        .border(size.borderWidth, LineStyle.Solid, rgba(0, 0, 0, 0.1))
        .borderTop(size.borderWidth, LineStyle.Solid, color.color)
        .borderRadius(50.percent)
        .animation(
            SpinnerKeyframes.toAnimation(
                duration = 1.s,
                iterationCount = AnimationIterationCount.Infinite,
                timingFunction = AnimationTimingFunction.Linear
            )
        )

    if (centered) {
        Box(
            modifier = Modifier.fillMaxWidth().display(DisplayStyle.Flex),
            contentAlignment = Alignment.Center
        ) {
            Div(attrs = spinnerModifier.toAttrs())
        }
    } else {
        Div(attrs = spinnerModifier.toAttrs())
    }
}