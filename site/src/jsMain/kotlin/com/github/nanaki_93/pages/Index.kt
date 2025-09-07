package com.github.nanaki_93.pages

import androidx.compose.runtime.*
import com.github.nanaki_93.CardStyle
import com.github.nanaki_93.GameContainerStyle
import com.github.nanaki_93.components.sections.QuestionArea
import com.github.nanaki_93.components.widgets.*
import com.github.nanaki_93.models.*
import com.github.nanaki_93.service.GameService
import com.github.nanaki_93.service.TokenManager
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

const val USER_ID = "2a456b71-d756-482d-b01a-d987b3a833bf"


@Page
@Composable
fun HomePage() {




    val gameService = remember { GameService() }
    var gameState by remember { mutableStateOf(GameState.LOADING) }
    var gameStateUi by remember { mutableStateOf(GameStateUi(userId = USER_ID, stats = GameStatisticsUi())) }
    var currentQuestion by remember { mutableStateOf(QuestionUi()) }
    var availableLevels by remember { mutableStateOf(listOf<Level>()) }
    var userId by remember { mutableStateOf("") }

    var userInput by remember { mutableStateOf("") }
    var isAnswering by remember { mutableStateOf(false) }
    var selectedLevel by remember { mutableStateOf(null as Level?) }
    var selectedGameMode by remember { mutableStateOf(null as GameMode?) }

    val coroutineScope = rememberCoroutineScope()

    suspend fun submitAnswer() {
        if (isAnswering) return

        isAnswering = true
        gameState = GameState.SHOWING_FEEDBACK

        val userQuestionDto = UserQuestionDto(
            userQuestionId = currentQuestion.id ?: "",
            japanese = currentQuestion.japanese,
            romanization = currentQuestion.romanization,
            translation = currentQuestion.translation,
            topic = currentQuestion.topic,
            level = selectedLevel!!,
            gameMode = selectedGameMode!!,
            hasKatakana = currentQuestion.hasKatakana,
            hasKanji = currentQuestion.hasKanji,
            userInput = userInput,
            userId = USER_ID
        )

        gameStateUi = gameService.processAnswer(userQuestionDto)
        userInput = ""

        // Show feedback for 2 seconds
        delay(2000)

        // Get next question
        currentQuestion = gameService.getNextQuestion(SelectRequest(selectedGameMode!!, selectedLevel!!, USER_ID))
        gameState = GameState.PLAYING
        isAnswering = false
    }

    suspend fun selectGameMode(mode: GameMode) {
        selectedGameMode = mode
        availableLevels = gameService.selectGameMode(LevelListRequest(mode, USER_ID))
        gameState = GameState.LEVEL_SELECTION
    }

    suspend fun selectLevel(level: Level) {
        selectedLevel = level
        selectedGameMode?.let {gameMode->
            currentQuestion = gameService.getNextQuestion(SelectRequest(gameMode, level, USER_ID))
            gameState = GameState.PLAYING
        }

    }

    // Single LaunchedEffect for initialization
    LaunchedEffect(Unit) {
        // Step 1: Check JWT authentication
        if (!TokenManager.isTokenValid()) {
            kotlinx.browser.window.location.href = "/login"
            return@LaunchedEffect
        }

        // Step 2: Get user data from JWT
        val userData = TokenManager.getUserData()
        if (userData == null) {
            kotlinx.browser.window.location.href = "/login"
            return@LaunchedEffect
        }

        userId = userData.userId

        // Step 3: Initialize game state
        try {
            gameStateUi = gameService.getGameState(userId)
            gameState = GameState.LOADING

            // Step 4: Show loading for 2 seconds then proceed to mode selection
            delay(2000)
            gameState = GameState.MODE_SELECTION
        } catch (e: Exception) {
            console.error("Failed to initialize game:", e)
            // Handle error appropriately
        }
    }

    // Early return if user is not authenticated
    if (userId.isEmpty()) return


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

            Spinner(isVisible = gameState== GameState.LOADING)

            GameStats(
                state = gameState,
                gameMode = selectedGameMode,
                level = selectedLevel,
                statsUi = gameStateUi.stats
            )

            GameModeSelector(
                state = gameState,
                currentMode = selectedGameMode,
                onModeSelected = { mode ->
                    coroutineScope.launch { selectGameMode(mode) }
                }
            )

            LevelSelector(
                state = gameState,
                selectedGameMode = selectedGameMode,
                availableLevels = availableLevels,
                currentLevel = selectedLevel,
                onLevelSelected = { level ->
                    coroutineScope.launch { selectLevel(level) }
                }
            )

            SpanText(
                "Level ${selectedLevel?.displayName?:""} - ${selectedGameMode?.displayName?:""} Mode",
                Modifier.fontSize(0.9.cssRem).opacity(0.7)
            )


            QuestionArea(
                state = gameState,
                currentQuestion = currentQuestion,
                userInput = userInput,
                isAnswering = isAnswering,
                onInputChange = { userInput = it },
                onSubmit = { coroutineScope.launch { submitAnswer() } },
            )

            Feedback(state = gameState,gameStateUi = gameStateUi )


        }
    }
}