package com.github.nanaki_93.components.widgets.auth

import androidx.compose.runtime.*
import com.github.nanaki_93.components.styles.*
import com.github.nanaki_93.components.widgets.SpacedColumn
import com.varabyte.kobweb.silk.components.forms.Button
import com.varabyte.kobweb.silk.components.forms.TextInput
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.toModifier
import org.jetbrains.compose.web.attributes.AutoComplete
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
        AuthFormStyle.toModifier()

        InputForm(name, "Name", onNameChange)
        InputForm(password, "Password", onPasswordChange, true)

        if (errorMessage.isNotEmpty()) {
            SpanText(
                "\u26A0\uFE0F  $errorMessage", // ⚠️ icon
                modifier = AuthErrorStyle
                    .toModifier()
            )
        }

        Button(
            onClick = { onSubmit() },
            modifier = AuthSubmitButtonStyle.toModifier(),
            enabled = !isLoading
        ) {
            SpanText(
                if (isLoading) "Loading..." else if (isLogin) "Login" else "Register"
            )
        }
    }
}

@Composable
fun InputForm(
    inputVal: String,
    inputPlaceholder: String = "",
    onInputChange: (String) -> Unit,
    isPassword: Boolean = false,
) {
    TextInput(
        text = inputVal,
        onTextChange = onInputChange,
        placeholder = inputPlaceholder,
        password = isPassword,
        autoComplete = if (isPassword) AutoComplete.currentPassword else AutoComplete.username,
        modifier = AuthInputStyle.toModifier()
    )
}