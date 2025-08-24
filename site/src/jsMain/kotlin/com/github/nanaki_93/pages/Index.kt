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
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.silk.components.forms.Button
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.toModifier
import kotlinx.coroutines.delay
import org.jetbrains.compose.web.css.*

enum class GameMode {
    HIRAGANA_SIGNS,
    HIRAGANA_WORDS,
    HIRAGANA_SENTENCES
}

@Page
@Composable
fun HomePage() {
    var gameState by remember { mutableStateOf(GameState()) }
    var userInput by remember { mutableStateOf("") }
    var isAnswering by remember { mutableStateOf(false) }
    var currentGameMode by remember { mutableStateOf(GameMode.HIRAGANA_SIGNS) }

    val gameService = remember { GameService() }

    fun selectGameMode(mode: GameMode) {
        currentGameMode = mode
        // Reset game state when switching modes
        gameState = GameState(
            hiraganaList = hiraganaCharsLv1
        ).getNextCharacter()
        userInput = ""
    }

    fun selectLevel(level: Int) {
        val characters = hiraganaLvMap[level] ?: hiraganaCharsLv1
        gameState = gameState.copy(
            level = level,
            hiraganaList = characters,
            feedback = "",
            isCorrect = null
        ).getNextCharacter()
        userInput = ""
    }

    fun restartFromLevel1() {
        gameState = GameState(
            hiraganaList = hiraganaCharsLv1
        ).getNextCharacter()
        userInput = ""
    }

    suspend fun submitAnswer() {
        if (isAnswering || gameState.currentChar == null) return

        isAnswering = true
        gameState = gameService.processAnswer(gameState, userInput, gameState.level)

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

            // Game Mode Selection Menu
            Row(
                modifier = Modifier.margin(0.cssRem, 0.cssRem, 1.cssRem, 0.cssRem),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.cssRem)
            ) {
                GameMode.entries.forEach { mode ->
                    Button(
                        onClick = { selectGameMode(mode) },
                        modifier = Modifier
                            .padding(0.5.cssRem, 0.75.cssRem)
                            .fontSize(1.cssRem)
                            .fontWeight(FontWeight.Medium)
                            .backgroundColor(
                                if (currentGameMode == mode)
                                    Color("#FF6B35") // Orange for active
                                else
                                    Color("#6C5CE7") // Purple for inactive
                            )
                            .color(Color.white)
                            .borderRadius(0.5.cssRem)
                            .transition(Transition.all(0.3.s))
                    ) {
                        SpanText(
                            when (mode) {
                                GameMode.HIRAGANA_SIGNS -> "ひらがな Signs"
                                GameMode.HIRAGANA_WORDS -> "ひらがな Words"
                                GameMode.HIRAGANA_SENTENCES -> "ひらがな Sentences"
                            }
                        )
                    }
                }
            }

            // Current mode indicator
            SpanText(
                "Mode: ${
                    when (currentGameMode) {
                        GameMode.HIRAGANA_SIGNS -> "Learning individual hiragana characters"
                        GameMode.HIRAGANA_WORDS -> "Learning hiragana words"
                        GameMode.HIRAGANA_SENTENCES -> "Learning hiragana sentences"
                    }
                }",
                Modifier
                    .fontSize(1.cssRem)
                    .fontStyle(FontStyle.Italic)
                    .opacity(0.8)
                    .margin(0.cssRem, 0.cssRem, 1.cssRem, 0.cssRem)
            )


            // Level Selection Buttons (only show for signs mode for now)
            if (currentGameMode == GameMode.HIRAGANA_SIGNS) {
                Row(
                    modifier = Modifier.gap(0.5.cssRem),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(8) { index ->
                        val level = index + 1
                        Button(
                            onClick = { selectLevel(level) },
                            modifier = Modifier
                                .padding(0.25.cssRem)
                                .minWidth(2.5.cssRem)
                                .fontSize(0.9.cssRem)
                                .backgroundColor(
                                    if (gameState.level == level)
                                        Color("#4CAF50")
                                    else
                                        Color("#2196F3")
                                )
                                .color(Color.white)
                        ) {
                            SpanText("Lv$level")
                        }
                    }
                }

                // Restart Button
                Button(
                    onClick = { restartFromLevel1() },
                    modifier = Modifier
                        .padding(0.5.cssRem)
                        .backgroundColor(Color("#FF5722"))
                        .color(Color.white)
                        .fontSize(0.9.cssRem)
                ) {
                    SpanText("Restart from Lv1")
                }
            } else {
                // Placeholder for other modes
                SpanText(
                    "Coming Soon: ${
                        when (currentGameMode) {
                            GameMode.HIRAGANA_WORDS -> "Word practice will be available soon!"
                            GameMode.HIRAGANA_SENTENCES -> "Sentence practice will be available soon!"
                            else -> ""
                        }
                    }",
                    Modifier
                        .fontSize(1.2.cssRem)
                        .color(Color("#6C5CE7"))
                        .fontWeight(FontWeight.Medium)
                        .padding(2.cssRem)
                        .textAlign(TextAlign.Center)
                )
            }

            // Only show game components for signs mode
            if (currentGameMode == GameMode.HIRAGANA_SIGNS) {
                GameStats(gameState)
                ProgressBar(gameState)
                QuestionArea(
                    gameState = gameState,
                    userInput = userInput,
                    isAnswering = isAnswering,
                    onInputChange = { userInput = it },
                    onSubmit = { submitAnswer() },
                )

                // Instructions
                SpanText(
                    "Level ${gameState.level}: Learning ${hiraganaCharsLv1.size} new characters each level",
                    Modifier.fontSize(0.9.cssRem).opacity(0.7)
                )
            }
        }
    }
}