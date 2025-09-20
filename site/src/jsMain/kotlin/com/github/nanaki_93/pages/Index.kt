package com.github.nanaki_93.pages

import androidx.compose.runtime.*
import com.github.nanaki_93.components.styles.Styles
import com.github.nanaki_93.components.widgets.*
import com.github.nanaki_93.models.*
import com.github.nanaki_93.service.AuthService
import com.github.nanaki_93.service.GameService
import com.github.nanaki_93.service.SessionManager
import com.github.nanaki_93.util.launchSafe
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxWidth
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.silk.style.toModifier
import kotlinx.coroutines.delay
import org.jetbrains.compose.web.css.cssRem


@Page
@Composable
fun HomePage() {


    var userId by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }


    val authService = remember { AuthService() }
    val gameService = remember { GameService(authService) }

    var gameState by remember { mutableStateOf(GameState.LOADING) }
    var gameStateUi by remember { mutableStateOf(GameStateUi(userId = userId, stats = GameStatisticsUi())) }
    var currentQuestion by remember { mutableStateOf(QuestionDto()) }
    var availableLevels by remember { mutableStateOf(listOf<Level>()) }


    var userInput by remember { mutableStateOf("") }
    var isAnswering by remember { mutableStateOf(false) }
    var selectedLevel by remember { mutableStateOf(null as Level?) }
    var selectedGameMode by remember { mutableStateOf(null as GameMode?) }
    var showAlert by remember { mutableStateOf(false) }


    val coroutineScope = rememberCoroutineScope()


    SessionManager.onSessionExpired = {
        println("Session expired")
        showAlert = true
    }
    suspend fun submitAnswer() {
        if (isAnswering) return

        isAnswering = true
        gameState = GameState.SHOWING_FEEDBACK

        val userQuestionDto = UserQuestionDto(
            questionId = currentQuestion.id ?: "",
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


    Box(Styles.GameContainer.toModifier()) {
        Column(
            modifier = Styles.Card.toModifier(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.cssRem)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,

                ) {
                BaseButton(
                    text = "Logout",
                    onClick = {
                        coroutineScope.launchSafe {
                            authService.logout()
                            kotlinx.browser.window.location.href = "/hiragame/login"
                        }
                    }
                )
            }
            CenterRow {
                TitleText("ひらがな Master - $username")
            }


            Spinner(isVisible = gameState == GameState.LOADING)




            if (selectedGameMode != null) {
                ModeItemRow("Game Mode:", selectedGameMode?.displayName)
            }
            if (selectedLevel != null) {
                ModeItemRow("Level:", selectedLevel?.displayName)
            }


            if (gameState != GameState.LOADING) {

                SpacedRow {
                    StatItem("Correct ", "${gameStateUi.stats.correctAnswers}")
                    StatItem("Attempts", "${gameStateUi.stats.totalAnswered}")
                    StatItem("Streak", "${gameStateUi.stats.streak}")
                }
            }

            if (gameState == GameState.LOADING) {
                LoadingText("Game mode selection will appear shortly...")
            }


            // Only show selector when in mode selection state
            if (gameState == GameState.MODE_SELECTION) {

                SubTitleText("Select a game mode to continue:")
                CenterRow {
                    GameMode.entries.forEach { mode ->
                        PrimaryButton(
                            text = mode.displayName,
                            onClick = { coroutineScope.launchSafe { selectGameMode(mode) } },
                        )
                    }
                }
            }



            if (gameState == GameState.LEVEL_SELECTION) {

                SubTitleText("Select your level:")
                CenterRow {
                    for (level in availableLevels) {
                        PrimaryButton(
                            text = "Lv-${level.displayName}",
                            onClick = {
                                coroutineScope.launchSafe { selectLevel(level) }
                            },
                        )
                    }
                }
            }


            if (gameState == GameState.PLAYING) {

                CenterColumn(
                    0.cssRem,
                    Styles.QuestionCard.toModifier(),
                ) {

                    QuestionText(currentQuestion.japanese)
                    PromptText("What is the romanization?")
                    SearchableTextInput(
                        text = userInput,
                        onTextChange = { userInput = it },
                        onEnterPressed = { coroutineScope.launchSafe { submitAnswer() } },
                        placeholder = "Type romanization here..."
                    )

                    ActionButton(
                        text = "Submit",
                        isLoading = isAnswering,
                        onClick = { coroutineScope.launchSafe { submitAnswer() } },
                        enabled = userInput.isNotEmpty() && !isAnswering
                    )
                }
            }

            if (gameState == GameState.SHOWING_FEEDBACK) {

                CenterColumn(
                    0.cssRem,
                    Styles.QuestionCard.toModifier(),
                ) {
                    CenterRow {
                        Spinner(isVisible = gameState == GameState.SHOWING_FEEDBACK, size = SpinnerSize.Large)
                    }
                    CenterRow {
                        FeedbackText(gameStateUi.feedback, gameStateUi.isCorrect)
                    }
                }
            }

        }
        if (showAlert) {
            SessionExpiredAlert(
                message = "Your session has expired.",
                onClose = {
                    coroutineScope.launchSafe {
                        authService.logout()
                        kotlinx.browser.window.location.href = "/hiragame/login"
                        showAlert = false
                    }
                }
            )
        }
    }
}