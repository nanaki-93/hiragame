package com.github.nanaki_93.components.sections

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.silk.components.forms.TextInput
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.toModifier
import com.github.nanaki_93.HiraganaCharStyle
import com.github.nanaki_93.InputStyle
import com.github.nanaki_93.components.widgets.SubmitButton
import com.github.nanaki_93.models.GameState
import com.varabyte.kobweb.compose.css.FontWeight
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.css.*

@Composable
fun QuestionArea(
    gameState: GameState,
    userInput: String,
    isAnswering: Boolean,
    onInputChange: (String) -> Unit,
    onSubmit: suspend () -> Unit,
) {
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
        gameState.currentChar?.let { char ->
            SpanText(char.char, HiraganaCharStyle.toModifier())

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


            // Feedback
            if (gameState.feedback.isNotEmpty()) {
                SpanText(
                    gameState.feedback,
                    Modifier
                        .fontSize(1.1.cssRem)
                        .fontWeight(FontWeight.Bold)
                        .color(
                            when (gameState.isCorrect) {
                                true -> rgba(76, 175, 80, 1.0)
                                false -> rgba(244, 67, 54, 1.0)
                                null -> Colors.White
                            }
                        )
                        .margin(1.cssRem, 0.px)
                )
            }
        }
    }
}