package com.github.nanaki_93.pages


import androidx.compose.runtime.*
import com.github.nanaki_93.CardStyle
import com.github.nanaki_93.GameContainerStyle
import com.github.nanaki_93.components.sections.QuestionArea
import com.github.nanaki_93.components.widgets.GameStats
import com.github.nanaki_93.components.widgets.ProgressBar
import com.github.nanaki_93.models.GameState
import com.github.nanaki_93.models.getNextCharacter
import com.github.nanaki_93.models.hiraganaCharsLv1
import com.github.nanaki_93.models.hiraganaLvMap
import com.github.nanaki_93.service.GameService
import com.varabyte.kobweb.compose.css.*
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.toModifier
import kotlinx.coroutines.delay
import org.jetbrains.compose.web.css.*



@Page
@Composable
fun HomePage() {
    var gameState by remember { mutableStateOf(GameState()) }
    var userInput by remember { mutableStateOf("") }
    var isAnswering by remember { mutableStateOf(false) }
    var level by remember { mutableStateOf(1) }

    val gameService = remember { GameService() }

    suspend fun submitAnswer() {
        if (isAnswering || gameState.currentChar == null) return

        isAnswering = true
        gameState = gameService.processAnswer(gameState, userInput,level)
        level =gameState.level
        userInput = ""
        delay(1500)
        gameState = gameService.getNextCharacterAndClearFeedback(gameState)
        isAnswering = false
    }


    // Initialize with first character
    LaunchedEffect(Unit) {
        gameState = gameState.copy(
            hiraganaList = hiraganaCharsLv1,
        ).getNextCharacter()
    }

    Box(GameContainerStyle.toModifier()) {
        Column(
            CardStyle.toModifier(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.cssRem)
        ) {
            // Title
            SpanText(
                "ひらがな Master",
                Modifier
                    .fontSize(2.5.cssRem)
                    .fontWeight(FontWeight.Bold)
                    .textShadow(2.px, 2.px, 4.px, rgba(0, 0, 0, 0.3))
            )

            GameStats(gameState)
            ProgressBar(gameState)
            QuestionArea(
                gameState = gameState,
                userInput = userInput,
                isAnswering = isAnswering,
                onInputChange = { userInput = it},
                onSubmit = {submitAnswer()},
                )

            // Instructions
            SpanText(
                "Level ${gameState.level}: Learning ${hiraganaCharsLv1.size} new characters each level",
                Modifier.fontSize(0.9.cssRem).opacity(0.7)
            )
        }
    }
}