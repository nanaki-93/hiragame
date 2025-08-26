package com.github.nanaki_93.components.widgets

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.css.Cursor
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.TextAlign
import com.varabyte.kobweb.compose.css.Transition
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.silk.components.forms.Button
import com.varabyte.kobweb.silk.components.text.SpanText
import org.jetbrains.compose.web.css.*

@Composable
fun LevelSelector(
    currentLevel: Int = 1,
    onLevelSelected: (Int) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .gap(0.5.cssRem),
        horizontalArrangement = Arrangement.Center
    ) {
        for (level in 1..5) {
            Button(
                onClick = { onLevelSelected(level) },
                modifier = Modifier
                    .padding(0.5.cssRem, 0.4.cssRem)
                    .borderRadius(10.px)
                    .backgroundColor(
                        if (currentLevel == level) {
                            rgba(255, 255, 255, 0.3)
                        } else {
                            rgba(255, 255, 255, 0.1)
                        }
                    )
                    .color(Color.white)
                    .let { mod ->
                        if (currentLevel == level) {
                            mod.border(2.px, LineStyle.Solid, rgba(255, 255, 255, 0.5))
                        } else {
                            mod.border(1.px, LineStyle.Solid, rgba(255, 255, 255, 0.2))
                        }
                    }
                    .cursor(Cursor.Pointer)
                    .transition(Transition.all(0.2.s))
                    .minWidth(55.px)
                    .textAlign(TextAlign.Center)
                    .fontWeight(if (currentLevel == level) FontWeight.Bold else FontWeight.Normal)

            ) {
                SpanText(
                    "Lv$level",
                    modifier = Modifier.color(Color.white)
                )

            }
        }
    }
}