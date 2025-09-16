//package com.github.nanaki_93.components.styles
//
//import com.varabyte.kobweb.compose.css.AnimationIterationCount
//import com.varabyte.kobweb.compose.css.Cursor
//import com.varabyte.kobweb.compose.css.FontWeight
//import com.varabyte.kobweb.compose.css.ScrollBehavior
//import com.varabyte.kobweb.compose.css.TextAlign
//import com.varabyte.kobweb.compose.css.TextDecorationLine
//import com.varabyte.kobweb.compose.css.Transition
//import com.varabyte.kobweb.compose.ui.Modifier
//import com.varabyte.kobweb.compose.ui.graphics.Colors
//import com.varabyte.kobweb.compose.ui.modifiers.*
//import com.varabyte.kobweb.compose.ui.styleModifier
//import com.varabyte.kobweb.silk.components.layout.HorizontalDividerStyle
//import com.varabyte.kobweb.silk.init.InitSilk
//import com.varabyte.kobweb.silk.init.InitSilkContext
//import com.varabyte.kobweb.silk.init.registerStyleBase
//import com.varabyte.kobweb.silk.style.CssStyle
//import com.varabyte.kobweb.silk.style.animation.Keyframes
//import com.varabyte.kobweb.silk.style.base
//import com.varabyte.kobweb.silk.theme.modifyStyleBase
//import org.jetbrains.compose.web.css.AlignItems
//import org.jetbrains.compose.web.css.AnimationTimingFunction
//import org.jetbrains.compose.web.css.CSSColorValue
//import org.jetbrains.compose.web.css.CSSMediaQuery
//import org.jetbrains.compose.web.css.Color
//import org.jetbrains.compose.web.css.DisplayStyle
//import org.jetbrains.compose.web.css.JustifyContent
//import org.jetbrains.compose.web.css.LineStyle
//import org.jetbrains.compose.web.css.Position
//import org.jetbrains.compose.web.css.StylePropertyValue
//import org.jetbrains.compose.web.css.background
//import org.jetbrains.compose.web.css.cssRem
//import org.jetbrains.compose.web.css.deg
//import org.jetbrains.compose.web.css.percent
//import org.jetbrains.compose.web.css.px
//import org.jetbrains.compose.web.css.rgba
//import org.jetbrains.compose.web.css.s
//import org.jetbrains.compose.web.css.vh
//import org.jetbrains.compose.web.css.vw
//
//@InitSilk
//fun initSiteStyles(ctx: InitSilkContext) {
//    // This site does not need scrolling itself, but this is a good demonstration for how you might enable this in your
//    // own site. Note that we only enable smooth scrolling unless the user has requested reduced motion, which is
//    // considered a best practice.
//    ctx.stylesheet.registerStyle("html") {
//        cssRule(CSSMediaQuery.MediaFeature("prefers-reduced-motion", StylePropertyValue("no-preference"))) {
//            Modifier.scrollBehavior(ScrollBehavior.Smooth)
//        }
//    }
//
//    ctx.stylesheet.registerStyleBase("body") {
//        Modifier
//            .fontFamily(
//                "-apple-system", "BlinkMacSystemFont", "Segoe UI", "Roboto", "Oxygen", "Ubuntu",
//                "Cantarell", "Fira Sans", "Droid Sans", "Helvetica Neue", "sans-serif"
//            )
//            .fontSize(18.px)
//            .lineHeight(1.5)
//    }
//
//    // Silk dividers only extend 90% by default; we want full width dividers in our site
//    ctx.theme.modifyStyleBase(HorizontalDividerStyle) {
//        Modifier.fillMaxWidth()
//    }
//}
//
//
//// Styles
//object AuthStyles {
//
//    val Container = CssStyle.Companion.base {
//        Modifier
//            .width(400.px)
//            .padding(2.cssRem)
//    }
//
//    val HeaderTitle = CssStyle.Companion.base {
//        Modifier
//            .fontSize(2.5.cssRem)
//            .fontWeight(FontWeight.Companion.Bold)
//            .textShadow(2.px, 2.px, 4.px, rgba(0, 0, 0, 0.3))
//    }
//
//    val HeaderSubtitle = CssStyle.Companion.base {
//        Modifier
//            .fontSize(1.5.cssRem)
//            .fontWeight(FontWeight.Companion.Bold)
//    }
//
//    val Form = CssStyle.Companion.base {
//        Modifier.fillMaxWidth()
//    }
//
//
//    val Input = CssStyle.Companion.base {
//        Modifier
//            .fillMaxWidth()
//            .padding(0.8.cssRem)
//            .borderRadius(8.px)
//            .border(1.px, LineStyle.Companion.Solid, rgba(255, 255, 255, 0.3))
//            .backgroundColor(rgba(255, 255, 255, 0.1))
//            .color(Colors.White)
//    }
//
//    val Error = CssStyle.Companion.base {
//        Modifier
//            .fontSize(0.9.cssRem)
//            .textAlign(TextAlign.Companion.Center)
//            .backgroundColor(ColorUtil.WhiteOp2) // semi-transparent white
//            .color(Colors.DeepSkyBlue)           // app accent color
//            .borderRadius(8.px)
//            .padding(0.5.cssRem)
//            .fontWeight(FontWeight.Companion.Bold)
//            .boxShadow(0.px, 2.px, 8.px, color = rgba(0, 0, 0, 0.08))
//    }
//
//    val SubmitButton = CssStyle.Companion.base {
//        Modifier
//            .fillMaxWidth()
//            .padding(0.8.cssRem)
//            .borderRadius(8.px)
//            .backgroundColor(Colors.DeepSkyBlue)
//            .color(Colors.White)
//            .fontWeight(FontWeight.Companion.Bold)
//    }
//
//    val ToggleContainer = CssStyle.Companion.base {
//        Modifier.fillMaxWidth()
//    }
//
//    val ToggleText = CssStyle.Companion.base {
//        Modifier
//            .fontSize(0.9.cssRem)
//            .opacity(0.8)
//    }
//
//    val ToggleButton = CssStyle.Companion.base {
//        Modifier.Companion
//            .backgroundColor(Colors.Transparent)
//            .color(Colors.DeepSkyBlue)
//            .textDecorationLine(TextDecorationLine.Underline)
//            .fontSize(0.9.cssRem)
//    }
//}
//
//object CommonStyles {
//
//    val ButtonText = CssStyle.base {
//        Modifier.Companion.color(Color.white)
//    }
//
//
//
//    val GameContainer = CssStyle.base {
//        Modifier
//            .fillMaxWidth()
//            .styleModifier {
//                background("linear-gradient(135deg, rgba(102, 126, 234, 1.0) 0%, rgba(118, 75, 162, 1.0) 100%)")
//            }
//            .minHeight(100.vh)
//            .display(DisplayStyle.Companion.Flex)
//            .justifyContent(JustifyContent.Companion.Center)
//            .alignItems(AlignItems.Companion.FlexStart)
//            .fontFamily("Arial", "sans-serif")
//            .padding(2.cssRem, 1.cssRem)
//    }
//
//    val Card = CssStyle.base {
//        Modifier
//            .background(ColorUtil.WhiteOp1)
//            .borderRadius(20.px)
//            .padding(2.cssRem)
//            .boxShadow(
//                0.px, 8.px, 32.px, 0.px,
//                color = rgba(31, 38, 135, 0.37),
//                inset = false
//            )
//            .border(1.px, LineStyle.Solid, rgba(255, 255, 255, 0.18))
//            .maxWidth(800.px)
//            .width(90.percent)
//            .textAlign(TextAlign.Center)
//            .color(Colors.White)
//    }
//
//    val Button = CssStyle.base {
//        Modifier
//            .styleModifier { background("linear-gradient(45deg, rgba(255, 107, 107, 1.0) 0%, rgba(78, 205, 196, 1.0) 100%)") }
//            .border(0.px)
//            .padding(0.8.cssRem, 2.cssRem)
//            .fontSize(1.1.cssRem)
//            .color(Colors.White)
//            .borderRadius(25.px)
//            .cursor(Cursor.Companion.Pointer)
//            .boxShadow(0.px, 4.px, 15.px, color = rgba(0, 0, 0, 0.2))
//            .transition(Transition.Companion.all(0.3.s))
//    }
//
//    // HomePage Title Style (extracted from Index.kt inline style)
//    val HomePageTitle = CssStyle.base {
//        Modifier
//            .fontSize(2.5.cssRem)
//            .fontWeight(FontWeight.Bold)
//            .textShadow(2.px, 2.px, 4.px, rgba(0, 0, 0, 0.3))
//    }
//}
//
//object CustomAlertStyles {
//
//    val Overlay = CssStyle.base {
//        Modifier
//            .position(Position.Companion.Fixed)
//            .top(0.px)
//            .left(0.px)
//            .width(100.vw)
//            .height(100.vh)
//            .backgroundColor(rgba(0, 0, 0, 0.7))
//            .zIndex(1000)
//            .display(DisplayStyle.Flex)
//            .alignItems(AlignItems.Center)
//            .justifyContent(JustifyContent.Center)
//    }
//
//    val Dialog = CssStyle.base {
//        Modifier
//            .backgroundColor(ColorUtil.WhiteOp1)
//            .borderRadius(15.px)
//            .padding(2.cssRem)
//            .minWidth(300.px)
//            .maxWidth(400.px)
//            .boxShadow(0.px, 4.px, 16.px, color = rgba(0, 0, 0, 0.3))
//            .border(1.px, LineStyle.Solid, ColorUtil.WhiteOp2)
//    }
//
//    val Title = CssStyle.base {
//        Modifier
//            .fontSize(1.5.cssRem)
//            .fontWeight(FontWeight.Bold)
//            .color(Colors.White)
//            .textShadow(2.px, 2.px, 4.px, rgba(0, 0, 0, 0.3))
//    }
//
//    val Message = CssStyle.base {
//        Modifier
//            .fontSize(1.1.cssRem)
//            .color(Colors.White)
//            .textAlign(TextAlign.Center)
//            .lineHeight(1.4)
//            .opacity(0.9)
//    }
//
//    val CancelButton = CssStyle.base {
//        Modifier
//            .padding(0.8.cssRem)
//            .borderRadius(8.px)
//            .backgroundColor(ColorUtil.WhiteOp2)
//            .color(Colors.White)
//            .border(1.px, LineStyle.Solid, ColorUtil.WhiteOp3)
//            .fontWeight(FontWeight.Bold)
//    }
//
//    val ConfirmButton = CssStyle.base {
//        Modifier
//            .padding(0.8.cssRem)
//            .borderRadius(8.px)
//            .backgroundColor(Colors.DeepSkyBlue)
//            .color(Colors.White)
//            .fontWeight(FontWeight.Bold)
//    }
//}
//
//object FeedbackStyles {
//    val Title = CssStyle.base {
//        Modifier
//            .fontSize(1.2.cssRem)
//            .fontWeight(FontWeight.Bold)
//    }
//
//    val Message = CssStyle.base {
//        Modifier
//            .fontSize(1.1.cssRem)
//            .fontWeight(FontWeight.Bold)
//            .margin(1.cssRem, 0.px)
//    }
//
//    // Helper function for feedback colors
//    fun getFeedbackColor(isCorrect: Boolean?): CSSColorValue = when (isCorrect) {
//        true -> rgba(76, 175, 80, 1.0)
//        false -> rgba(244, 67, 54, 1.0)
//        null -> Colors.White
//    }
//}
//
//object GameModeStyles {
//
//    val LoadingText = CssStyle.base {
//        Modifier
//            .fontSize(0.9.cssRem)
//            .opacity(0.7)
//    }
//
//    val SelectorTitle = CssStyle.base {
//        Modifier.fontSize(1.1.cssRem)
//    }
//
//    val Button = CssStyle.base {
//        Modifier
//            .padding(0.6.cssRem, 0.5.cssRem)
//            .borderRadius(10.px)
//            .cursor(Cursor.Pointer)
//            .transition(Transition.all(0.2.s))
//            .minWidth(80.px)
//            .textAlign(TextAlign.Center)
//    }
//
//    fun getButtonBackground(isSelected: Boolean) =
//        Modifier.backgroundColor(if (isSelected) ColorUtil.WhiteOp3 else ColorUtil.WhiteOp1)
//            .let { mod ->
//                if (isSelected) mod.border(2.px, LineStyle.Solid, ColorUtil.WhiteOp5)
//                else mod.border(1.px, LineStyle.Solid, ColorUtil.WhiteOp2)
//            }
//            .fontWeight(if (isSelected) FontWeight.Bold else FontWeight.Normal)
//}
//
//object GameStatsStyles {
//    val Item = CssStyle.base {
//        Modifier
//            .background(ColorUtil.WhiteOp2)
//            .padding(0.5.cssRem, 1.cssRem)
//            .borderRadius(10.px)
//            .flexGrow(1)
//            .minWidth(100.px)
//            .margin(0.25.cssRem)
//    }
//
//    val Label = CssStyle.base {
//        Modifier
//            .fontSize(0.8.cssRem)
//            .opacity(0.8)
//    }
//
//    val Value = CssStyle.base {
//        Modifier
//            .fontSize(1.2.cssRem)
//            .fontWeight(FontWeight.Bold)
//    }
//}
//
//object LevelSelectorStyles {
//
//    val Button = CssStyle.base {
//        Modifier
//            .padding(0.5.cssRem, 0.4.cssRem)
//            .borderRadius(10.px)
//            .minWidth(55.px)
//            .textAlign(TextAlign.Center)
//            .cursor(Cursor.Pointer)
//            .transition(Transition.all(0.2.s))
//
//    }
//
//    fun getBackground(isSelected: Boolean) =
//        Modifier.backgroundColor(if (isSelected) ColorUtil.WhiteOp3 else ColorUtil.WhiteOp1)
//            .color(Color.white)
//            .let { mod ->
//                if (isSelected) mod.border(2.px, LineStyle.Solid, ColorUtil.WhiteOp5)
//                else mod.border(1.px, LineStyle.Solid, ColorUtil.WhiteOp2)
//            }
//            .fontWeight(if (isSelected) FontWeight.Bold else FontWeight.Normal)
//    val SelectorTitle = CssStyle.base {
//        Modifier.fontSize(1.1.cssRem)
//    }
//
//}
//
//object QuestionAreaStyles {
//
//    val Input = CssStyle.base {
//        Modifier
//            .background(ColorUtil.WhiteOp2)
//            .border(2.px, LineStyle.Solid, ColorUtil.WhiteOp3)
//            .borderRadius(10.px)
//            .padding(0.8.cssRem)
//            .fontSize(1.2.cssRem)
//            .color(Colors.White)
//            .textAlign(TextAlign.Center)
//            .width(100.percent)
//            .margin(1.cssRem, 0.px)
//    }
//    val AreaContainer = CssStyle.base {
//        Modifier
//            .fillMaxWidth()
//            .padding(2.cssRem)
//            .background(ColorUtil.WhiteOp1)
//            .borderRadius(15.px)
//            .minHeight(200.px)
//    }
//
//    val Prompt = CssStyle.base {
//        Modifier
//            .fontSize(1.2.cssRem)
//            .opacity(0.9)
//            .margin(1.cssRem, 0.px)
//    }
//
//    val Question = CssStyle.base {
//        Modifier
//            .fontSize(4.cssRem)
//            .fontWeight(FontWeight.Bold)
//            .margin(1.cssRem, 0.px)
//            .textShadow(3.px, 3.px, 6.px, rgba(0, 0, 0, 0.4))
//    }
//}
//
//object SpinnerStyles{
//
//    // Spinner Component Styles
//    val BaseStyle = CssStyle.base {
//        Modifier
//            .borderRadius(50.percent)
//            .animation(
//                SKeyframes.toAnimation(
//                    duration = 1.s,
//                    iterationCount = AnimationIterationCount.Companion.Infinite,
//                    timingFunction = AnimationTimingFunction.Companion.Linear
//                )
//            )
//    }
//
//    val SKeyframes = Keyframes {
//        0.percent {
//            Modifier.rotate(0.deg)
//        }
//        100.percent {
//            Modifier.rotate(360.deg)
//        }
//    }
//
//    val CenteredContainer = CssStyle.base {
//        Modifier
//            .fillMaxWidth()
//            .display(DisplayStyle.Flex)
//    }
//}