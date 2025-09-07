package com.github.nanaki_93.ai.generation

import com.github.nanaki_93.dto.AiQuestionDto
import com.github.nanaki_93.models.GameMode
import com.github.nanaki_93.models.Level
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
interface AIService {

    fun callApi(prompt: String): String


    fun getPrompt(questionReq: AiQuestionDto): String = when (questionReq.gameMode) {
        GameMode.WORD, GameMode.SIGN -> getWordsPrompt(questionReq)
        GameMode.SENTENCE -> getSentencesPrompt(questionReq)
    }.also { LoggerFactory.getLogger(this.javaClass).info("===QuestionReq: $questionReq")}

    private fun getWordsPrompt(questionReq: AiQuestionDto): String = """
    You are a Japanese language teacher. Generate exactly ${questionReq.nQuestions} UNIQUE Japanese SINGLE WORDS related to this topic "${questionReq.topic}".
    
    STRICT REQUIREMENTS:
    1. CRITICAL: Generate ONLY SINGLE WORDS - NO phrases, NO sentences, NO multiple words
       - CORRECT: みず (water), さかな (fish), ほん (book)
       - WRONG: みずがあります (there is water), あおいほん (blue book), きれいなさかな (beautiful fish)
    2. Each word must be COMPLETELY DIFFERENT (no duplicates, no variations of the same word)
    3. Use appropriate Japanese characters for the level
    4. Generate MAXIMUM VARIETY across different word types, following the "${questionReq.topic}" as closely as possible:
       - Nouns (objects, animals, people, places, concepts): ねこ, いえ, がっこう
       - Adjectives: あかい, おおきい, あたたかい
       - Verbs (in dictionary form): たべる, のむ, ねる
       - Time expressions: きょう, あした, ゆうがた
       - Body parts: あたま, て, あし
       - Colors: あお, きいろ, みどり
       - Numbers: いち, に, さん
       - Family: おかあさん, おとうさん, きょうだい
    5. Difficulty level based on JLPT: ${questionReq.level}
        ${getLevelDescription(questionReq.level)}
    6. Each line must follow this exact CSV format: hiragana;romanization;translation;topic;level
    
    
    VARIETY EXAMPLES (do not copy these, create your own diverse words):
    
    ねこ;neko;cat;animals;${questionReq.level}
    べんきょう;benkyou;study;education;${questionReq.level}
    あたたかい;atatakai;warm;weather;${questionReq.level}
    
    Generate exactly ${questionReq.nQuestions} COMPLETELY DIFFERENT single words covering maximum variety of this topic "${questionReq.topic}" now:
""".trimIndent()

    private fun getSentencesPrompt(questionReq: AiQuestionDto): String = """
    You are a Japanese language teacher. Generate exactly ${questionReq.nQuestions} UNIQUE Japanese COMPLETE SENTENCES about this topic "${questionReq.topic}".
    
    STRICT REQUIREMENTS:
    1. CRITICAL: Generate COMPLETE SENTENCES ONLY - each sentence must contain 2 OR MORE WORDS
       - CORRECT: きょうはあついです (Today is hot), わたしはがくせいです (I am a student)
       - WRONG: みず (water), ねこ (cat) - these are single words, not sentences
    2. Each sentence must be COMPLETELY DIFFERENT (no duplicates, no similar structures)
    3. Use appropriate Japanese characters for the level
    4. Generate MAXIMUM VARIETY in sentence patterns and structures, following the "${questionReq.topic}" as closely as possible:
       - Subject + Adjective: わたしはげんきです, ねこはかわいいです
       - Location sentences: がっこうにいきます, いえにいます
       - Time expressions: あしたはにちようびです, いまはごごです
       - Action sentences: ほんをよみます, おんがくをききます
       - Existence sentences: つくえのうえにほんがあります
       - Weather/feeling: きょうはさむいです, おなかがすいています
       - Daily activities: あさごはんをたべました, ともだちとはなします
       - Questions: これはなんですか, どこにいきますか
    5. Sentences must be grammatically correct and natural
    6. Difficulty level based on JLPT: ${questionReq.level}
        ${getLevelDescription(questionReq.level)}
    7. Each line must follow this exact CSV format: hiragana;romanization;translation;topic;level
    
    
    VARIETY EXAMPLES (do not copy these, create your own diverse sentences):
    
    きょうはあついです;kyou wa atsui desu;Today is hot;weather;${questionReq.level}
    としょかんでべんきょうします;toshokan de benkyou shimasu;I study at the library;education;${questionReq.level}
    ともだちとえいがをみました;tomodachi to eiga wo mimashita;I watched a movie with friends;entertainment;${questionReq.level}
    
    Generate exactly ${questionReq.nQuestions} COMPLETELY DIFFERENT complete sentences (2+ words each) covering maximum variety of this topic "${questionReq.topic}" now:
""".trimIndent()

    private fun getLevelDescription(level: Level): String = when (level) {
        Level.N5 -> """
        N5 (Beginner): Basic Japanese phrases and expressions. About 100 kanji and 800 vocabulary words. Simple sentences in hiragana, katakana, and basic kanji. Everyday topics spoken slowly. """

        Level.N4 -> """
        N4 (Elementary): Stronger foundation in basic Japanese. Familiar topics in conversations and daily subjects. Around 1,500 words and 300 kanji."""

        Level.N3 -> """
        N3 (Intermediate): Bridge between basic and advanced understanding. Slightly complex written material and natural conversations. Around 3,750 words and 650 kanji."""

        Level.N2 -> """
        N2 (Upper Intermediate): High proficiency. Articles and commentary on various topics with conversation nuances. About 1,000 kanji and 6,000 vocabulary words."""

        Level.N1 -> """
        N1 (Advanced): Comprehensive grasp in wide range of situations including in-depth discussions and complex readings. Roughly 2,000 kanji and 10,000+ words."""
    }
}