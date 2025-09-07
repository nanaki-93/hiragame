package com.github.nanaki_93

import com.varabyte.kobweb.compose.css.Cursor
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.ScrollBehavior
import com.varabyte.kobweb.compose.css.TextAlign
import com.varabyte.kobweb.compose.css.Transition
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.silk.components.layout.HorizontalDividerStyle
import com.varabyte.kobweb.silk.init.InitSilk
import com.varabyte.kobweb.silk.init.InitSilkContext
import com.varabyte.kobweb.silk.init.registerStyleBase
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.theme.modifyStyleBase
import org.jetbrains.compose.web.css.*

@InitSilk
fun initSiteStyles(ctx: InitSilkContext) {
    // This site does not need scrolling itself, but this is a good demonstration for how you might enable this in your
    // own site. Note that we only enable smooth scrolling unless the user has requested reduced motion, which is
    // considered a best practice.
    ctx.stylesheet.registerStyle("html") {
        cssRule(CSSMediaQuery.MediaFeature("prefers-reduced-motion", StylePropertyValue("no-preference"))) {
            Modifier.scrollBehavior(ScrollBehavior.Smooth)
        }
    }

    ctx.stylesheet.registerStyleBase("body") {
        Modifier
            .fontFamily(
                "-apple-system", "BlinkMacSystemFont", "Segoe UI", "Roboto", "Oxygen", "Ubuntu",
                "Cantarell", "Fira Sans", "Droid Sans", "Helvetica Neue", "sans-serif"
            )
            .fontSize(18.px)
            .lineHeight(1.5)
    }

    // Silk dividers only extend 90% by default; we want full width dividers in our site
    ctx.theme.modifyStyleBase(HorizontalDividerStyle) {
        Modifier.fillMaxWidth()
    }
}


// Styles
val GameContainerStyle = CssStyle.base {
    Modifier
        .fillMaxSize()
        .styleModifier {
            background("linear-gradient(135deg, rgba(102, 126, 234, 1.0) 0%, rgba(118, 75, 162, 1.0) 100%)")
        }
        .minHeight(100.vh)
        .display(DisplayStyle.Flex)
        .justifyContent(JustifyContent.Center)
        .alignItems(AlignItems.Center)
        .fontFamily("Arial", "sans-serif")
}
val CardStyle = CssStyle.base {
    Modifier
        .background(rgba(255, 255, 255, 0.1))
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


val HiraganaCharStyle = CssStyle.base {
    Modifier
        .fontSize(4.cssRem)
        .fontWeight(FontWeight.Bold)
        .margin(1.cssRem, 0.px)
        .textShadow(3.px, 3.px, 6.px, rgba(0, 0, 0, 0.4))
}

val StatItemStyle = CssStyle.base {
    Modifier
        .background(rgba(255, 255, 255, 0.2))
        .padding(0.5.cssRem, 1.cssRem)
        .borderRadius(10.px)
        .flexGrow(1)
        .minWidth(100.px)
        .margin(0.25.cssRem)
}

val InputStyle = CssStyle.base {
    Modifier
        .background(rgba(255, 255, 255, 0.2))
        .border(2.px, LineStyle.Solid, rgba(255, 255, 255, 0.3))
        .borderRadius(10.px)
        .padding(0.8.cssRem)
        .fontSize(1.2.cssRem)
        .color(Colors.White)
        .textAlign(TextAlign.Center)
        .width(100.percent)
        .margin(1.cssRem, 0.px)
}

val ButtonStyle = CssStyle.base {
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


