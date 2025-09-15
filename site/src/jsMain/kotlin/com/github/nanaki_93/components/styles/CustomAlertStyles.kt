package com.github.nanaki_93.components.styles

import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.TextAlign
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.alignItems
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.border
import com.varabyte.kobweb.compose.ui.modifiers.borderRadius
import com.varabyte.kobweb.compose.ui.modifiers.boxShadow
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.display
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.height
import com.varabyte.kobweb.compose.ui.modifiers.justifyContent
import com.varabyte.kobweb.compose.ui.modifiers.left
import com.varabyte.kobweb.compose.ui.modifiers.lineHeight
import com.varabyte.kobweb.compose.ui.modifiers.maxWidth
import com.varabyte.kobweb.compose.ui.modifiers.minWidth
import com.varabyte.kobweb.compose.ui.modifiers.opacity
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.position
import com.varabyte.kobweb.compose.ui.modifiers.textAlign
import com.varabyte.kobweb.compose.ui.modifiers.textShadow
import com.varabyte.kobweb.compose.ui.modifiers.top
import com.varabyte.kobweb.compose.ui.modifiers.width
import com.varabyte.kobweb.compose.ui.modifiers.zIndex
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.base
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.vh
import org.jetbrains.compose.web.css.vw

object CustomAlertStyles {

    val Overlay = CssStyle.base {
        Modifier
            .position(Position.Fixed)
            .top(0.px)
            .left(0.px)
            .width(100.vw)
            .height(100.vh)
            .backgroundColor(rgba(0, 0, 0, 0.7))
            .zIndex(1000)
            .display(DisplayStyle.Flex)
            .alignItems(AlignItems.Center)
            .justifyContent(JustifyContent.Center)
    }

    val Dialog = CssStyle.base {
        Modifier
            .backgroundColor(ColorUtil.WhiteOp1)
            .borderRadius(15.px)
            .padding(2.cssRem)
            .minWidth(300.px)
            .maxWidth(400.px)
            .boxShadow(0.px, 4.px, 16.px, color = rgba(0, 0, 0, 0.3))
            .border(1.px, LineStyle.Solid, ColorUtil.WhiteOp2)
    }

    val Title = CssStyle.base {
        Modifier
            .fontSize(1.5.cssRem)
            .fontWeight(FontWeight.Bold)
            .color(Colors.White)
            .textShadow(2.px, 2.px, 4.px, rgba(0, 0, 0, 0.3))
    }

    val Message = CssStyle.base {
        Modifier
            .fontSize(1.1.cssRem)
            .color(Colors.White)
            .textAlign(TextAlign.Center)
            .lineHeight(1.4)
            .opacity(0.9)
    }

    val CancelButton = CssStyle.base {
        Modifier
            .padding(0.8.cssRem)
            .borderRadius(8.px)
            .backgroundColor(ColorUtil.WhiteOp2)
            .color(Colors.White)
            .border(1.px, LineStyle.Solid, ColorUtil.WhiteOp3)
            .fontWeight(FontWeight.Bold)
    }

    val ConfirmButton = CssStyle.base {
        Modifier
            .padding(0.8.cssRem)
            .borderRadius(8.px)
            .backgroundColor(Colors.DeepSkyBlue)
            .color(Colors.White)
            .fontWeight(FontWeight.Bold)
    }
}