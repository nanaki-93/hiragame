package com.github.nanaki_93.pages

import androidx.compose.runtime.Composable
import com.github.nanaki_93.components.styles.Styles
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.silk.style.toModifier
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.Main
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

/** Compatibility route for old login links; local practice never requires an account. */
@Page("/login")
@Composable
fun LoginPage() {
    Main {
        Box(Styles.GameContainer.toModifier()) {
            Column(modifier = Styles.Card.toModifier()) {
                H1 { Text("No account required") }
                P { A(href = "/hiragame/") { Text("Go to Home to practise locally") } }
            }
        }
    }
}
