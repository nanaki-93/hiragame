package com.github.nanaki_93.components.widgets

import androidx.compose.runtime.Composable
import com.github.nanaki_93.components.styles.*
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.color
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
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

@Composable
fun SpacedRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(0.5.cssRem)
    ) { content() }
}

@Composable
fun CenterColumn(
    spacing: CSSLengthOrPercentageValue = 1.cssRem,
    modifier: Modifier? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier?:Modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) { content() }
}

@Composable
fun SpacedColumn(
    spacing: CSSLengthOrPercentageValue = 1.cssRem,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) { content() }
}

@Composable
fun ItemColumn(
    flexValue: Int = 1,
    content: @Composable () -> Unit
) {
    Column(
        Modifier.flex(flexValue).then(Styles.StatItem.toModifier()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) { content() }
}


@Composable
fun CenteredButtonRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

// Button Components - Simplified with clear naming
@Composable
fun BaseButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Styles.ButtonLink.toModifier()
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
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Button(
        onClick = {onClick()},
        modifier = Styles.ButtonPrimary.toModifier(),
        enabled = enabled
    ) {
        SpanText(text)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Button(
        onClick ={onClick()},
        modifier = Styles.ButtonSecondary.toModifier(),
        enabled = enabled
    ) {
        SpanText(text)
    }
}

// Simplified Action Button
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    enabled: Boolean = true
) {
    Button(
        onClick = {onClick()},
        modifier = Styles.ButtonPrimary.toModifier(),
        enabled = enabled && !isLoading
    ) {
        if (isLoading) {
            LoadingSpinner(size = SpinnerSize.Small, color = SpinnerColor.White)
        } else {
            SpanText(text)
        }
    }
}

// Text Components - Simplified and consistent
@Composable
fun BaseText(text: String, modifier: Modifier = Modifier) {
    SpanText(text, modifier)
}

@Composable
fun TitleText(text: String) {
    SpanText(text, Styles.Title.toModifier())
}

@Composable
fun SubTitleText(text: String) {
    SpanText(text, Styles.Subtitle.toModifier())
}

@Composable
fun LabelText(text: String) {
    SpanText(text, Styles.Label.toModifier())
}

@Composable
fun ValueText(text: String) {
    SpanText(text, Styles.Value.toModifier())
}

@Composable
fun ErrorText(text: String) {
    SpanText("⚠️  $text", Styles.Error.toModifier())
}

@Composable
fun PromptText(text: String) {
    SpanText(text, Styles.Prompt.toModifier())
}

@Composable
fun QuestionText(text: String) {
    SpanText(text, Styles.Question.toModifier())
}
@Composable
fun LoadingText(text: String) {
    SpanText(text,Styles.LoadingText.toModifier())
}
@Composable
fun FeedbackText(text: String,isCorrect : Boolean?) {
    SpanText(text,Styles.FeedbackMessage.toModifier().color(StyleHelpers.getFeedbackColor(isCorrect)))
}

// Input Components - Simplified
@Composable
fun BaseTextInput(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String = "",
    enabled: Boolean = true,
    isLarge: Boolean = false
) {
    val style = if (isLarge) Styles.InputLarge else Styles.Input
    TextInput(
        text = text,
        onTextChange = onTextChange,
        placeholder = placeholder,
        modifier = style.toModifier(),
        enabled = enabled
    )
}

@Composable
fun StyledTextInput(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String = "",
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
        modifier = Styles.Input.toModifier(),
        enabled = enabled
    )
}

@Composable
fun SearchableTextInput(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String = "",
    onEnterPressed: () -> Unit = {},
    enabled: Boolean = true
) {
    TextInput(
        text = text,
        onTextChange = onTextChange,
        placeholder = placeholder,
        modifier = Styles.InputLarge.toModifier().onKeyDown { keyboardEvent ->
            if (keyboardEvent.key == "Enter" && enabled && text.isNotEmpty()) {
                keyboardEvent.preventDefault()
                onEnterPressed()
            }
        },
        enabled = enabled
    )
}

// Form Components - Simplified
@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "",
    placeholder: String = "",
    isPassword: Boolean = false,
    errorMessage: String = ""
) {
    if (label.isNotEmpty()) {
        LabelText(label)
    }

    StyledTextInput(
        text = value,
        onTextChange = onValueChange,
        placeholder = placeholder,
        password = isPassword,
        autoComplete = if (isPassword) AutoComplete.currentPassword else AutoComplete.username
    )

    if (errorMessage.isNotEmpty()) {
        ErrorText(errorMessage)
    }
}

@Composable
fun StatItem(label: String, value: String) {
    ItemColumn {
        LabelText(label)
        ValueText(value)
    }
}