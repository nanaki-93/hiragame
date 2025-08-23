package com.github.nanaki_93.models

data class HiraganaChar(
    val char: String,
    val romanization: String,
    val level: Int = 1,
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
    // Basic vowels
    HiraganaChar("あ", "a"),
    HiraganaChar("い", "i"),
    HiraganaChar("う", "u"),
    HiraganaChar("え", "e"),
    HiraganaChar("お", "o"),

    // K-line (ka-gyou)
    HiraganaChar("か", "ka"),
    HiraganaChar("き", "ki"),
    HiraganaChar("く", "ku"),
    HiraganaChar("け", "ke"),
    HiraganaChar("こ", "ko"),

    )
val hiraganaCharsLv1Pro = listOf(

    // Y-line (ya-gyou)
    HiraganaChar("や", "ya"),
    HiraganaChar("ゆ", "yu"),
    HiraganaChar("よ", "yo"),
    // W-line (wa-gyou)
    HiraganaChar("わ", "wa"),
    HiraganaChar("を", "wo"), // Particle "o"
    // G-line (ga-gyou) - with dakuten (tenten)
    HiraganaChar("が", "ga"),
    HiraganaChar("ぎ", "gi"),
    HiraganaChar("ぐ", "gu"),
    HiraganaChar("げ", "ge"),
    HiraganaChar("ご", "go"),

    )

val hiraganaCharsLv2 = listOf(
    // S-line (sa-gyou)
    HiraganaChar("さ", "sa"),
    HiraganaChar("し", "shi"),
    HiraganaChar("す", "su"),
    HiraganaChar("せ", "se"),
    HiraganaChar("そ", "so"),
    // T-line (ta-gyou)
    HiraganaChar("た", "ta"),
    HiraganaChar("ち", "chi"),
    HiraganaChar("つ", "tsu"),
    HiraganaChar("て", "te"),
    HiraganaChar("と", "to"),


    )
val hiraganaCharsLv2Pro = listOf(

    // Z-line (za-gyou) - with dakuten (tenten)
    HiraganaChar("ざ", "za"),
    HiraganaChar("じ", "ji"),
    HiraganaChar("ず", "zu"),
    HiraganaChar("ぜ", "ze"),
    HiraganaChar("ぞ", "zo"),

    // D-line (da-gyou) - with dakuten (tenten)
    HiraganaChar("だ", "da"),
    HiraganaChar("ぢ", "di"), // Note: pronounced "ji" but romanized as "di"
    HiraganaChar("づ", "du"), // Note: pronounced "zu" but romanized as "du"
    HiraganaChar("で", "de"),
    HiraganaChar("ど", "do"),

    )

val hiraganaCharsLv3 = listOf(
    // N-line (na-gyou)
    HiraganaChar("な", "na"),
    HiraganaChar("に", "ni"),
    HiraganaChar("ぬ", "nu"),
    HiraganaChar("ね", "ne"),
    HiraganaChar("の", "no"),

    // H-line (ha-gyou)
    HiraganaChar("は", "ha"),
    HiraganaChar("ひ", "hi"),
    HiraganaChar("ふ", "fu"),
    HiraganaChar("へ", "he"),
    HiraganaChar("ほ", "ho"),


    )
val hiraganaCharsLv3Pro = listOf(
    // B-line (ba-gyou) - with dakuten (tenten)
    HiraganaChar("ば", "ba"),
    HiraganaChar("び", "bi"),
    HiraganaChar("ぶ", "bu"),
    HiraganaChar("べ", "be"),
    HiraganaChar("ぼ", "bo"),
    // P-line (pa-gyou) - with handakuten (maru)
    HiraganaChar("ぱ", "pa"),
    HiraganaChar("ぴ", "pi"),
    HiraganaChar("ぷ", "pu"),
    HiraganaChar("ぺ", "pe"),
    HiraganaChar("ぽ", "po"),

    )
val hiraganaCharsLv4 = listOf(

    // M-line (ma-gyou)
    HiraganaChar("ま", "ma"),
    HiraganaChar("み", "mi"),
    HiraganaChar("む", "mu"),
    HiraganaChar("め", "me"),
    HiraganaChar("も", "mo"),

    // R-line (ra-gyou)
    HiraganaChar("ら", "ra"),
    HiraganaChar("り", "ri"),
    HiraganaChar("る", "ru"),
    HiraganaChar("れ", "re"),
    HiraganaChar("ろ", "ro"),

    )

val hiraganaCharsLv5 = listOf(
    // N
    HiraganaChar("ん", "n"),

    // Combination characters (ya-yu-yo combinations)
    HiraganaChar("きゃ", "kya"),
    HiraganaChar("きゅ", "kyu"),
    HiraganaChar("きょ", "kyo"),

    HiraganaChar("ぎゃ", "gya"),
    HiraganaChar("ぎゅ", "gyu"),
    HiraganaChar("ぎょ", "gyo"),
    HiraganaChar("しゃ", "sha"),
    HiraganaChar("しゅ", "shu"),
    HiraganaChar("しょ", "sho"),
)
val hiraganaCharsLv6 = listOf(

    HiraganaChar("じゃ", "ja"),
    HiraganaChar("じゅ", "ju"),
    HiraganaChar("じょ", "jo"),

    HiraganaChar("ちゃ", "cha"),
    HiraganaChar("ちゅ", "chu"),
    HiraganaChar("ちょ", "cho"),


    HiraganaChar("にゃ", "nya"),
    HiraganaChar("にゅ", "nyu"),
    HiraganaChar("にょ", "nyo"),
)
val hiraganaCharsLv7 = listOf(

    HiraganaChar("ひゃ", "hya"),
    HiraganaChar("ひゅ", "hyu"),
    HiraganaChar("ひょ", "hyo"),

    HiraganaChar("びゃ", "bya"),
    HiraganaChar("びゅ", "byu"),
    HiraganaChar("びょ", "byo"),

    HiraganaChar("ぴゃ", "pya"),
    HiraganaChar("ぴゅ", "pyu"),
    HiraganaChar("ぴょ", "pyo"),
)


val hiraganaCharsLv8 = listOf(

    HiraganaChar("みゃ", "mya"),
    HiraganaChar("みゅ", "myu"),
    HiraganaChar("みょ", "myo"),

    HiraganaChar("りゃ", "rya"),
    HiraganaChar("りゅ", "ryu"),
    HiraganaChar("りょ", "ryo"),

    HiraganaChar("ぢゃ", "dya"), // Rarely used
    HiraganaChar("ぢゅ", "dyu"), // Rarely used
    HiraganaChar("ぢょ", "dyo"), // Rarely used

)

val hiraganaLvMap = mapOf(
    1 to hiraganaCharsLv1,
    2 to hiraganaCharsLv2,
    3 to hiraganaCharsLv3,
    4 to hiraganaCharsLv4,
    5 to hiraganaCharsLv5,
    6 to hiraganaCharsLv6,
    7 to hiraganaCharsLv7,
    8 to hiraganaCharsLv8,
)