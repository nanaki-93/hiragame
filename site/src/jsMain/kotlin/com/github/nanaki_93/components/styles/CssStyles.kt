package com.github.nanaki_93.components.styles

import com.varabyte.kobweb.compose.css.Cursor
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.TextAlign
import com.varabyte.kobweb.compose.css.Transition
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.background
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.border
import com.varabyte.kobweb.compose.ui.modifiers.borderRadius
import com.varabyte.kobweb.compose.ui.modifiers.boxShadow
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.cursor
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxWidth
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.maxWidth
import com.varabyte.kobweb.compose.ui.modifiers.minWidth
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.textAlign
import com.varabyte.kobweb.compose.ui.modifiers.transition
import com.varabyte.kobweb.compose.ui.modifiers.width
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.base
import org.jetbrains.compose.web.css.CSSColorValue
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.s


// In a new object or a common part of your styles file
object ReusableModifiers {
    fun buttonBase() = Modifier
        .padding(0.8.cssRem)
        .borderRadius(8.px)
        .fontWeight(FontWeight.Bold)
        .cursor(Cursor.Pointer)
        .transition(Transition.all(0.3.s))

    fun cardBase() = Modifier
        .background(ColorPalette.CardBackground)
        .borderRadius(15.px)
        .padding(2.cssRem)
        .boxShadow(0.px, 4.px, 15.px, color = rgba(0, 0, 0, 0.1))
}

// Then, in your existing styles, you can reuse it
val SubmitButton = CssStyle.base {
    ReusableModifiers.buttonBase()
        .fillMaxWidth()
        .backgroundColor(ColorPalette.Accent)
        .color(Colors.White)
}

val ConfirmButton = CssStyle.base {
    ReusableModifiers.buttonBase()
        .padding(0.8.cssRem) // Override padding if needed
        .backgroundColor(ColorPalette.Accent)
        .color(Colors.White)
}

// Usage
val Card = CssStyle.base {
    ReusableModifiers.cardBase()
        .maxWidth(800.px)
        .width(90.percent)
        .textAlign(TextAlign.Center)
        .color(ColorPalette.Text)
}

val Dialog = CssStyle.base {
    ReusableModifiers.cardBase()
        .minWidth(300.px)
        .maxWidth(400.px)
        .boxShadow(0.px, 4.px, 16.px, color = rgba(0, 0, 0, 0.2)) // Override shadow
}

// Create a new, more generic function
fun getSelectionStyle(
    isSelected: Boolean,
    selectedColor: CSSColorValue,
    borderColor: CSSColorValue
): Modifier {
    return Modifier
        .backgroundColor(if (isSelected) selectedColor else Colors.White)
        .color(if (isSelected) Colors.White else ColorPalette.Text)
        .let { mod ->
            if (isSelected) mod.border(2.px, LineStyle.Solid, selectedColor)
            else mod.border(1.px, LineStyle.Solid, borderColor)
        }
        .fontWeight(if (isSelected) FontWeight.Bold else FontWeight.Normal)
}

// Then update the existing functions to call this new one
fun getButtonBackground(isSelected: Boolean) = getSelectionStyle(
    isSelected,
    ColorPalette.SecondaryAccent,
    ColorPalette.Border
)

fun getBackground(isSelected: Boolean) = getSelectionStyle(
    isSelected,
    ColorPalette.SecondaryAccent,
    ColorPalette.Border
)