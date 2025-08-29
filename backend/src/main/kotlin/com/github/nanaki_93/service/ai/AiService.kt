package com.github.nanaki_93.service.ai

import org.springframework.stereotype.Service

@Service
interface AiService {

    fun callApi(prompt: String): String


    fun getWordsPrompt(topic: String, difficulty: Int, nQuestions: Int): String = """
        You are a Japanese language teacher. Generate exactly $nQuestions UNIQUE Japanese sentences about "$topic".
        
        STRICT REQUIREMENTS:
        1. Each sentence must be DIFFERENT (no duplicates)
        2. Use ONLY hiragana characters
        3. Sentences must be simple and grammatically correct
        4. Appropriate for Japanese learners at difficulty level $difficulty
        5. Each line must follow this exact CSV format: hiragana;romanization;translation;topic;level
        
        EXAMPLES (do not copy these):
        きょうはあついです;kyou wa atsui desu;Today is hot;weather;$difficulty
        わたしはがくせいです;watashi wa gakusei desu;I am a student;school;$difficulty
        
        Generate exactly $nQuestions DIFFERENT sentences about $topic now:
    """.trimIndent()

    fun getSentencesPrompt(topic: String, difficulty: Int, nQuestions: Int): String = """
        You are a Japanese language teacher. Generate exactly $nQuestions UNIQUE Japanese sentences about "$topic".
        
        STRICT REQUIREMENTS:
        1. Each sentence must be DIFFERENT (no duplicates)
        2. Use ONLY hiragana characters
        3. Sentences must be simple and grammatically correct
        4. Appropriate for Japanese learners at difficulty level $difficulty
        5. Each line must follow this exact CSV format: hiragana;romanization;translation;topic;level
        
        EXAMPLES (do not copy these):
        きょうはあついです;kyou wa atsui desu;Today is hot;weather;$difficulty
        わたしはがくせいです;watashi wa gakusei desu;I am a student;school;$difficulty
        
        Generate exactly $nQuestions DIFFERENT sentences about $topic now:
    """.trimIndent()
}