package com.github.nanaki_93.service.ai

import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.models.Level
import com.github.nanaki_93.models.topicsByJLPTLevel
import org.springframework.stereotype.Service

@Service
interface AiService {

    fun callApi(prompt: String): String


    fun getPrompt(level: Level, nQuestions: Int, gameMode: GameMode): String = when (gameMode) {
        GameMode.WORD, GameMode.SIGN -> getWordsPrompt(level, nQuestions)
        GameMode.SENTENCE -> getSentencesPrompt(level, nQuestions)
    }

    private fun getWordsPrompt(level: Level, nQuestions: Int): String = """
    You are a Japanese language teacher. Generate exactly $nQuestions UNIQUE Japanese SINGLE WORDS related to these topics ["${
        topicsByJLPTLevel[level]?.joinToString(
            ", "
        )
    }"].
    
    STRICT REQUIREMENTS:
    1. CRITICAL: Generate ONLY SINGLE WORDS - NO phrases, NO sentences, NO multiple words
       - CORRECT: みず (water), さかな (fish), ほん (book)
       - WRONG: みずがあります (there is water), あおいほん (blue book), きれいなさかな (beautiful fish)
    2. Each word must be COMPLETELY DIFFERENT (no duplicates, no variations of the same word)
    3. Use appropriate Japanese characters for the level
    4. Generate MAXIMUM VARIETY across different word types:
       - Nouns (objects, animals, people, places, concepts): ねこ, いえ, がっこう
       - Adjectives: あかい, おおきい, あたたかい
       - Verbs (in dictionary form): たべる, のむ, ねる
       - Time expressions: きょう, あした, ゆうがた
       - Body parts: あたま, て, あし
       - Colors: あお, きいろ, みどり
       - Numbers: いち, に, さん
       - Family: おかあさん, おとうさん, きょうだい
    5. Difficulty level based on JLPT: $level
        ${getLevelDescription(level)}
    6. Each line must follow this exact CSV format: hiragana;romanization;translation;topic;level
    
    
    VARIETY EXAMPLES (do not copy these, create your own diverse words):
    
    ねこ;neko;cat;animals;$level
    べんきょう;benkyou;study;education;$level
    あたたかい;atatakai;warm;weather;$level
    
    Generate exactly $nQuestions COMPLETELY DIFFERENT single words covering maximum variety of topics ["${
        topicsByJLPTLevel[level]?.joinToString(
            ", "
        )
    }"] now:
""".trimIndent()

    private fun getSentencesPrompt(level: Level, nQuestions: Int): String = """
    You are a Japanese language teacher. Generate exactly $nQuestions UNIQUE Japanese COMPLETE SENTENCES about these topics ["${
        topicsByJLPTLevel[level]?.joinToString(
            ", "
        )
    }"].
    
    STRICT REQUIREMENTS:
    1. CRITICAL: Generate COMPLETE SENTENCES ONLY - each sentence must contain 2 OR MORE WORDS
       - CORRECT: きょうはあついです (Today is hot), わたしはがくせいです (I am a student)
       - WRONG: みず (water), ねこ (cat) - these are single words, not sentences
    2. Each sentence must be COMPLETELY DIFFERENT (no duplicates, no similar structures)
    3. Use appropriate Japanese characters for the level
    4. Generate MAXIMUM VARIETY in sentence patterns and structures:
       - Subject + Adjective: わたしはげんきです, ねこはかわいいです
       - Location sentences: がっこうにいきます, いえにいます
       - Time expressions: あしたはにちようびです, いまはごごです
       - Action sentences: ほんをよみます, おんがくをききます
       - Existence sentences: つくえのうえにほんがあります
       - Weather/feeling: きょうはさむいです, おなかがすいています
       - Daily activities: あさごはんをたべました, ともだちとはなします
       - Questions: これはなんですか, どこにいきますか
    5. Sentences must be grammatically correct and natural
    6. Difficulty level based on JLPT: $level
        ${getLevelDescription(level)}
    7. Each line must follow this exact CSV format: hiragana;romanization;translation;topic;level
    
    
    VARIETY EXAMPLES (do not copy these, create your own diverse sentences):
    
    きょうはあついです;kyou wa atsui desu;Today is hot;weather;$level
    としょかんでべんきょうします;toshokan de benkyou shimasu;I study at the library;education;$level
    ともだちとえいがをみました;tomodachi to eiga wo mimashita;I watched a movie with friends;entertainment;$level
    
    Generate exactly $nQuestions COMPLETELY DIFFERENT complete sentences (2+ words each) covering maximum variety of topics ["${
        topicsByJLPTLevel[level]?.joinToString(
            ", "
        )
    }"] now:
""".trimIndent()

    private fun getLevelDescription(level: Level): String = when (level) {
        Level.N5 -> """
        N5 (Beginner): Basic Japanese phrases and expressions. About 100 kanji and 800 vocabulary words. Simple sentences in hiragana, katakana, and basic kanji. Everyday topics spoken slowly. """

        Level.N5_PLUS -> """
        N5_PLUS (Beginner+): Good grasp of basic Japanese with more complex sentences and expressions. About 150 kanji and 1,200 vocabulary words. More complex sentences and conversations at natural speed."""

        Level.N4 -> """
        N4 (Elementary): Stronger foundation in basic Japanese. Familiar topics in conversations and daily subjects. Around 1,500 words and 300 kanji."""

        Level.N4_PLUS -> """
        N4_PLUS (Elementary+): Advanced elementary level with complex sentences and expressions. About 200 kanji and 1,800 vocabulary words."""

        Level.N3 -> """
        N3 (Intermediate): Bridge between basic and advanced understanding. Slightly complex written material and natural conversations. Around 3,750 words and 650 kanji."""

        Level.N2 -> """
        N2 (Upper Intermediate): High proficiency. Articles and commentary on various topics with conversation nuances. About 1,000 kanji and 6,000 vocabulary words."""

        Level.N1 -> """
        N1 (Advanced): Comprehensive grasp in wide range of situations including in-depth discussions and complex readings. Roughly 2,000 kanji and 10,000+ words."""
    }
}