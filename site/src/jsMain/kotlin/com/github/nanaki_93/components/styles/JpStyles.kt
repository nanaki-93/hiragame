package com.github.nanaki_93.components.styles

import com.varabyte.kobweb.compose.css.AnimationIterationCount
import com.varabyte.kobweb.compose.css.Cursor
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.ScrollBehavior
import com.varabyte.kobweb.compose.css.TextAlign
import com.varabyte.kobweb.compose.css.TextDecorationLine
import com.varabyte.kobweb.compose.css.Transition
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.silk.components.layout.HorizontalDividerStyle
import com.varabyte.kobweb.silk.init.InitSilk
import com.varabyte.kobweb.silk.init.InitSilkContext
import com.varabyte.kobweb.silk.init.registerStyleBase
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.animation.Keyframes
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.theme.modifyStyleBase
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.AnimationTimingFunction
import org.jetbrains.compose.web.css.CSSColorValue
import org.jetbrains.compose.web.css.CSSMediaQuery
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.StylePropertyValue
import org.jetbrains.compose.web.css.background
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.css.deg
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.s
import org.jetbrains.compose.web.css.vh
import org.jetbrains.compose.web.css.vw

@InitSilk
fun initSiteStyles(ctx: InitSilkContext) {
    ctx.stylesheet.registerStyle("html") {
        cssRule(CSSMediaQuery.MediaFeature("prefers-reduced-motion", StylePropertyValue("no-preference"))) {
            Modifier.scrollBehavior(ScrollBehavior.Smooth)
        }
    }

    ctx.stylesheet.registerStyleBase("body") {
        // Updated font to a more suitable one for Japanese aesthetic
        Modifier
            .fontFamily(
                "Noto Sans Japanese",
                "-apple-system", "BlinkMacSystemFont", "Segoe UI", "Roboto", "Oxygen", "Ubuntu",
                "Cantarell", "Fira Sans", "Droid Sans", "Helvetica Neue", "sans-serif"
            )
            .fontSize(18.px)
            .lineHeight(1.5)
    }

    ctx.theme.modifyStyleBase(HorizontalDividerStyle) {
        Modifier.fillMaxWidth()
    }
}

object ColorPalette {
    val Background = Color("#F5EFE6") // Soft, creamy off-white
    val CardBackground = Color("#FFFFFF") // Clean white
    val Accent = Color("#E69A8D") // A soft, muted sakura pink
    val SecondaryAccent = Color("#92B4A7") // A gentle, forest green
    val Text = Color("#333333") // Dark gray for better readability
    val SubtleText = Color("#A39D90") // Softer gray for prompts and minor text
    val Border = Color("#AFA59B") // Subtle, warm gray for borders
}

// Styles
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
            .border(1.px, LineStyle.Solid, ColorPalette.Border)
            .backgroundColor(Color.transparent)
            .color(ColorPalette.Text)
    }

    val Error = CssStyle.base {
        Modifier
            .fontSize(0.9.cssRem)
            .textAlign(TextAlign.Center)
            .backgroundColor(ColorPalette.Accent)
            .color(Colors.White)
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
            .backgroundColor(ColorPalette.Accent)
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
            .color(ColorPalette.Text)
    }

    val ToggleButton = CssStyle.base {
        Modifier
            .backgroundColor(Colors.Transparent)
            .color(ColorPalette.Accent)
            .textDecorationLine(TextDecorationLine.Underline)
            .fontSize(0.9.cssRem)
    }
}

object CommonStyles {

        val HomePageTitle = CssStyle.base {
        Modifier
            .fontSize(2.5.cssRem)
            .fontWeight(FontWeight.Bold)
            .textShadow(2.px, 2.px, 4.px, rgba(0, 0, 0, 0.3))
    }
    val ButtonText = CssStyle.base {
        Modifier.color(Color.white)
    }

