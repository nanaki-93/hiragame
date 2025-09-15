package com.github.nanaki_93.components.widgets

import androidx.compose.runtime.*
import com.varabyte.kobweb.silk.components.forms.Button
import com.varabyte.kobweb.silk.components.text.SpanText
import com.github.nanaki_93.components.styles.CommonStyles
import com.varabyte.kobweb.silk.style.toModifier

@Composable
fun SubmitButton(
    onClick: () -> Unit,
    isAnswering: Boolean,
    userInput: String
) {
    Button(
        onClick = { onClick() },
        modifier = CommonStyles.Button.toModifier(),
        enabled = userInput.isNotEmpty() && !isAnswering
    ) {
        SpanText(if (isAnswering) "Checking..." else "Submit")
    }
}