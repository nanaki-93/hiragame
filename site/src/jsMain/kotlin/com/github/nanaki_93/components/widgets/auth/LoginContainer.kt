package com.github.nanaki_93.components.widgets.auth

import androidx.compose.runtime.Composable
import com.github.nanaki_93.components.styles.AuthContainerStyle
import com.github.nanaki_93.components.styles.GameContainerStyle
import com.github.nanaki_93.components.styles.CardStyle
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.silk.style.toModifier
import org.jetbrains.compose.web.css.cssRem

@Composable
fun LoginContainer(content: @Composable () -> Unit) {
    Box(GameContainerStyle.toModifier()) {
        Column(
            CardStyle.toModifier().then(AuthContainerStyle.toModifier()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.5.cssRem)
        ) {
            content()
        }
    }
}