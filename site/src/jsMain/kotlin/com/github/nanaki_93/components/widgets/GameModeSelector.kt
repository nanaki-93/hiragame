
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

enum class GameMode(val displayName: String) {
    SIGN("Sign"),
    WORD("Word"),
    SENTENCE("Sentence")
}

@Composable
fun GameModeSelector(
    currentMode: GameMode = GameMode.SIGN,
    onModeSelected: (GameMode) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .gap(0.5.cssRem),
        horizontalArrangement = Arrangement.Center
    ) {
        GameMode.entries.forEach { mode ->
            Button(
                onClick = { onModeSelected(mode) },
                modifier = Modifier
                    .padding(0.6.cssRem, 0.5.cssRem)
                    .borderRadius(10.px)
                    .backgroundColor(
                        if (currentMode == mode) {
                            rgba(255, 255, 255, 0.3)
                        } else {
                            rgba(255, 255, 255, 0.1)
                        }
                    )
                    .let { mod ->
                        if (currentMode == mode) {
                            mod.border(2.px, LineStyle.Solid, rgba(255, 255, 255, 0.5))
                        } else {
                            mod.border(1.px, LineStyle.Solid, rgba(255, 255, 255, 0.2))
                        }
                    }
                    .cursor(Cursor.Pointer)
                    .transition(Transition.all(0.2.s))
                    .minWidth(80.px)
                    .textAlign(TextAlign.Center)
                    .fontWeight(if (currentMode == mode) FontWeight.Bold else FontWeight.Normal)

            ) {
                SpanText(
                    mode.displayName,
                    modifier = Modifier.color(Color.white)
                )
            }
        }
    }
}