    val GameContainer = CssStyle.base {
        Modifier
            .fillMaxWidth()
            .background(ColorPalette.Background)
            .minHeight(100.vh)
            .display(DisplayStyle.Flex)
            .justifyContent(JustifyContent.Center)
            .alignItems(AlignItems.FlexStart)
            .fontFamily("Noto Sans Japanese", "sans-serif")
            .padding(2.cssRem, 1.cssRem)
    }

    val Card = CssStyle.base {
        Modifier
            .background(ColorPalette.CardBackground)
            .borderRadius(15.px)
            .padding(2.cssRem)
            .boxShadow(0.px, 4.px, 15.px, color = rgba(0, 0, 0, 0.1))
            .maxWidth(800.px)
            .width(90.percent)
            .textAlign(TextAlign.Center)
            .color(ColorPalette.Text)
    }

    val Button = CssStyle.base {
        Modifier
            .background(ColorPalette.Accent)
            .border(0.px)
            .padding(0.8.cssRem, 2.cssRem)
            .fontSize(1.1.cssRem)
            .color(Colors.White)
            .borderRadius(25.px)
            .cursor(Cursor.Pointer)
            .transition(Transition.all(0.3.s))
            .boxShadow(0.px, 4.px, 15.px, color = rgba(0, 0, 0, 0.2))
    }
}

object CustomAlertStyles {

    val Overlay = CssStyle.base {
        Modifier
            .position(Position.Fixed)
            .top(0.px)
            .left(0.px)
            .width(100.vw)
            .height(100.vh)
            .backgroundColor(rgba(0, 0, 0, 0.4)) // Lighter overlay
            .zIndex(1000)
            .display(DisplayStyle.Flex)
            .alignItems(AlignItems.Center)
            .justifyContent(JustifyContent.Center)
    }

    val Dialog = CssStyle.base {
        Modifier
            .backgroundColor(ColorPalette.CardBackground)
            .borderRadius(15.px)
            .padding(2.cssRem)
            .minWidth(300.px)
            .maxWidth(400.px)
            .boxShadow(0.px, 4.px, 16.px, color = rgba(0, 0, 0, 0.2))
    }

    val Title = CssStyle.base {
        Modifier
            .fontSize(1.5.cssRem)
            .fontWeight(FontWeight.Bold)
            .color(ColorPalette.Text)
            .textShadow(2.px, 2.px, 4.px, rgba(0, 0, 0, 0.1))
    }

    val Message = CssStyle.base {
        Modifier
            .fontSize(1.1.cssRem)
            .color(ColorPalette.SubtleText)
            .textAlign(TextAlign.Center)
            .lineHeight(1.4)
            .opacity(0.9)
    }

    val CancelButton = CssStyle.base {
        Modifier
            .padding(0.8.cssRem)
            .borderRadius(8.px)
            .backgroundColor(ColorPalette.Border)
            .color(ColorPalette.Text)
            .border(1.px, LineStyle.Solid, ColorPalette.Border)
            .fontWeight(FontWeight.Bold)
    }

    val ConfirmButton = CssStyle.base {
        Modifier
            .padding(0.8.cssRem)
            .borderRadius(8.px)
            .backgroundColor(ColorPalette.Accent)
            .color(Colors.White)
            .fontWeight(FontWeight.Bold)
    }
}

object FeedbackStyles {
    val Title = CssStyle.base {
        Modifier
            .fontSize(1.2.cssRem)
            .fontWeight(FontWeight.Bold)
            .color(ColorPalette.Text)
    }

    val Message = CssStyle.base {
        Modifier
            .fontSize(1.1.cssRem)
            .fontWeight(FontWeight.Bold)
            .margin(1.cssRem, 0.px)
    }

    fun getFeedbackColor(isCorrect: Boolean?): CSSColorValue = when (isCorrect) {
        true -> ColorPalette.SecondaryAccent
        false -> ColorPalette.Accent
        null -> ColorPalette.SubtleText
    }
}

object GameModeStyles {

    val LoadingText = CssStyle.base {
        Modifier
            .fontSize(0.9.cssRem)
            .opacity(0.7)
    }

