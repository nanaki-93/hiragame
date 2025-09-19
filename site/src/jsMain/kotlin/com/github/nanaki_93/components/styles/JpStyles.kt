package com.github.nanaki_93.components.styles

import com.varabyte.kobweb.compose.css.AnimationIterationCount
import com.varabyte.kobweb.compose.css.Cursor
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.ScrollBehavior
import com.varabyte.kobweb.compose.css.TextAlign
import com.varabyte.kobweb.compose.css.TextDecorationLine
import com.varabyte.kobweb.compose.css.Transition
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.silk.components.layout.HorizontalDividerStyle
import com.varabyte.kobweb.silk.init.InitSilk
import com.varabyte.kobweb.silk.init.InitSilkContext
import com.varabyte.kobweb.silk.init.registerStyleBase
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.animation.Keyframes
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.theme.modifyStyleBase
import org.jetbrains.compose.web.css.*

@InitSilk
fun initSiteStyles(ctx: InitSilkContext) {
    ctx.stylesheet.registerStyle("html") {
        cssRule(CSSMediaQuery.MediaFeature("prefers-reduced-motion", StylePropertyValue("no-preference"))) {
            Modifier.scrollBehavior(ScrollBehavior.Smooth)
        }
    }

    ctx.stylesheet.registerStyleBase("body") {
        Modifier
            .fontFamily(
                "Noto Sans Japanese", "-apple-system", "BlinkMacSystemFont",
                "Segoe UI", "Roboto", "sans-serif"
            )
            .fontSize(18.px)
            .lineHeight(1.5)
    }

    ctx.theme.modifyStyleBase(HorizontalDividerStyle) {
        Modifier.fillMaxWidth()
    }
}

// Unified Color Palette
object Colors {
    val Background = Color("#F5EFE6")      // Soft creamy off-white
    val CardBackground = Color("#FFFFFF")   // Clean white
    val Primary = Color("#E69A8D")         // Soft sakura pink
    val Secondary = Color("#92B4A7")       // Gentle forest green
    val Text = Color("#333333")            // Dark gray
    val TextSubtle = Color("#A39D90")      // Softer gray
    val Border = Color("#AFA59B")          // Subtle warm gray
    val Error = Primary                     // Use primary for errors
    val Success = Secondary                 // Use secondary for success
}

// Base Modifier Functions - The foundation of all styles
object Base {
    fun card() = Modifier
        .background(Colors.CardBackground)
        .borderRadius(15.px)
        .padding(2.cssRem)
        .boxShadow(0.px, 4.px, 15.px, color = rgba(0, 0, 0, 0.1))

    fun button() = Modifier
        .padding(0.8.cssRem)
        .borderRadius(8.px)
        .fontWeight(FontWeight.Bold)
        .cursor(Cursor.Pointer)
        .transition(Transition.all(0.3.s))
        .border(0.px)

    fun input() = Modifier
        .padding(0.8.cssRem)
        .borderRadius(8.px)
        .border(1.px, LineStyle.Solid, Colors.Border)
        .backgroundColor(Colors.CardBackground)
        .color(Colors.Text)

    fun text(size: CSSLengthValue = 1.cssRem, weight: FontWeight = FontWeight.Normal) = Modifier
        .fontSize(size)
        .fontWeight(weight)
        .color(Colors.Text)
}

// Unified Styles - One place for all component styles
object Styles {

    // Layout Styles
    val GameContainer = CssStyle.base {
        Modifier
            .fillMaxWidth()
            .background(Colors.Background)
            .minHeight(100.vh)
            .display(DisplayStyle.Flex)
            .justifyContent(JustifyContent.Center)
            .alignItems(AlignItems.FlexStart)
            .fontFamily("Noto Sans Japanese", "sans-serif")
            .padding(2.cssRem, 1.cssRem)
    }

    val Card = CssStyle.base {
        Base.card()
            .maxWidth(800.px)
            .width(90.percent)
            .textAlign(TextAlign.Center)
            .color(Colors.Text)
    }

    val QuestionCard = CssStyle.base {
        Base.card()
            .fillMaxWidth()
            .minHeight(200.px)
    }

    val StatItem = CssStyle.base {
        Base.card()
            .padding(0.5.cssRem, 1.cssRem)
            .borderRadius(10.px)
            .flexGrow(1)
            .minWidth(100.px)
            .margin(0.25.cssRem)
            .border(1.px, LineStyle.Solid, Colors.Border)
    }

    // Text Styles
    val Title = CssStyle.base {
        Base.text(2.5.cssRem, FontWeight.Bold)
            .textShadow(2.px, 2.px, 4.px, rgba(0, 0, 0, 0.3))
    }

    val Subtitle = CssStyle.base {
        Base.text(1.5.cssRem, FontWeight.Bold)
    }

    val Question = CssStyle.base {
        Base.text(4.cssRem, FontWeight.Bold)
            .margin(1.cssRem, 0.px)
            .textShadow(3.px, 3.px, 6.px, rgba(0, 0, 0, 0.1))
    }

