package com.github.nanaki_93.components.widgets.auth

import androidx.compose.runtime.Composable
import com.github.nanaki_93.components.styles.CommonStyles
import com.varabyte.kobweb.silk.components.forms.Button
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.toModifier

@Composable
fun LogoutButton(onLogout: () -> Unit) {
    Button(
        onClick = { onLogout() },
        modifier = CommonStyles.Button.toModifier()
    ) {
        SpanText("Logout")
    }
}