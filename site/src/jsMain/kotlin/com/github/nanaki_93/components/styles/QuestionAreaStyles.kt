package com.github.nanaki_93.components.styles

import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.TextAlign
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.background
import com.varabyte.kobweb.compose.ui.modifiers.border
import com.varabyte.kobweb.compose.ui.modifiers.borderRadius
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxWidth
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.margin
import com.varabyte.kobweb.compose.ui.modifiers.minHeight
import com.varabyte.kobweb.compose.ui.modifiers.opacity
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.textAlign
import com.varabyte.kobweb.compose.ui.modifiers.textShadow
import com.varabyte.kobweb.compose.ui.modifiers.width
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.base
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba

object QuestionAreaStyles {

    val Input = CssStyle.base {
        Modifier
            .background(ColorUtil.WhiteOp2)
            .border(2.px, LineStyle.Solid, ColorUtil.WhiteOp3)
            .borderRadius(10.px)
            .padding(0.8.cssRem)
            .fontSize(1.2.cssRem)
            .color(Colors.White)
            .textAlign(TextAlign.Center)
            .width(100.percent)
            .margin(1.cssRem, 0.px)
    }
    val AreaContainer = CssStyle.base {
        Modifier
            .fillMaxWidth()
            .padding(2.cssRem)
            .background(ColorUtil.WhiteOp1)
            .borderRadius(15.px)
            .minHeight(200.px)
    }

    val Prompt = CssStyle.base {
        Modifier
            .fontSize(1.2.cssRem)
            .opacity(0.9)
            .margin(1.cssRem, 0.px)
    }

    val Question = CssStyle.base {
        Modifier
            .fontSize(4.cssRem)
            .fontWeight(FontWeight.Bold)
            .margin(1.cssRem, 0.px)
            .textShadow(3.px, 3.px, 6.px, rgba(0, 0, 0, 0.4))
    }
}