package com.github.nanaki_93.components.styles

import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.TextAlign
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.border
import com.varabyte.kobweb.compose.ui.modifiers.borderRadius
import com.varabyte.kobweb.compose.ui.modifiers.boxShadow
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxWidth
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.opacity
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.textAlign
import com.varabyte.kobweb.compose.ui.modifiers.textDecorationLine
import com.varabyte.kobweb.compose.ui.modifiers.textShadow
import com.varabyte.kobweb.compose.ui.modifiers.width
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.base
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba


object AuthStyles {

    val Container = CssStyle.base {
        Modifier
            .width(400.px)
            .padding(2.cssRem)
    }

    val HeaderTitle = CssStyle.base {
        Modifier
            .fontSize(2.5.cssRem)
            .fontWeight(FontWeight.Bold)
            .textShadow(2.px, 2.px, 4.px, rgba(0, 0, 0, 0.3))
    }

    val HeaderSubtitle = CssStyle.base {
        Modifier
            .fontSize(1.5.cssRem)
            .fontWeight(FontWeight.Bold)
    }

    val Form = CssStyle.base {
        Modifier.fillMaxWidth()
    }


    val Input = CssStyle.base {
        Modifier
            .fillMaxWidth()
            .padding(0.8.cssRem)
            .borderRadius(8.px)
            .border(1.px, LineStyle.Solid, rgba(255, 255, 255, 0.3))
            .backgroundColor(rgba(255, 255, 255, 0.1))
            .color(Colors.White)
    }

    val Error = CssStyle.base {
        Modifier
            .fontSize(0.9.cssRem)
            .textAlign(TextAlign.Center)
            .backgroundColor(ColorUtil.WhiteOp2) // semi-transparent white
            .color(Colors.DeepSkyBlue)           // app accent color
            .borderRadius(8.px)
            .padding(0.5.cssRem)
            .fontWeight(FontWeight.Bold)
            .boxShadow(0.px, 2.px, 8.px, color = rgba(0, 0, 0, 0.08))
    }

    val SubmitButton = CssStyle.base {
        Modifier
            .fillMaxWidth()
            .padding(0.8.cssRem)
            .borderRadius(8.px)
            .backgroundColor(Colors.DeepSkyBlue)
            .color(Colors.White)
            .fontWeight(FontWeight.Bold)
    }

    val ToggleContainer = CssStyle.base {
        Modifier.fillMaxWidth()
    }

    val ToggleText = CssStyle.base {
        Modifier
            .fontSize(0.9.cssRem)
            .opacity(0.8)
    }

    val ToggleButton = CssStyle.base {
        Modifier
            .backgroundColor(Colors.Transparent)
            .color(Colors.DeepSkyBlue)
            .textDecorationLine(com.varabyte.kobweb.compose.css.TextDecorationLine.Underline)
            .fontSize(0.9.cssRem)
    }
}