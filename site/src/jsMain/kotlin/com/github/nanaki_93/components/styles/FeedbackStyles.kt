package com.github.nanaki_93.components.styles

import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.margin
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.base
import org.jetbrains.compose.web.css.CSSColorValue
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba

object FeedbackStyles {
    val Title = CssStyle.base {
        Modifier
            .fontSize(1.2.cssRem)
            .fontWeight(FontWeight.Bold)
    }

    val Message = CssStyle.base {
        Modifier
            .fontSize(1.1.cssRem)
            .fontWeight(FontWeight.Bold)
            .margin(1.cssRem, 0.px)
    }

    // Helper function for feedback colors
    fun getFeedbackColor(isCorrect: Boolean?): CSSColorValue = when (isCorrect) {
        true -> rgba(76, 175, 80, 1.0)
        false -> rgba(244, 67, 54, 1.0)
        null -> Colors.White
    }
}