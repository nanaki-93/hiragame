package com.github.nanaki_93.components.sections

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.silk.components.forms.TextInput
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.toModifier
import com.github.nanaki_93.HiraganaCharStyle
import com.github.nanaki_93.InputStyle
import com.github.nanaki_93.components.widgets.SubmitButton
import com.github.nanaki_93.models.GameState
import com.github.nanaki_93.models.QuestionUi
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.css.*

@Composable
fun QuestionArea(
    state: GameState,
    currentQuestion: QuestionUi,
    userInput: String,
    isAnswering: Boolean,
    onInputChange: (String) -> Unit,
    onSubmit: suspend () -> Unit,
) {
    if (state != GameState.PLAYING && state != GameState.SHOWING_FEEDBACK) return
    val coroutineScope = rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(2.cssRem)
            .background(rgba(255, 255, 255, 0.1))
            .borderRadius(15.px)
            .minHeight(200.px),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        SpanText(currentQuestion.japanese, HiraganaCharStyle.toModifier())

        SpanText(
            "What is the romanization?",
            Modifier.fontSize(1.2.cssRem).opacity(0.9).margin(1.cssRem, 0.px)
        )

        TextInput(
            text = userInput,
            onTextChange = onInputChange,
            modifier = InputStyle.toModifier()
                .onKeyDown { keyboardEvent ->
                    if (keyboardEvent.key == "Enter" && !isAnswering) {
                        keyboardEvent.preventDefault()
                        coroutineScope.launch { onSubmit() }
                    }
                },
            placeholder = "Type romanization here..."
        )

        SubmitButton(
            onClick = { coroutineScope.launch { onSubmit() } },
            isAnswering = isAnswering,
            userInput = userInput
        )

    }
}