    val Prompt = CssStyle.base {
        Base.text(1.2.cssRem)
            .color(Colors.TextSubtle)
            .margin(1.cssRem, 0.px)
    }

    val Label = CssStyle.base {
        Base.text(0.8.cssRem)
            .color(Colors.TextSubtle)
    }

    val Value = CssStyle.base {
        Base.text(1.2.cssRem, FontWeight.Bold)
    }

    val Error = CssStyle.base {
        Base.text(0.9.cssRem, FontWeight.Bold)
            .textAlign(TextAlign.Center)
            .backgroundColor(Colors.Error)
            .color(org.jetbrains.compose.web.css.Color.white)
            .borderRadius(8.px)
            .padding(0.5.cssRem)
            .boxShadow(0.px, 2.px, 8.px, color = rgba(0, 0, 0, 0.08))
    }

    // Button Styles
    val ButtonPrimary = CssStyle.base {
        Base.button()
            .backgroundColor(Colors.Primary)
            .color(org.jetbrains.compose.web.css.Color.white)
            .boxShadow(0.px, 4.px, 15.px, color = rgba(0, 0, 0, 0.2))
    }

    val ButtonSecondary = CssStyle.base {
        Base.button()
            .backgroundColor(Colors.Border)
            .color(Colors.Text)
            .border(1.px, LineStyle.Solid, Colors.Border)
    }

    val ButtonSmall = CssStyle.base {
        Base.button()
            .padding(0.5.cssRem, 0.8.cssRem)
            .borderRadius(10.px)
            .minWidth(80.px)
            .textAlign(TextAlign.Center)
    }

    val ButtonLink = CssStyle.base {
        Modifier
            .backgroundColor(org.jetbrains.compose.web.css.Color.transparent)
            .color(Colors.Primary)
            .textDecorationLine(TextDecorationLine.Underline)
            .fontSize(0.9.cssRem)
            .cursor(Cursor.Pointer)
    }

    // Input Styles
    val Input = CssStyle.base {
        Base.input()
            .fillMaxWidth()
    }

    val InputLarge = CssStyle.base {
        Base.input()
            .fontSize(1.2.cssRem)
            .textAlign(TextAlign.Center)
            .width(100.percent)
            .margin(1.cssRem, 0.px)
            .border(2.px, LineStyle.Solid, Colors.Border)
            .borderRadius(10.px)
    }

    // Feedback Styles
    val FeedbackMessage = CssStyle.base {
        Base.text(1.1.cssRem, FontWeight.Bold)
            .margin(1.cssRem, 0.px)
    }

    // Modal Styles
    val ModalOverlay = CssStyle.base {
        Modifier
            .position(Position.Fixed)
            .top(0.px).left(0.px)
            .width(100.vw).height(100.vh)
            .backgroundColor(rgba(0, 0, 0, 0.4))
            .zIndex(1000)
            .display(DisplayStyle.Flex)
            .alignItems(AlignItems.Center)
            .justifyContent(JustifyContent.Center)
    }

    val ModalDialog = CssStyle.base {
        Base.card()
            .minWidth(300.px)
            .maxWidth(400.px)
            .boxShadow(0.px, 4.px, 16.px, color = rgba(0, 0, 0, 0.2))
    }
    // Loading and feedback styles
    val LoadingText = CssStyle.base {
        Base.text(0.9.cssRem)
            .color(Colors.TextSubtle)
            .opacity(0.7)
    }

    val ToggleText = CssStyle.base {
        Base.text(0.9.cssRem)
            .color(Colors.Text)
            .opacity(0.8)
    }
    // Spinner Animation
    val SpinnerKeyframes = Keyframes {
        0.percent { Modifier.rotate(0.deg) }
        100.percent { Modifier.rotate(360.deg) }
    }

    val Spinner = CssStyle.base {
        Modifier
            .borderRadius(50.percent)
            .animation(
                SpinnerKeyframes.toAnimation(
                    duration = 1.s,
                    iterationCount = AnimationIterationCount.Infinite,
                    timingFunction = AnimationTimingFunction.Linear
                )
            )
    }
}

// Helper Functions for Dynamic Styling
object StyleHelpers {
    fun getFeedbackColor(isCorrect: Boolean?): CSSColorValue = when (isCorrect) {
        true -> Colors.Success
        false -> Colors.Error
        null -> Colors.TextSubtle
    }

    fun getSelectionStyle(isSelected: Boolean): Modifier {
        return if (isSelected) {
            Modifier
                .backgroundColor(Colors.Secondary)
                .color(org.jetbrains.compose.web.css.Color.white)
                .border(2.px, LineStyle.Solid, Colors.Secondary)
                .fontWeight(FontWeight.Bold)
        } else {
            Modifier
                .backgroundColor(org.jetbrains.compose.web.css.Color.white)
                .color(Colors.Text)
                .border(1.px, LineStyle.Solid, Colors.Border)
                .fontWeight(FontWeight.Normal)
        }
    }
}
