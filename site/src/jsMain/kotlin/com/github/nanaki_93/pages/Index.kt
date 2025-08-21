package com.github.nanaki_93.pages


import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.css.*
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.silk.components.forms.Button
import com.varabyte.kobweb.silk.components.forms.TextInput
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.style.toModifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.JustifyContent
import kotlin.random.Random

// Data classes
data class HiraganaChar(
    val char: String,
    val romanization: String,
    val difficulty: Int = 1
)

data class GameState(
    val score: Int = 0,
    val streak: Int = 0,
    val level: Int = 1,
    val totalAnswered: Int = 0,
    val correctAnswers: Int = 0,
    val currentChar: HiraganaChar? = null,
    val feedback: String = "",
    val isCorrect: Boolean? = null
)

// Styles
val GameContainerStyle = CssStyle.base {
    Modifier
        .fillMaxSize()
        .styleModifier {
            background("linear-gradient(135deg, rgba(102, 126, 234, 1.0) 0%, rgba(118, 75, 162, 1.0) 100%)")
        }
        .minHeight(100.vh)
        .display(DisplayStyle.Flex)
        .justifyContent(JustifyContent.Center)
        .alignItems(AlignItems.Center)
        .fontFamily("Arial", "sans-serif")
}

val CardStyle = CssStyle.base {
    Modifier
        .background(rgba(255, 255, 255, 0.1))
        .borderRadius(20.px)
        .padding(2.cssRem)
        .boxShadow(
            0.px, 8.px, 32.px, 0.px,
            color = rgba(31, 38, 135, 0.37),
            inset = false
        )
        .border(1.px, LineStyle.Solid, rgba(255, 255, 255, 0.18))
        .maxWidth(500.px)
        .width(90.percent)
        .textAlign(TextAlign.Center)
        .color(Colors.White)
}



val HiraganaCharStyle = CssStyle.base {
    Modifier
        .fontSize(6.cssRem)
        .fontWeight(FontWeight.Bold)
        .margin(1.cssRem, 0.px)
        .textShadow(3.px, 3.px, 6.px, rgba(0, 0, 0, 0.4))
}

val StatItemStyle = CssStyle.base {
    Modifier
        .background(rgba(255, 255, 255, 0.2))
        .padding(0.5.cssRem, 1.cssRem)
        .borderRadius(10.px)
        .flexGrow(1)
        .minWidth(100.px)
        .margin(0.25.cssRem)
}

val InputStyle = CssStyle.base {
    Modifier
        .background(rgba(255, 255, 255, 0.2))
        .border(2.px, LineStyle.Solid, rgba(255, 255, 255, 0.3))
        .borderRadius(10.px)
        .padding(0.8.cssRem)
        .fontSize(1.2.cssRem)
        .color(Colors.White)
        .textAlign(TextAlign.Center)
        .width(100.percent)
        .margin(1.cssRem, 0.px)
}

val ButtonStyle = CssStyle.base {
    Modifier
        .styleModifier { background("linear-gradient(45deg, rgba(255, 107, 107, 1.0) 0%, rgba(78, 205, 196, 1.0) 100%)") }
        .border(0.px)
        .padding(0.8.cssRem, 2.cssRem)
        .fontSize(1.1.cssRem)
        .color(Colors.White)
        .borderRadius(25.px)
        .cursor(Cursor.Pointer)
        .boxShadow(0.px, 4.px, 15.px, color = rgba(0, 0, 0, 0.2))
        .transition(Transition.all(0.3.s))
}


// Hiragana data
val hiraganaChars = listOf(
    HiraganaChar("あ", "a", 1), HiraganaChar("い", "i", 1), HiraganaChar("う", "u", 1),
    HiraganaChar("え", "e", 1), HiraganaChar("お", "o", 1),
    HiraganaChar("か", "ka", 1), HiraganaChar("き", "ki", 1), HiraganaChar("く", "ku", 1),
    HiraganaChar("け", "ke", 1), HiraganaChar("こ", "ko", 1),
    HiraganaChar("さ", "sa", 2), HiraganaChar("し", "shi", 2), HiraganaChar("す", "su", 2),
    HiraganaChar("せ", "se", 2), HiraganaChar("そ", "so", 2),
    HiraganaChar("た", "ta", 2), HiraganaChar("ち", "chi", 2), HiraganaChar("つ", "tsu", 2),
    HiraganaChar("て", "te", 2), HiraganaChar("と", "to", 2),
    HiraganaChar("な", "na", 2), HiraganaChar("に", "ni", 2), HiraganaChar("ぬ", "nu", 2),
    HiraganaChar("ね", "ne", 2), HiraganaChar("の", "no", 2),
    HiraganaChar("は", "ha", 3), HiraganaChar("ひ", "hi", 3), HiraganaChar("ふ", "fu", 3),
    HiraganaChar("へ", "he", 3), HiraganaChar("ほ", "ho", 3),
    HiraganaChar("ま", "ma", 3), HiraganaChar("み", "mi", 3), HiraganaChar("む", "mu", 3),
    HiraganaChar("め", "me", 3), HiraganaChar("も", "mo", 3),
    HiraganaChar("や", "ya", 4), HiraganaChar("ゆ", "yu", 4), HiraganaChar("よ", "yo", 4),
    HiraganaChar("ら", "ra", 4), HiraganaChar("り", "ri", 4), HiraganaChar("る", "ru", 4),
    HiraganaChar("れ", "re", 4), HiraganaChar("ろ", "ro", 4),
    HiraganaChar("わ", "wa", 5), HiraganaChar("ん", "n", 5)
)

