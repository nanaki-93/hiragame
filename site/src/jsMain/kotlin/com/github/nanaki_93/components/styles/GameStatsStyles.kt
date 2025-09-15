package com.github.nanaki_93.components.styles

import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.background
import com.varabyte.kobweb.compose.ui.modifiers.borderRadius
import com.varabyte.kobweb.compose.ui.modifiers.flexGrow
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.margin
import com.varabyte.kobweb.compose.ui.modifiers.minWidth
import com.varabyte.kobweb.compose.ui.modifiers.opacity
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.base
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.css.px

object GameStatsStyles {
    val Item = CssStyle.base {
        Modifier
            .background(ColorUtil.WhiteOp2)
            .padding(0.5.cssRem, 1.cssRem)
            .borderRadius(10.px)
            .flexGrow(1)
            .minWidth(100.px)
            .margin(0.25.cssRem)
    }

    val Label = CssStyle.base {
        Modifier
            .fontSize(0.8.cssRem)
            .opacity(0.8)
    }

    val Value = CssStyle.base {
        Modifier
            .fontSize(1.2.cssRem)
            .fontWeight(FontWeight.Bold)
    }
}