package com.github.nanaki_93.components.widgets.auth

import androidx.compose.runtime.*
import com.github.nanaki_93.components.styles.*
import com.github.nanaki_93.components.widgets.ActionButton
import com.github.nanaki_93.components.widgets.FormField
import com.github.nanaki_93.components.widgets.SpacedColumn
import com.varabyte.kobweb.silk.style.toModifier
import org.jetbrains.compose.web.css.cssRem

@Composable
fun LoginForm(
    name: String,
    password: String,
    isLogin: Boolean,
    isLoading: Boolean,
    errorMessage: String,
    onNameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    SpacedColumn(1.cssRem) {
        AuthStyles.Form.toModifier()

        FormField(name,onNameChange,"Name","Name")
        FormField(password, onPasswordChange,"Password", "Password", true,errorMessage)

        ActionButton(
            text = if (isLogin) "Login" else "Register",
            onClick = { onSubmit() },
            isLoading = isLoading,
            enabled = !isLoading,
        )
    }
}
