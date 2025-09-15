package com.github.nanaki_93.components.styles

import com.varabyte.kobweb.compose.css.AnimationIterationCount
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.animation
import com.varabyte.kobweb.compose.ui.modifiers.borderRadius
import com.varabyte.kobweb.compose.ui.modifiers.display
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxWidth
import com.varabyte.kobweb.compose.ui.modifiers.rotate
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.animation.Keyframes
import com.varabyte.kobweb.silk.style.base
import org.jetbrains.compose.web.css.AnimationTimingFunction
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.deg
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.s

object SpinnerStyles{

    // Spinner Component Styles
    val BaseStyle = CssStyle.base {
        Modifier
            .borderRadius(50.percent)
            .animation(
                SKeyframes.toAnimation(
                    duration = 1.s,
                    iterationCount = AnimationIterationCount.Infinite,
                    timingFunction = AnimationTimingFunction.Linear
                )
            )
    }

    val SKeyframes = Keyframes {
        0.percent {
            Modifier.rotate(0.deg)
        }
        100.percent {
            Modifier.rotate(360.deg)
        }
    }

    val CenteredContainer = CssStyle.base {
        Modifier
            .fillMaxWidth()
            .display(DisplayStyle.Flex)
    }
}