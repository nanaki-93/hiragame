package com.github.nanaki_93.components.widgets.auth

import androidx.compose.runtime.Composable
import com.github.nanaki_93.components.styles.ColorUtil
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.silk.components.forms.Button
import com.varabyte.kobweb.silk.components.text.SpanText
import org.jetbrains.compose.web.css.*

@Composable
fun LogoutButton(onLogout: () -> Unit) {
    Button(
        onClick = { onLogout() },
        modifier = Modifier
            .backgroundColor(ColorUtil.WhiteOp1)
            .color(ColorUtil.White)
            .border(1.px, LineStyle.Solid, ColorUtil.WhiteOp3)
            .borderRadius(6.px)
            .padding(0.5.cssRem)
            .fontSize(0.8.cssRem)
    ) {
        SpanText("Logout")
    }
}