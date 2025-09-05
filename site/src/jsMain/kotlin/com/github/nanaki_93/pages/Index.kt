package com.github.nanaki_93.pages


import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.github.nanaki_93.CardStyle
import com.github.nanaki_93.GameContainerStyle
import com.github.nanaki_93.components.sections.QuestionArea
import com.github.nanaki_93.components.widgets.GameModeSelector
import com.github.nanaki_93.components.widgets.GameStats
import com.github.nanaki_93.components.widgets.LevelSelector
import com.github.nanaki_93.components.widgets.ProgressBar
import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.models.GameStateReq
import com.github.nanaki_93.models.Level
import com.github.nanaki_93.models.SelectRequest
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
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.css.*


@Page
@Composable
fun HomePage() {
    var gameStateReq by remember { mutableStateOf(GameStateReq()) }
    var availableLevels by remember { mutableStateOf(listOf<Level>()) }

    var userInput by remember { mutableStateOf("") }
    var isAnswering by remember { mutableStateOf(false) }
    var level by remember { mutableStateOf(Level.N5) }
    var gameMode by remember { mutableStateOf(GameMode.SIGN) }

    val gameService = remember { GameService() }

    val coroutineScope = rememberCoroutineScope()
    suspend fun submitAnswer() {
        if (isAnswering) return

        isAnswering = true
        gameStateReq = gameService.processAnswer(gameStateReq, userInput, level)
        level = Level.N5
        userInput = ""
        delay(1500)
        gameStateReq = gameService.getNextQuestion(gameStateReq)
        isAnswering = false
    }

    suspend fun selectLevel(selectedLevel: Level) {


        val next = gameService.getNextQuestion(SelectRequest(gameMode, selectedLevel, "user-1"))

    }

    // Initialize with first question
    LaunchedEffect(Unit) {
        gameStateReq = gameStateReq.copy()
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
            GameModeSelector(
                currentMode = gameMode,
                onModeSelected = { _ ->
                    coroutineScope.launch {
                        availableLevels = gameService.selectGameMode("user-1")
                    }
                }
            )


            LevelSelector(
                availableLevels = availableLevels,
                currentLevel = level,
                onLevelSelected = { selectedLevel -> coroutineScope.launch { selectLevel(selectedLevel) } }
            )

            GameStats(gameStateReq)
            ProgressBar(gameStateReq)
            QuestionArea(
                gameStateReq = gameStateReq,
                userInput = userInput,
                isAnswering = isAnswering,
                onInputChange = { userInput = it },
                onSubmit = { submitAnswer() },
            )

            // Instructions
            SpanText(
                "Level ${level.displayName}:  questions each level",
                Modifier.fontSize(0.9.cssRem).opacity(0.7)
            )
        }
    }
}