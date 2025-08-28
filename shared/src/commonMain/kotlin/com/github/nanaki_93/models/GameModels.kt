package com.github.nanaki_93.models

import kotlinx.serialization.Serializable


enum class GameMode(val displayName: String) {
    SIGN("Sign"),
    WORD("Word"),
    SENTENCE("Sentence")
}

@Serializable
data class HiraganaQuestionDto(
    val hiragana: String,
    val romanization: String,
    val translation: String? = null,
    val topic: String = "",
    val difficulty: Int = 1
)

@Serializable
data class GameState(
    val score: Int = 0,
    val streak: Int = 0,
    val level: Int = 1,
    val totalAnswered: Int = 0,
    val correctAnswers: Int = 0,
    val currentChar: HiraganaQuestionDto? = null,
    val hiraganaList: List<HiraganaQuestionDto> = emptyList(),
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
    HiraganaQuestionDto("あ", "a"),
    HiraganaQuestionDto("い", "i"),
    HiraganaQuestionDto("う", "u"),
    HiraganaQuestionDto("え", "e"),
    HiraganaQuestionDto("お", "o"),
    HiraganaQuestionDto("か", "ka"),
    HiraganaQuestionDto("き", "ki"),
    HiraganaQuestionDto("く", "ku"),
    HiraganaQuestionDto("け", "ke"),
    HiraganaQuestionDto("こ", "ko"),
)
val hiraganaCharsLv2s = listOf(
    HiraganaQuestionDto("さ", "sa"),
    HiraganaQuestionDto("し", "shi"),
    HiraganaQuestionDto("す", "su"),
    HiraganaQuestionDto("せ", "se"),
    HiraganaQuestionDto("そ", "so"),
    HiraganaQuestionDto("た", "ta"),
    HiraganaQuestionDto("ち", "chi"),
    HiraganaQuestionDto("つ", "tsu"),
    HiraganaQuestionDto("て", "te"),
    HiraganaQuestionDto("と", "to"),
    HiraganaQuestionDto("な", "na"),
    HiraganaQuestionDto("に", "ni"),
    HiraganaQuestionDto("ぬ", "nu"),
    HiraganaQuestionDto("ね", "ne"),
    HiraganaQuestionDto("の", "no"),
)
val hiraganaCharsLv3s = listOf(
    HiraganaQuestionDto("は", "ha"),
    HiraganaQuestionDto("ひ", "hi"),
    HiraganaQuestionDto("ふ", "fu"),
    HiraganaQuestionDto("へ", "he"),
    HiraganaQuestionDto("ほ", "ho"),
    HiraganaQuestionDto("ま", "ma"),
    HiraganaQuestionDto("み", "mi"),
    HiraganaQuestionDto("む", "mu"),
    HiraganaQuestionDto("め", "me"),
    HiraganaQuestionDto("も", "mo"),
)
val hiraganaCharsLv4s = listOf(
    HiraganaQuestionDto("や", "ya"),
    HiraganaQuestionDto("ゆ", "yu"),
    HiraganaQuestionDto("よ", "yo"),
    HiraganaQuestionDto("ら", "ra"),
    HiraganaQuestionDto("り", "ri"),
    HiraganaQuestionDto("る", "ru"),
    HiraganaQuestionDto("れ", "re"),
    HiraganaQuestionDto("ろ", "ro"),
    HiraganaQuestionDto("わ", "wa"),
    HiraganaQuestionDto("ん", "n"),
    HiraganaQuestionDto("を", "wu"),
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