@Page
@Composable
fun HomePage() {
    var gameState by remember { mutableStateOf(GameState()) }
    var userInput by remember { mutableStateOf("") }
    var isAnswering by remember { mutableStateOf(false) }

    fun getNextCharacter(level: Int): HiraganaChar {
        val availableChars = hiraganaChars.filter { it.difficulty <= level }
        return availableChars[Random.nextInt(availableChars.size)]
    }

    fun calculateLevel(correctAnswers: Int): Int {
        return minOf(5, (correctAnswers / 10) + 1)
    }

    suspend fun submitAnswer() {
        if (isAnswering || gameState.currentChar == null) return

        isAnswering = true
        val currentChar = gameState.currentChar!!
        val isCorrect = userInput.lowercase().trim() == currentChar.romanization.lowercase()

        val newCorrectAnswers = if (isCorrect) gameState.correctAnswers + 1 else gameState.correctAnswers
        val newStreak = if (isCorrect) gameState.streak + 1 else 0
        val newScore = gameState.score + if (isCorrect) (10 + newStreak * 2) else 0
        val newLevel = calculateLevel(newCorrectAnswers)

        gameState = gameState.copy(
            score = newScore,
            streak = newStreak,
            totalAnswered = gameState.totalAnswered + 1,
            correctAnswers = newCorrectAnswers,
            level = newLevel,
            feedback = if (isCorrect) {
                when (newStreak) {
                    in 1..2 -> "Correct! 正解！"
                    in 3..5 -> "Great streak! 連続正解！"
                    in 6..10 -> "Amazing! 素晴らしい！"
                    else -> "Incredible! マスター級！"
                }
            } else {
                "Wrong! It's '${currentChar.romanization}' 間違い！"
            },
            isCorrect = isCorrect
        )

        delay(1500)

        gameState = gameState.copy(
            currentChar = getNextCharacter(newLevel),
            feedback = "",
            isCorrect = null
        )

        userInput = ""
        isAnswering = false
    }

    // Initialize with first character
    LaunchedEffect(Unit) {
        gameState = gameState.copy(currentChar = getNextCharacter(gameState.level))
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

            // Stats
            Row(
                Modifier.fillMaxWidth().flexWrap(FlexWrap.Wrap),
                horizontalArrangement = Arrangement.spacedBy(0.5.cssRem)
            ) {
                Column(StatItemStyle.toModifier(), horizontalAlignment = Alignment.CenterHorizontally) {
                    SpanText("Score", Modifier.fontSize(0.8.cssRem).opacity(0.8))
                    SpanText("${gameState.score}", Modifier.fontSize(1.2.cssRem).fontWeight(FontWeight.Bold))
                }
                Column(StatItemStyle.toModifier(), horizontalAlignment = Alignment.CenterHorizontally) {
                    SpanText("Streak", Modifier.fontSize(0.8.cssRem).opacity(0.8))
                    SpanText("${gameState.streak}", Modifier.fontSize(1.2.cssRem).fontWeight(FontWeight.Bold))
                }
                Column(StatItemStyle.toModifier(), horizontalAlignment = Alignment.CenterHorizontally) {
                    SpanText("Level", Modifier.fontSize(0.8.cssRem).opacity(0.8))
                    SpanText("${gameState.level}", Modifier.fontSize(1.2.cssRem).fontWeight(FontWeight.Bold))
                }
                Column(StatItemStyle.toModifier(), horizontalAlignment = Alignment.CenterHorizontally) {
                    SpanText("Accuracy", Modifier.fontSize(0.8.cssRem).opacity(0.8))
                    val accuracy = if (gameState.totalAnswered > 0) {
                        (gameState.correctAnswers * 100 / gameState.totalAnswered)
                    } else 0
                    SpanText("$accuracy%", Modifier.fontSize(1.2.cssRem).fontWeight(FontWeight.Bold))
                }
            }

            // Progress bar
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.px)
                    .background(rgba(255, 255, 255, 0.2))
                    .borderRadius(5.px)
            ) {
                val progress = ((gameState.correctAnswers % 10) * 10)
                Box(
                    Modifier
                        .width(progress.percent)
                        .height(100.percent)
                        .styleModifier {
                            background("linear-gradient(90deg, rgba(76, 175, 80, 1.0) 0%, rgba(139, 195, 74, 1.0) 100%)")
                        }
                        .borderRadius(5.px)
                )
            }

            // Question area
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

                    val coroutineScope = rememberCoroutineScope()

                    TextInput(
                        text = userInput,
                        onTextChange = { userInput = it },
                        modifier = InputStyle.toModifier()
                            .onKeyDown { keyboardEvent ->
                                if (keyboardEvent.key == "Enter" && !isAnswering) {
                                    keyboardEvent.preventDefault()
                                    coroutineScope.launch { submitAnswer() }
                                }
                            },
                        placeholder = "Type romanization here..."
                    )

                    Button(
                        onClick = {
                            coroutineScope.launch { submitAnswer() }
                        },
                        modifier = ButtonStyle.toModifier(),
                        enabled = !isAnswering && userInput.isNotBlank()
                    ) {
                        SpanText(if (isAnswering) "Checking..." else "Submit")
                    }

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

            // Instructions
            SpanText(
                "Level ${gameState.level}: Learning ${hiraganaChars.filter { it.difficulty <= gameState.level }.size} characters",
                Modifier.fontSize(0.9.cssRem).opacity(0.7)
            )
        }
    }
}