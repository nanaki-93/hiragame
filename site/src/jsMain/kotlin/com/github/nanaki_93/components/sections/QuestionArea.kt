package com.github.nanaki_93.components.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.github.nanaki_93.components.styles.*
import com.github.nanaki_93.components.widgets.ActionButton
import com.github.nanaki_93.components.widgets.SearchableTextInput
import com.github.nanaki_93.models.GameState
import com.github.nanaki_93.models.QuestionUi
import com.github.nanaki_93.util.launchSafe
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.toModifier

@Composable
fun QuestionArea(
    state: GameState,
    currentQuestion: QuestionUi,
    userInput: String,
    isAnswering: Boolean,
    onInputChange: (String) -> Unit,
    onSubmit: suspend () -> Unit,
) {
    if (state != GameState.PLAYING ) return
    val coroutineScope = rememberCoroutineScope()

    Column(
        QuestionAreaStyles.AreaContainer.toModifier(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        SpanText(currentQuestion.japanese, QuestionAreaStyles.Question.toModifier())
        SpanText("What is the romanization?", QuestionAreaStyles.Prompt.toModifier())
        SearchableTextInput(
            text = userInput,
            onTextChange = onInputChange,
            onEnterPressed = { coroutineScope.launchSafe { onSubmit() } },
            placeholder = "Type romanization here..."
        )



        ActionButton(
            text = "Submit",
            isLoading = isAnswering,
            loadingText = "Checking...",
            onClick = { coroutineScope.launchSafe { onSubmit() } },
            enabled = userInput.isNotEmpty() && !isAnswering
        )
    }
}