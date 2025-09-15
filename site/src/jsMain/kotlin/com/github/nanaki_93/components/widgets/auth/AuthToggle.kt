package com.github.nanaki_93.components.widgets.auth

import androidx.compose.runtime.Composable
import com.github.nanaki_93.components.styles.AuthStyles

import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.silk.components.forms.Button
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.toModifier

@Composable
fun AuthToggle(
    isLogin: Boolean,
    onToggle: () -> Unit
) {
    Row(
        AuthStyles.ToggleContainer.toModifier(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpanText(
            if (isLogin) "Don't have an account? " else "Already have an account? ",
            AuthStyles.ToggleText.toModifier()
        )
        Button(
            onClick = { onToggle() },
            modifier = AuthStyles.ToggleButton.toModifier()
        ) {
            SpanText(if (isLogin) "Register" else "Login")
        }
    }
}