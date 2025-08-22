package com.github.nanaki_93.models

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
    val hiraganaList: List<HiraganaChar> = emptyList(),
    val feedback: String = "",
    val isCorrect: Boolean? = null
)

fun GameState.getNextCharacter(): GameState =
    if (this.hiraganaList.isNotEmpty()) {
        copy(currentChar = this.hiraganaList[kotlin.random.Random.nextInt(this.hiraganaList.size)])
    } else {
        this
    }

// Hiragana data

val hiraganaCharsLv1 = listOf(
    HiraganaChar("あ", "a", 1),
    HiraganaChar("い", "i", 1),
    HiraganaChar("う", "u", 1),
    HiraganaChar("え", "e", 1),
    HiraganaChar("お", "o", 1),
    HiraganaChar("か", "ka", 1),
    HiraganaChar("き", "ki", 1),
    HiraganaChar("く", "ku", 1),
    HiraganaChar("け", "ke", 1),
    HiraganaChar("こ", "ko", 1),
)
val hiraganaCharsLv2 = listOf(
    HiraganaChar("さ", "sa", 2),
    HiraganaChar("し", "shi", 2),
    HiraganaChar("す", "su", 2),
    HiraganaChar("せ", "se", 2),
    HiraganaChar("そ", "so", 2),
    HiraganaChar("た", "ta", 2),
    HiraganaChar("ち", "chi", 2),
    HiraganaChar("つ", "tsu", 2),
    HiraganaChar("て", "te", 2),
    HiraganaChar("と", "to", 2),
    HiraganaChar("な", "na", 2),
    HiraganaChar("に", "ni", 2),
    HiraganaChar("ぬ", "nu", 2),
    HiraganaChar("ね", "ne", 2),
    HiraganaChar("の", "no", 2),
)
val hiraganaCharsLv3 = listOf(
    HiraganaChar("は", "ha", 3),
    HiraganaChar("ひ", "hi", 3),
    HiraganaChar("ふ", "fu", 3),
    HiraganaChar("へ", "he", 3),
    HiraganaChar("ほ", "ho", 3),
    HiraganaChar("ま", "ma", 3),
    HiraganaChar("み", "mi", 3),
    HiraganaChar("む", "mu", 3),
    HiraganaChar("め", "me", 3),
    HiraganaChar("も", "mo", 3),
)
val hiraganaCharsLv4 = listOf(
    HiraganaChar("や", "ya", 4),
    HiraganaChar("ゆ", "yu", 4),
    HiraganaChar("よ", "yo", 4),
    HiraganaChar("ら", "ra", 4),
    HiraganaChar("り", "ri", 4),
    HiraganaChar("る", "ru", 4),
    HiraganaChar("れ", "re", 4),
    HiraganaChar("ろ", "ro", 4),
    HiraganaChar("わ", "wa", 4),
    HiraganaChar("ん", "n", 4),
    HiraganaChar("を", "wu", 4)
)

val hiraganaLvMap = mapOf(
    1 to hiraganaCharsLv1,
    2 to hiraganaCharsLv1 + hiraganaCharsLv2,
    3 to hiraganaCharsLv1 + hiraganaCharsLv2 + hiraganaCharsLv3,
    4 to hiraganaCharsLv1 + hiraganaCharsLv2 + hiraganaCharsLv3 + hiraganaCharsLv4
)