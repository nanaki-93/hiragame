package com.github.nanaki_93.components.styles

import com.varabyte.kobweb.compose.css.Cursor
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.TextAlign
import com.varabyte.kobweb.compose.css.Transition
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.alignItems
import com.varabyte.kobweb.compose.ui.modifiers.background
import com.varabyte.kobweb.compose.ui.modifiers.border
import com.varabyte.kobweb.compose.ui.modifiers.borderRadius
import com.varabyte.kobweb.compose.ui.modifiers.boxShadow
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.cursor
import com.varabyte.kobweb.compose.ui.modifiers.display
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxWidth
import com.varabyte.kobweb.compose.ui.modifiers.fontFamily
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.justifyContent
import com.varabyte.kobweb.compose.ui.modifiers.maxWidth
import com.varabyte.kobweb.compose.ui.modifiers.minHeight
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.textAlign
import com.varabyte.kobweb.compose.ui.modifiers.textShadow
import com.varabyte.kobweb.compose.ui.modifiers.transition
import com.varabyte.kobweb.compose.ui.modifiers.width
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.base
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.background
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.s
import org.jetbrains.compose.web.css.vh

object CommonStyles {

    val ButtonText = CssStyle.base {
        Modifier.color(Color.white)
    }



    val GameContainer = CssStyle.base {
        Modifier
            .fillMaxWidth()
            .styleModifier {
                background("linear-gradient(135deg, rgba(102, 126, 234, 1.0) 0%, rgba(118, 75, 162, 1.0) 100%)")
            }
            .minHeight(100.vh)
            .display(DisplayStyle.Flex)
            .justifyContent(JustifyContent.Center)
            .alignItems(AlignItems.FlexStart)
            .fontFamily("Arial", "sans-serif")
            .padding(2.cssRem, 1.cssRem)
    }

    val Card = CssStyle.base {
        Modifier
            .background(ColorUtil.WhiteOp1)
            .borderRadius(20.px)
            .padding(2.cssRem)
            .boxShadow(
                0.px, 8.px, 32.px, 0.px,
                color = rgba(31, 38, 135, 0.37),
                inset = false
            )
            .border(1.px, LineStyle.Solid, rgba(255, 255, 255, 0.18))
            .maxWidth(800.px)
            .width(90.percent)
            .textAlign(TextAlign.Center)
            .color(Colors.White)
    }

    val Button = CssStyle.base {
        Modifier
            .styleModifier { background("linear-gradient(45deg, rgba(255, 107, 107, 1.0) 0%, rgba(78, 205, 196, 1.0) 100%)") }
            .border(0.px)
            .padding(0.8.cssRem, 2.cssRem)
            .fontSize(1.1.cssRem)
            .color(Colors.White)
            .borderRadius(25.px)
            .cursor(Cursor.Pointer)
            .boxShadow(0.px, 4.px, 15.px, color = rgba(0, 0, 0, 0.2))
            .transition(Transition.all(0.3.s))
    }

    // HomePage Title Style (extracted from Index.kt inline style)
    val HomePageTitle = CssStyle.base {
        Modifier
            .fontSize(2.5.cssRem)
            .fontWeight(FontWeight.Bold)
            .textShadow(2.px, 2.px, 4.px, rgba(0, 0, 0, 0.3))
    }
}