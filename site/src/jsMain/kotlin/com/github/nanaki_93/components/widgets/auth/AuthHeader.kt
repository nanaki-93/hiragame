package com.github.nanaki_93.components.widgets.auth

import androidx.compose.runtime.Composable
import com.github.nanaki_93.components.styles.AuthHeaderSubtitleStyle
import com.github.nanaki_93.components.styles.AuthHeaderTitleStyle
import com.github.nanaki_93.components.widgets.CenterColumn
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.toModifier
import org.jetbrains.compose.web.css.cssRem

@Composable
fun AuthHeader(isLogin: Boolean) {
    CenterColumn(1.cssRem) {
        SpanText(
            "ひらがな Master",
            AuthHeaderTitleStyle.toModifier()
        )

        SpanText(
            if (isLogin) "Welcome Back!" else "Create Account",
            AuthHeaderSubtitleStyle.toModifier()
        )
    }
}