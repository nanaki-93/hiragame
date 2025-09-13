package com.github.nanaki_93.pages

import androidx.compose.runtime.*
import com.github.nanaki_93.CardStyle
import com.github.nanaki_93.GameContainerStyle
import com.github.nanaki_93.components.sections.QuestionArea
import com.github.nanaki_93.components.widgets.*
import com.github.nanaki_93.components.widgets.auth.LogoutButton
import com.github.nanaki_93.models.*
import com.github.nanaki_93.service.AuthService
import com.github.nanaki_93.service.GameService
import com.github.nanaki_93.util.launchSafe
import com.varabyte.kobweb.compose.css.*
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.Row
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


    var userId by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }


    val authService = remember { AuthService() }
    val gameService = remember { GameService(authService) }

    var gameState by remember { mutableStateOf(GameState.LOADING) }
    var gameStateUi by remember { mutableStateOf(GameStateUi(userId = userId, stats = GameStatisticsUi())) }
    var currentQuestion by remember { mutableStateOf(QuestionUi()) }
    var availableLevels by remember { mutableStateOf(listOf<Level>()) }


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
            userId = userId
        )

        gameStateUi = gameService.processAnswer(userQuestionDto)
        userInput = ""

        // Show feedback for 2 seconds
        delay(2000)

        // Get next question
        currentQuestion = gameService.getNextQuestion(SelectRequest(selectedGameMode!!, selectedLevel!!, userId))
        gameState = GameState.PLAYING
        isAnswering = false
    }

    suspend fun selectGameMode(mode: GameMode) {
        selectedGameMode = mode
        availableLevels = gameService.selectGameMode(LevelListRequest(mode, userId))
        gameState = GameState.LEVEL_SELECTION
    }

    suspend fun selectLevel(level: Level) {
        selectedLevel = level
        selectedGameMode?.let { gameMode ->
            currentQuestion = gameService.getNextQuestion(SelectRequest(gameMode, level, userId))
            gameState = GameState.PLAYING
        }

    }

    // Single LaunchedEffect for initialization
    LaunchedEffect(Unit) {
        // Step 1: Check JWT authentication
        if (!authService.isAuthenticated()) {
            kotlinx.browser.window.location.href = "/hiragame/login"
            return@LaunchedEffect
        }

        // Step 2: Get user data from JWT
        val userData = authService.getCurrentUser()

        userId = userData.userId
        username = userData.username

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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .gap(0.5.cssRem),
                horizontalArrangement = Arrangement.Center
            ) {
                LogoutButton {
                    coroutineScope.launchSafe {
                        authService.logout()
                        kotlinx.browser.window.location.href = "/hiragame/login"
                    }
                }
                // Title
                SpanText(
                    "ひらがな Master - $username",
                    Modifier
                        .fontSize(2.5.cssRem)
                        .fontWeight(FontWeight.Bold)
                        .textShadow(2.px, 2.px, 4.px, rgba(0, 0, 0, 0.3))
                )
            }

            Spinner(isVisible = gameState == GameState.LOADING)

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
                    coroutineScope.launchSafe { selectGameMode(mode) }
                }
            )

            LevelSelector(
                state = gameState,
                availableLevels = availableLevels,
                currentLevel = selectedLevel,
                onLevelSelected = { level ->
                    coroutineScope.launchSafe { selectLevel(level) }
                }
            )


            QuestionArea(
                state = gameState,
                currentQuestion = currentQuestion,
                userInput = userInput,
                isAnswering = isAnswering,
                onInputChange = { userInput = it },
                onSubmit = { coroutineScope.launchSafe { submitAnswer() } },
            )

            Feedback(state = gameState, gameStateUi = gameStateUi)

        }
    }
}