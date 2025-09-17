package com.github.nanaki_93.components.widgets

import androidx.compose.runtime.Composable
import com.github.nanaki_93.components.styles.*
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxWidth
import com.varabyte.kobweb.compose.ui.modifiers.flex
import com.varabyte.kobweb.compose.ui.modifiers.gap
import com.varabyte.kobweb.compose.ui.modifiers.onKeyDown
import com.varabyte.kobweb.silk.components.forms.Button
import com.varabyte.kobweb.silk.components.forms.TextInput
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.toModifier
import org.jetbrains.compose.web.attributes.AutoComplete
import org.jetbrains.compose.web.css.CSSLengthOrPercentageValue
import org.jetbrains.compose.web.css.cssRem

// Layout Components
@Composable
fun CenterRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().gap(0.5.cssRem),
        horizontalArrangement = Arrangement.Center
    ) {
        content()
    }
}

@Composable
fun SpacedRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(0.5.cssRem)
    ) {
        content()
    }
}

@Composable
fun CenterColumn(
    spacing: CSSLengthOrPercentageValue = 1.cssRem,
    content: @Composable () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        content()
    }
}

@Composable
fun SpacedColumn(
    spacing: CSSLengthOrPercentageValue = 1.cssRem,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        content()
    }
}

@Composable
fun ItemColumn(
    flexValue: Int = 1,
    content: @Composable () -> Unit
) {
    Column(
        Modifier.flex(flexValue).then(GameStatsStyles.Item.toModifier()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content()
    }
}

@Composable
fun ButtonRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

@Composable
fun CenteredButtonRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

// Button Components
@Composable
fun BaseButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = CommonStyles.Button.toModifier()
) {
    Button(
        onClick = { onClick() },
        modifier = modifier,
        enabled = enabled
    ) {
        SpanText(text)
    }
}

@Composable
fun StyledButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    style: Modifier = CommonStyles.Button.toModifier(),
    textStyle: Modifier = CommonStyles.ButtonText.toModifier()
) {
    Button(
        onClick = { onClick() },
        modifier = style,
        enabled = enabled
    ) {
        SpanText(text, textStyle)
    }
}

@Composable
fun ResetButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    style: Modifier = CustomAlertStyles.CancelButton.toModifier(),
    textStyle: Modifier = CommonStyles.ButtonText.toModifier()
) {
    Button(
        onClick = { onClick() },
        modifier = style,
        enabled = enabled
    ) {
        SpanText(text, textStyle)
    }
}

@Composable
fun ActionButton(
    text: String,
    loadingText: String = "Loading...",
    onClick: () -> Unit,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = CommonStyles.Button.toModifier()
) {
    Button(
        onClick = { onClick() },
        modifier = modifier,
        enabled = enabled && !isLoading
    ) {
        SpanText(if (isLoading) loadingText else text)
    }
}

// Text Components
@Composable
fun BaseText(
    text: String,
    modifier: Modifier = Modifier
) {
    SpanText(text, modifier)
}

@Composable
fun TitleText(
    text: String,
    modifier: Modifier = CommonStyles.HomePageTitle.toModifier()
) {
    SpanText(text, modifier)
}

@Composable
fun SubTitleText(
    text: String,
    modifier: Modifier = AuthStyles.HeaderSubtitle.toModifier()
) {
    SpanText(text, modifier)
}

@Composable
fun LabelText(
    text: String,
    modifier: Modifier = GameStatsStyles.Label.toModifier()
) {
    SpanText(text, modifier)
}

@Composable
fun ValueText(
    text: String,
    modifier: Modifier = GameStatsStyles.Value.toModifier()
) {
    SpanText(text, modifier)
}

@Composable
fun ErrorText(
    text: String,
    modifier: Modifier = AuthStyles.Error.toModifier()
) {
    SpanText("⚠️  $text", modifier)
}

// Input Components
@Composable
fun BaseTextInput(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    TextInput(
        text = text,
        onTextChange = onTextChange,
        placeholder = placeholder,
        modifier = modifier,
        enabled = enabled
    )
}

@Composable
fun StyledTextInput(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = AuthStyles.Input.toModifier(),
    password: Boolean = false,
    autoComplete: AutoComplete? = null,
    enabled: Boolean = true
) {
    TextInput(
        text = text,
        onTextChange = onTextChange,
        placeholder = placeholder,
        password = password,
        autoComplete = autoComplete,
        modifier = modifier,
        enabled = enabled
    )
}

@Composable
fun SearchableTextInput(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String = "",
    onEnterPressed: () -> Unit = {},
    modifier: Modifier = QuestionAreaStyles.Input.toModifier(),
    enabled: Boolean = true
) {
    TextInput(
        text = text,
        onTextChange = onTextChange,
        placeholder = placeholder,
        modifier = modifier.onKeyDown { keyboardEvent ->
            if (keyboardEvent.key == "Enter" && enabled && text.isNotEmpty()) {
                keyboardEvent.preventDefault()
                onEnterPressed()
            }
        },
        enabled = enabled
    )
}

// Form Components
@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "",
    placeholder: String = "",
    isPassword: Boolean = false,
    errorMessage: String = "",
    modifier: Modifier = AuthStyles.Input.toModifier()
) {
    if (label.isNotEmpty()) {
        LabelText(label)
    }

    StyledTextInput(
        text = value,
        onTextChange = onValueChange,
        placeholder = placeholder,
        password = isPassword,
        autoComplete = if (isPassword) AutoComplete.currentPassword else AutoComplete.username,
        modifier = modifier
    )

    if (errorMessage.isNotEmpty()) {
        ErrorText(errorMessage)
    }
}