    val SelectorTitle = CssStyle.base {
        Modifier.fontSize(1.1.cssRem)
    }

    val Button = CssStyle.base {
        Modifier
            .padding(0.6.cssRem, 0.5.cssRem)
            .borderRadius(10.px)
            .cursor(Cursor.Pointer)
            .transition(Transition.all(0.2.s))
            .minWidth(80.px)
            .textAlign(TextAlign.Center)
    }

    fun getButtonBackground(isSelected: Boolean) =
        Modifier
            .backgroundColor(if (isSelected) ColorPalette.SecondaryAccent else Colors.White)
            .color(if (isSelected) Colors.White else ColorPalette.Text)
            .let { mod ->
                if (isSelected) mod.border(2.px, LineStyle.Solid, ColorPalette.SecondaryAccent)
                else mod.border(1.px, LineStyle.Solid, ColorPalette.Border)
            }
            .fontWeight(if (isSelected) FontWeight.Bold else FontWeight.Normal)
}

object GameStatsStyles {
    val Item = CssStyle.base {
        Modifier
            .background(ColorPalette.CardBackground)
            .padding(0.5.cssRem, 1.cssRem)
            .borderRadius(10.px)
            .flexGrow(1)
            .minWidth(100.px)
            .margin(0.25.cssRem)
            .border(1.px, LineStyle.Solid, ColorPalette.Border)
    }

    val Label = CssStyle.base {
        Modifier
            .fontSize(0.8.cssRem)
            .color(ColorPalette.SubtleText)
    }

    val Value = CssStyle.base {
        Modifier
            .fontSize(1.2.cssRem)
            .fontWeight(FontWeight.Bold)
            .color(ColorPalette.Text)
    }
}

object LevelSelectorStyles {

    val Button = CssStyle.base {
        Modifier
            .padding(0.5.cssRem, 0.4.cssRem)
            .borderRadius(10.px)
            .minWidth(55.px)
            .textAlign(TextAlign.Center)
            .cursor(Cursor.Pointer)
            .transition(Transition.all(0.2.s))
    }

    fun getBackground(isSelected: Boolean) =
        Modifier
            .backgroundColor(if (isSelected) ColorPalette.SecondaryAccent else Colors.White)
            .color(if (isSelected) Colors.White else ColorPalette.Text)
            .let { mod ->
                if (isSelected) mod.border(2.px, LineStyle.Solid, ColorPalette.SecondaryAccent)
                else mod.border(1.px, LineStyle.Solid, ColorPalette.Border)
            }
            .fontWeight(if (isSelected) FontWeight.Bold else FontWeight.Normal)

    val SelectorTitle = CssStyle.base {
        Modifier
            .fontSize(1.1.cssRem)
            .color(ColorPalette.Text)
    }
}

object QuestionAreaStyles {

    val Input = CssStyle.base {
        Modifier
            .background(ColorPalette.CardBackground)
            .border(2.px, LineStyle.Solid, ColorPalette.Border)
            .borderRadius(10.px)
            .padding(0.8.cssRem)
            .fontSize(1.2.cssRem)
            .color(ColorPalette.Text)
            .textAlign(TextAlign.Center)
            .width(100.percent)
            .margin(1.cssRem, 0.px)
    }
    val AreaContainer = CssStyle.base {
        Modifier
            .fillMaxWidth()
            .padding(2.cssRem)
            .background(ColorPalette.CardBackground)
            .borderRadius(15.px)
            .minHeight(200.px)
    }

    val Prompt = CssStyle.base {
        Modifier
            .fontSize(1.2.cssRem)
            .color(ColorPalette.SubtleText)
            .margin(1.cssRem, 0.px)
    }

    val Question = CssStyle.base {
        Modifier
            .fontSize(4.cssRem)
            .fontWeight(FontWeight.Bold)
            .margin(1.cssRem, 0.px)
            .color(ColorPalette.Text)
            .textShadow(3.px, 3.px, 6.px, rgba(0, 0, 0, 0.1))
    }
}

object SpinnerStyles {
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