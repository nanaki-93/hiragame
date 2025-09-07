package com.github.nanaki_93.components.widgets.auth

import androidx.compose.runtime.Composable
import com.github.nanaki_93.service.TokenManager
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.silk.components.forms.Button
import com.varabyte.kobweb.silk.components.text.SpanText
import org.jetbrains.compose.web.css.*

@Composable
fun LogoutButton() {
    Button(
        onClick = {
            TokenManager.clearToken()
            kotlinx.browser.window.location.href = "/login"
        },
        modifier = Modifier
            .backgroundColor(rgba(255, 255, 255, 0.1))
            .color(Colors.White)
            .border(1.px, LineStyle.Solid, rgba(255, 255, 255, 0.3))
            .borderRadius(6.px)
            .padding(0.5.cssRem)
            .fontSize(0.8.cssRem)
    ) {
        SpanText("Logout")
    }
}