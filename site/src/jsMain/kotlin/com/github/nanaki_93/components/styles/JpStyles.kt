package com.github.nanaki_93.components.styles

import com.varabyte.kobweb.compose.css.*
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
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.JustifyContent

@InitSilk
fun initSiteStyles(ctx: InitSilkContext) {
    ctx.stylesheet.registerStyle("html") {
        cssRule(CSSMediaQuery.MediaFeature("prefers-reduced-motion", StylePropertyValue("no-preference"))) {
            Modifier.scrollBehavior(ScrollBehavior.Smooth)
        }
    }
    ctx.stylesheet.registerStyleBase("body") {
        Modifier.fontFamily("Noto Sans Japanese", "-apple-system", "BlinkMacSystemFont", "Segoe UI", "Roboto", "sans-serif")
            .fontSize(18.px)
            .lineHeight(1.5)
    }
    ctx.theme.modifyStyleBase(HorizontalDividerStyle) { Modifier.fillMaxWidth() }
}

object Colors {
    val Background = Color("#F8F5F2") // Lighter, warmer off-white
    val CardBackground = Color("#FFFFFF")
    val Primary = Color("#E3B09C") // Muted Sakura pink / Peach
    val Secondary = Color("#9EB89B") // Muted Forest green / Moss green
    val Text = Color("#3B3B3B") // Slightly softer dark gray
    val TextSubtle = Color("#8C8684") // Warmer subtle gray
    val Border = Color("#D4C7B8") // A warmer, subtle border color
    val Error = Color("#E07D7D") // Muted red for errors
    val Success = Color("#7DAE7D") // Muted green for success
}

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
        .textAlign(TextAlign.Center)
        .display(DisplayStyle.Flex)
        .alignItems(AlignItems.Center)
        .justifyContent(JustifyContent.Center)

    fun input() = Modifier
        .padding(0.8.cssRem)
        .borderRadius(8.px)
        .border(2.px, LineStyle.Solid, Colors.Border) // Slightly thicker, more modern border
        .backgroundColor(Color.white)
        .color(Colors.Text)
        .outline(0.px) // Remove default outline

    fun text(size: CSSLengthValue = 1.cssRem, weight: FontWeight = FontWeight.Normal,style: FontStyle = FontStyle.Normal) = Modifier
        .fontSize(size)
        .fontWeight(weight)
        .fontStyle(style)
        .color(Colors.Text)

    fun headingText() = Modifier.fontFamily("'Zen Old Mincho', serif", "Noto Sans Japanese", "sans-serif").fontWeight(FontWeight.Bold)
    fun bodyText() = Modifier.fontFamily("'Noto Sans Japanese', sans-serif").fontWeight(FontWeight.Normal)
}

object Styles {
    val GameContainer = CssStyle.base {
        Modifier.fillMaxWidth().background(Colors.Background).minHeight(100.vh)
            .display(DisplayStyle.Flex).justifyContent(JustifyContent.Center)
            .alignItems(AlignItems.FlexStart).fontFamily("Noto Sans Japanese", "sans-serif")
            .padding(2.cssRem, 1.cssRem)
    }

    val Card = CssStyle.base { Base.card().maxWidth(800.px).width(90.percent).textAlign(TextAlign.Center).color(Colors.Text) }
    val QuestionCard = CssStyle.base { Base.card().fillMaxWidth().minHeight(200.px) }
    val StatItem = CssStyle.base { Base.card().padding(0.5.cssRem, 1.cssRem).borderRadius(10.px).flexGrow(1).minWidth(100.px).margin(0.25.cssRem).border(1.px, LineStyle.Solid, Colors.Border) }
    val Title = CssStyle.base { Base.headingText().fontSize(2.5.cssRem).textShadow(2.px, 2.px, 4.px, rgba(0, 0, 0, 0.1)) }
    val Subtitle = CssStyle.base { Base.headingText().fontSize(1.5.cssRem) }
    val Question = CssStyle.base { Base.text(4.cssRem, FontWeight.Bold).margin(1.cssRem, 0.px).textShadow(3.px, 3.px, 6.px, rgba(0, 0, 0, 0.1)) }
    val Prompt = CssStyle.base { Base.text(1.2.cssRem).color(Colors.TextSubtle).margin(1.cssRem, 0.px) }
    val Label = CssStyle.base { Base.text(0.8.cssRem).color(Colors.TextSubtle) }
    val TitleLabel = CssStyle.base { Base.text(size = 1.2.cssRem).color(Colors.TextSubtle) }
    val TitleValue = CssStyle.base { Base.text(size = 1.2.cssRem, weight = FontWeight.Bold) }
    val NoValue = CssStyle.base { Base.text(1.2.cssRem).color(Colors.Text) }

