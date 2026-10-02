package com.github.nanaki_93.pages

import androidx.compose.runtime.Composable
import com.github.nanaki_93.components.styles.Styles
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.silk.components.navigation.Link
import com.varabyte.kobweb.silk.style.toModifier
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.Main
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

/** F06 catalog shell. The validated catalog, progress projection and preview follow in later steps. */
@Page("/topics")
@Composable
fun TopicsPage() {
    Main {
        Box(Styles.GameContainer.toModifier()) {
            Column(modifier = Styles.Card.toModifier()) {
                H1 { Text("Topics / Learn") }
                P { Text("Workplace lesson browsing is being prepared. Practice and local save controls remain on Home.") }
                Link(path = "/") { Text("Back to Home") }
            }
        }
    }
}
