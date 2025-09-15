package com.github.nanaki_93.components.styles

import com.github.nanaki_93.models.GameMode
import com.varabyte.kobweb.compose.css.AnimationIterationCount
import com.varabyte.kobweb.compose.css.Cursor
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.Overflow
import com.varabyte.kobweb.compose.css.PointerEvents
import com.varabyte.kobweb.compose.css.ScrollBehavior
import com.varabyte.kobweb.compose.css.TextAlign
import com.varabyte.kobweb.compose.css.TextTransform
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
import com.varabyte.kobweb.silk.style.GeneralKind
import com.varabyte.kobweb.silk.style.animation.Keyframes
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.theme.modifyStyleBase
import org.jetbrains.compose.web.css.*

// Japanese-inspired Color Palette
object JapaneseColors {
    // Traditional Japanese colors
    val Sakura = rgb(255, 183, 197)        // Cherry blossom pink
    val SakuraDark = rgb(255, 158, 180)    // Darker cherry blossom
    val Indigo = rgb(75, 96, 154)          // Japanese indigo (ai-iro)
    val IndigoDark = rgb(54, 69, 115)      // Darker indigo
    val Wisteria = rgb(147, 132, 209)      // Fuji-iro (wisteria)
    val WisteriaDark = rgb(123, 106, 187)  // Darker wisteria
    val Gold = rgb(255, 215, 0)            // Japanese gold
    val Vermillion = rgb(234, 86, 71)      // Shu-iro (vermillion)
    val Navy = rgb(35, 47, 84)             // Kon-iro (navy)
    val Paper = rgb(250, 248, 245)         // Washi paper white
    val Ink = rgb(45, 45, 45)              // Sumi ink
    val Silver = rgb(192, 192, 192)        // Gin-iro (silver)

    // Modern adaptations
    val GradientStart = rgba(147, 132, 209, 1.0)  // Wisteria
    val GradientEnd = rgba(75, 96, 154, 1.0)      // Indigo
    val CardBg = rgba(250, 248, 245, 0.95)        // Paper-like with opacity
    val CardBorder = rgba(147, 132, 209, 0.3)     // Subtle wisteria border
    val AccentPrimary = rgba(234, 86, 71, 1.0)    // Vermillion
    val AccentSecondary = rgba(255, 183, 197, 1.0) // Sakura
}
//
//@InitSilk
//fun initSiteStyles(ctx: InitSilkContext) {
//    // Smooth scrolling with Japanese aesthetic consideration
//    ctx.stylesheet.registerStyle("html") {
//        cssRule(CSSMediaQuery.MediaFeature("prefers-reduced-motion", StylePropertyValue("no-preference"))) {
//            Modifier.scrollBehavior(ScrollBehavior.Smooth)
//        }
//    }
//
//    // Enhanced body styles with Japanese typography preferences
//    ctx.stylesheet.registerStyleBase("body") {
//        Modifier
//            .fontFamily(
//                "'Noto Sans JP'", "'Hiragino Kaku Gothic ProN'", "'Yu Gothic'",
//                "'Meiryo'", "'Takao'", "'IPAexfont'", "'IPAPGothic'",
//                "'VL PGothic'", "Osaka", "'MS PGothic'", "sans-serif"
//            )
//            .fontSize(16.px)
//            .lineHeight(1.7) // Better for Japanese text readability
//            .letterSpacing(0.02.em)
//    }
//
//    // Enhanced divider styles
//    ctx.theme.modifyStyleBase(HorizontalDividerStyle) {
//        Modifier
//            .fillMaxWidth()
//            .height(1.px)
//            .styleModifier {
//                background("linear-gradient(90deg, transparent 0%, ${JapaneseColors.Wisteria} 50%, transparent 100%)")
//            }
//    }
//}
