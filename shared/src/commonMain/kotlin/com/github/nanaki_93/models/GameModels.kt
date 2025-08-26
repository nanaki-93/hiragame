package com.github.nanaki_93.models

import kotlinx.serialization.Serializable


enum class GameMode(val displayName: String) {
    SIGN("Sign"),
    WORD("Word"),
    SENTENCE("Sentence")
}

@Serializable
data class HiraganaQuestion(
    val char: String,
    val romanization: String,
    val translation: String? = null,
    val difficulty: Int = 1
)

@Serializable
data class GameState(
    val score: Int = 0,
    val streak: Int = 0,
    val level: Int = 1,
    val totalAnswered: Int = 0,
    val correctAnswers: Int = 0,
    val currentChar: HiraganaQuestion? = null,
    val hiraganaList: List<HiraganaQuestion> = emptyList(),
    val feedback: String = "",
    val isCorrect: Boolean? = null
)

fun GameState.getNextQuestion(): GameState =
    if (this.hiraganaList.isNotEmpty()) {
        copy(currentChar = this.hiraganaList[kotlin.random.Random.nextInt(this.hiraganaList.size)])
    } else {
        this
    }

// Hiragana data

val hiraganaCharsLv1s = listOf(
    HiraganaQuestion("あ", "a"),
    HiraganaQuestion("い", "i"),
    HiraganaQuestion("う", "u"),
    HiraganaQuestion("え", "e"),
    HiraganaQuestion("お", "o"),
    HiraganaQuestion("か", "ka"),
    HiraganaQuestion("き", "ki"),
    HiraganaQuestion("く", "ku"),
    HiraganaQuestion("け", "ke"),
    HiraganaQuestion("こ", "ko"),
)
val hiraganaCharsLv2s = listOf(
    HiraganaQuestion("さ", "sa"),
    HiraganaQuestion("し", "shi"),
    HiraganaQuestion("す", "su"),
    HiraganaQuestion("せ", "se"),
    HiraganaQuestion("そ", "so"),
    HiraganaQuestion("た", "ta"),
    HiraganaQuestion("ち", "chi"),
    HiraganaQuestion("つ", "tsu"),
    HiraganaQuestion("て", "te"),
    HiraganaQuestion("と", "to"),
    HiraganaQuestion("な", "na"),
    HiraganaQuestion("に", "ni"),
    HiraganaQuestion("ぬ", "nu"),
    HiraganaQuestion("ね", "ne"),
    HiraganaQuestion("の", "no"),
)
val hiraganaCharsLv3s = listOf(
    HiraganaQuestion("は", "ha"),
    HiraganaQuestion("ひ", "hi"),
    HiraganaQuestion("ふ", "fu"),
    HiraganaQuestion("へ", "he"),
    HiraganaQuestion("ほ", "ho"),
    HiraganaQuestion("ま", "ma"),
    HiraganaQuestion("み", "mi"),
    HiraganaQuestion("む", "mu"),
    HiraganaQuestion("め", "me"),
    HiraganaQuestion("も", "mo"),
)
val hiraganaCharsLv4s = listOf(
    HiraganaQuestion("や", "ya"),
    HiraganaQuestion("ゆ", "yu"),
    HiraganaQuestion("よ", "yo"),
    HiraganaQuestion("ら", "ra"),
    HiraganaQuestion("り", "ri"),
    HiraganaQuestion("る", "ru"),
    HiraganaQuestion("れ", "re"),
    HiraganaQuestion("ろ", "ro"),
    HiraganaQuestion("わ", "wa"),
    HiraganaQuestion("ん", "n"),
    HiraganaQuestion("を", "wu"),
)

val hiraganaLvMap = mapOf(
    1 to hiraganaCharsLv1s,
    2 to hiraganaCharsLv2s,
    3 to hiraganaCharsLv3s,
    4 to hiraganaCharsLv4s
)

@Serializable
data class ProcessAnswerRequest(val gameState: GameState, val userInput: String, val level: Int)

@Serializable
data class GameStateRequest(val gameState: GameState)

@Serializable
data class GameModeRequest(val gameMode: GameMode)
