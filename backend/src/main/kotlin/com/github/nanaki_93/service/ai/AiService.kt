package com.github.nanaki_93.service.ai

import com.github.nanaki_93.models.Level
import org.springframework.stereotype.Service

@Service
interface AiService {

    fun callApi(prompt: String): String


    fun getWordsPrompt(topic: String, level: Level, nQuestions: Int): String = """
        You are a Japanese language teacher. Generate exactly $nQuestions UNIQUE Japanese words related to "$topic".
        
        STRICT REQUIREMENTS:
        1. Each word must be DIFFERENT (no duplicates)
        2. Use ONLY hiragana characters
        3. Words must be common and appropriate for Japanese learners
        4. Difficulty level based on JLPT Japanese language profiency test : $level  
            N5 (Beginner) :This is the starting point. At this level, you can understand some basic Japanese phrases and expressions. You'll need to know about 100 kanji and roughly 800 vocabulary words. You can read simple, short sentences written in hiragana, katakana, and basic kanji. You can also listen to and understand conversations about everyday topics spoken slowly.(In this level you have to generate words with ONLY hiragana characters)
            N5_PLUS (Beginner+) :This level is for those who have a good grasp of basic Japanese but want to improve their understanding of more complex sentences and expressions. You'll need to know about 150 kanji and roughly 1,200 vocabulary words. You can read and understand more complex sentences and expressions, and you can listen to and understand conversations about more complex topics at a more natural speed.
            N4 (Elementary) :At this level, you have a stronger foundation in basic Japanese. You can understand familiar topics in conversations and read passages on daily subjects. The vocabulary count increases to around 1,500 words, and you should know about 300 kanji.
            N4_PLUS (Elementary+) :This level is for those who have a good grasp of basic Japanese but want to improve their understanding of more complex sentences and expressions. You'll need to know about 200 kanji and roughly 1,800 vocabulary words. You can read and understand more complex sentences and expressions, and you can listen to and understand conversations about more complex topics at a more natural speed.
            N3 (Intermediate) :This is often considered the bridge between a basic and an advanced understanding of Japanese. You can read and understand slightly more complex written material and grasp the main points of everyday conversations at a more natural speed. The required vocabulary is around 3,750 words, and you should be familiar with about 650 kanji.
            N2 (Upper Intermediate) :At this level, you have a high degree of proficiency in Japanese. You can read and understand articles and commentary on a variety of topics, and you can understand the nuances of conversations and news reports. This level is often required for those who want to work in a Japanese company. You'll need to know about 1,000 kanji and 6,000 vocabulary words.
            N1 (Advanced) :This is the highest level of the test. Passing the N1 means you have a comprehensive grasp of Japanese in a wide range of situations, including in-depth discussions, complex readings, and formal settings. The number of required kanji and vocabulary is quite extensive, with no official list, but you can expect to need roughly 2,000 kanji and 10,000+ words.
            
        5. Each line must follow this exact CSV format: hiragana;romanization;translation;level
        
        
        EXAMPLES (do not copy these):
        ひらがな;hiragana;hiragana script;1
        さかな;sakana;fish;1
        
        Generate exactly $nQuestions DIFFERENT words about $topic now:
   
    """.trimIndent()

    fun getSentencesPrompt(topic: String, level: Level, nQuestions: Int): String = """
        You are a Japanese language teacher. Generate exactly $nQuestions UNIQUE Japanese sentences about "$topic".
        
        STRICT REQUIREMENTS:
        1. Each sentence must be DIFFERENT (no duplicates)
        2. Use ONLY hiragana characters
        3. Sentences must be simple and grammatically correct
        4. Difficulty level based on JLPT Japanese language profiency test : $level 
            N5 (Beginner) :This is the starting point. At this level, you can understand some basic Japanese phrases and expressions. You'll need to know about 100 kanji and roughly 800 vocabulary words. You can read simple, short sentences written in hiragana, katakana, and basic kanji. You can also listen to and understand conversations about everyday topics spoken slowly.(In this level you have to generate sentences with ONLY hiragana characters)
            N5_PLUS (Beginner+) :This level is for those who have a good grasp of basic Japanese but want to improve their understanding of more complex sentences and expressions. You'll need to know about 150 kanji and roughly 1,200 vocabulary words. You can read and understand more complex sentences and expressions, and you can listen to and understand conversations about more complex topics at a more natural speed.
            N4 (Elementary) :At this level, you have a stronger foundation in basic Japanese. You can understand familiar topics in conversations and read passages on daily subjects. The vocabulary count increases to around 1,500 words, and you should know about 300 kanji.
            N4_PLUS (Elementary+) :This level is for those who have a good grasp of basic Japanese but want to improve their understanding of more complex sentences and expressions. You'll need to know about 200 kanji and roughly 1,800 vocabulary words. You can read and understand more complex sentences and expressions, and you can listen to and understand conversations about more complex topics at a more natural speed.
            N3 (Intermediate) :This is often considered the bridge between a basic and an advanced understanding of Japanese. You can read and understand slightly more complex written material and grasp the main points of everyday conversations at a more natural speed. The required vocabulary is around 3,750 words, and you should be familiar with about 650 kanji.
            N2 (Upper Intermediate) :At this level, you have a high degree of proficiency in Japanese. You can read and understand articles and commentary on a variety of topics, and you can understand the nuances of conversations and news reports. This level is often required for those who want to work in a Japanese company. You'll need to know about 1,000 kanji and 6,000 vocabulary words.
            N1 (Advanced) :This is the highest level of the test. Passing the N1 means you have a comprehensive grasp of Japanese in a wide range of situations, including in-depth discussions, complex readings, and formal settings. The number of required kanji and vocabulary is quite extensive, with no official list, but you can expect to need roughly 2,000 kanji and 10,000+ words.
        5. Each line must follow this exact CSV format: hiragana;romanization;translation;level
        
        EXAMPLES (do not copy these):
        きょうはあついです;kyou wa atsui desu;Today is hot;$level
        わたしはがくせいです;watashi wa gakusei desu;I am a student;$level
        
        Generate exactly $nQuestions DIFFERENT sentences about $topic now:
    """.trimIndent()
}