    val Value = CssStyle.base { Base.text(1.2.cssRem, FontWeight.Bold) }
    val Error = CssStyle.base { Base.text(0.9.cssRem, FontWeight.Bold).textAlign(TextAlign.Center).backgroundColor(Colors.Error).color(Color.white).borderRadius(8.px).padding(0.5.cssRem).boxShadow(0.px, 2.px, 8.px, color =  rgba(0, 0, 0, 0.08)) }
    val ButtonPrimary = CssStyle.base { Base.button().backgroundColor(Colors.Primary).color(Color.white).boxShadow(0.px, 4.px, 15.px, color = rgba(0, 0, 0, 0.2)) }
    val ButtonSecondary = CssStyle.base { Base.button().backgroundColor(Color.transparent).color(Colors.Text).border(2.px, LineStyle.Solid, Colors.Border)  }
    val ButtonSmall = CssStyle.base { Base.button().padding(0.5.cssRem, 0.8.cssRem).fontSize(0.9.cssRem).minWidth(80.px) }
    val ButtonLink = CssStyle.base { Modifier.backgroundColor(Color.transparent).color(Colors.Primary).textDecorationLine(TextDecorationLine.Underline).fontSize(0.9.cssRem).cursor(Cursor.Pointer) }
    val Input = CssStyle.base { Base.input().fillMaxWidth() }

    val InputSimple = CssStyle.base { Base.input().border(1.px, LineStyle.Solid, Colors.Border).boxShadow(0.px, 2.px, 5.px, color = rgba(0, 0, 0, 0.05)).transition(Transition.of("border-color", 0.2.s)).then(Modifier.onClick { Modifier.border(1.px,LineStyle.Solid, Colors.Primary) }) }
    val InputLarge = CssStyle.base { Base.input().fontSize(1.2.cssRem).textAlign(TextAlign.Center).width(100.percent).margin(1.cssRem, 0.px).border(2.px, LineStyle.Solid, Colors.Border).borderRadius(10.px) }
    val FeedbackMessage = CssStyle.base { Base.text(1.1.cssRem, FontWeight.Bold).margin(1.cssRem, 0.px) }
    val ModalOverlay = CssStyle.base { Modifier.position(Position.Fixed).top(0.px).left(0.px).width(100.vw).height(100.vh).backgroundColor(rgba(0, 0, 0, 0.4)).zIndex(1000).display(DisplayStyle.Flex).alignItems(AlignItems.Center).justifyContent(JustifyContent.Center) }
    val ModalDialog = CssStyle.base { Base.card().minWidth(300.px).maxWidth(400.px).boxShadow(0.px, 4.px, 16.px, color = rgba(0, 0, 0, 0.2)) }
    val LoadingText = CssStyle.base { Base.text(0.9.cssRem).color(Colors.TextSubtle).opacity(0.7) }
    val ToggleText = CssStyle.base { Base.text(0.9.cssRem).color(Colors.Text).opacity(0.8) }
    val SpinnerKeyframes = Keyframes { 0.percent { Modifier.rotate(0.deg) }; 100.percent { Modifier.rotate(360.deg) } }
    val Spinner = CssStyle.base { Modifier.borderRadius(50.percent).animation(SpinnerKeyframes.toAnimation(duration = 1.s, iterationCount = AnimationIterationCount.Infinite, timingFunction = AnimationTimingFunction.Linear)) }
}

object StyleHelpers {
    fun getFeedbackColor(isCorrect: Boolean?) = when (isCorrect) {
        true -> Colors.Success
        false -> Colors.Error
        null -> Colors.TextSubtle
    }

    fun getSelectionStyle(isSelected: Boolean) = if (isSelected) {
        Modifier.backgroundColor(Colors.Secondary).color(Color.white).border(2.px, LineStyle.Solid, Colors.Secondary).fontWeight(FontWeight.Bold)
    } else {
        Modifier.backgroundColor(Color.white).color(Colors.Text).border(1.px, LineStyle.Solid, Colors.Border).fontWeight(FontWeight.Normal)
    }
}
