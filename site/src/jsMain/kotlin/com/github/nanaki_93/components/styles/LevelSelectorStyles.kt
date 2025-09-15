package com.github.nanaki_93.components.styles

import com.varabyte.kobweb.compose.css.Cursor
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.TextAlign
import com.varabyte.kobweb.compose.css.Transition
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.border
import com.varabyte.kobweb.compose.ui.modifiers.borderRadius
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.cursor
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.minWidth
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.textAlign
import com.varabyte.kobweb.compose.ui.modifiers.transition
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.base
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.s

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
        Modifier.backgroundColor(if (isSelected) ColorUtil.WhiteOp3 else ColorUtil.WhiteOp1)
            .color(Color.white)
            .let { mod ->
                if (isSelected) mod.border(2.px, LineStyle.Solid, ColorUtil.WhiteOp5)
                else mod.border(1.px, LineStyle.Solid, ColorUtil.WhiteOp2)
            }
            .fontWeight(if (isSelected) FontWeight.Bold else FontWeight.Normal)
    val SelectorTitle = CssStyle.base {
        Modifier.fontSize(1.1.cssRem)
    }

}