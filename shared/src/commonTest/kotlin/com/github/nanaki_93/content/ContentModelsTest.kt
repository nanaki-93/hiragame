package com.github.nanaki_93.content

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentModelsTest {
    private val json = Json { encodeDefaults = true }
    private val text = JapaneseText(
        surface = "確認します。", reading = "かくにんします。", translation = "I will confirm.",
        segments = listOf(TextSegment("確認", "かくにん"), TextSegment("します。")),
        context = "At work", register = "polite",
    )
    private val review = ReviewMetadata(
        ReviewStatus.REVIEWED, ReviewerType.AGENT, "2026-10-01", "notes/seed.md",
        "Independently authored", RightsStatus.PUBLISHABLE, "Original writing",
    )

    @Test fun catalogRoundTripAndStableIdentity() {
        val topic = Topic("topic_kana", "Kana", "Foundations")
        val entry = ContentEntry("550e8400-e29b-41d4-a716-446655440000", DocumentKind.LESSON, topic.id, "lessons/one.json")
        val catalog = ContentCatalog(1, 1, listOf(topic), listOf(entry))
        val wire = json.encodeToString(catalog)
        assertTrue(wire.contains("\"kind\":\"lesson\""))
        assertEquals(catalog, json.decodeFromString<ContentCatalog>(wire))
        assertEquals(emptyList(), json.decodeFromString<ContentCatalog>("""{"formatVersion":1,"contentVersion":1,"topics":[],"entries":[]}""").audioAssets)
        val edited = catalog.copy(contentVersion = 2, topics = listOf(topic.copy(title = "Kana revised")))
        assertEquals(catalog.topics.single().id, edited.topics.single().id)
        assertEquals(catalog.entries.single().id, edited.entries.single().id)
        assertFailsWith<IllegalArgumentException> { catalog.copy(entries = listOf(entry, entry)) }
        assertFailsWith<IllegalArgumentException> { Topic("bad.id", "Kana", "Foundations") }
        assertFailsWith<IllegalArgumentException> { catalog.copy(formatVersion = 2) }
        assertFailsWith<IllegalArgumentException> { catalog.copy(contentVersion = 0) }
    }

    @Test fun authoredTextSegmentsAndAudio() {
        assertEquals(text, json.decodeFromString<JapaneseText>(json.encodeToString(text)))
        assertFailsWith<IllegalArgumentException> {
            text.copy(segments = listOf(TextSegment("確認", "かくにん")))
        }
        assertFailsWith<IllegalArgumentException> { TextSegment("漢字") }
        // Supplementary CJK (including compatibility forms) must not bypass the reading rule.
        for (ideograph in listOf("𠮷", "你", "𰀀")) {
            assertFailsWith<IllegalArgumentException> { TextSegment("A${ideograph}。") }
            assertFailsWith<IllegalArgumentException> {
                json.decodeFromString<TextSegment>("""{"surface":"${ideograph}"}""")
            }
            assertEquals("A${ideograph}。", TextSegment("A${ideograph}。", "よし").surface)
        }
        assertFailsWith<IllegalArgumentException> { text.copy(reading = " ") }
        assertFailsWith<IllegalArgumentException> { text.copy(translation = null) }
        assertFailsWith<IllegalArgumentException> { text.copy(gloss = "sound") }
        assertFailsWith<IllegalArgumentException> { text.copy(translation = null, gloss = "sound") }
        val kana = JapaneseText("あ", "あ", gloss = "the sound a")
        assertEquals(kana, json.decodeFromString<JapaneseText>(json.encodeToString(kana)))
        assertEquals("カ", JapaneseText("カ", "カ", gloss = "the sound ka").surface)
        for (word in listOf("こんにちは", "カタカナ", "きゃ", "ー")) {
            assertFailsWith<IllegalArgumentException> { JapaneseText(word, word, gloss = "sound") }
            assertFailsWith<IllegalArgumentException> {
                json.decodeFromString<JapaneseText>("""{"surface":"${word}","reading":"${word}","gloss":"sound"}""")
            }
        }
        val phrase = Phrase("phrase_1", text, "Confirm a detail", "polite")
        val wire = Json.encodeToString(phrase)
        assertFalse(wire.contains("audioId"))
        assertEquals(null, Json.decodeFromString<Phrase>(wire).audioId)
    }

    @Test fun lessonAndPracticeRoundTripAndRequiredValues() {
        val phrase = Phrase("phrase_1", text, "Confirm a detail", "polite", sourceTurnId = "turn_1")
        val lesson = Lesson(
            1, 1, "lesson_1", "topic_kana", "Clarification", "Office", "Confirm details",
            AdvisoryDifficulty.BEGINNER, 5, emptyList(),
            Dialogue(listOf(Speaker("speaker_1", "A", "Colleague")), listOf(DialogueTurn("turn_1", "speaker_1", text))),
            listOf(phrase), listOf(GrammarNote("grammar_1", "Polite form", listOf(text))),
            RolePlayObjective("Confirm a detail", listOf("Ask a question")),
            listOf(ReviewItem("review_1", ReviewTargetKind.PHRASE, phrase.id, "reading", "Japanese to meaning")), review,
            exercises = emptyList(),
        )
        val practice = PracticeSet(1, 1, "practice_1", "topic_kana", "Reading", "Kana review", review = review, exercises = emptyList())
        assertEquals(lesson, json.decodeFromString<Lesson>(json.encodeToString(lesson)))
        assertEquals(practice, Json.decodeFromString<PracticeSet>(Json.encodeToString(practice)))
        assertFailsWith<IllegalArgumentException> { lesson.copy(title = " ") }
        assertFailsWith<IllegalArgumentException> { practice.copy(description = "") }
        assertFailsWith<IllegalArgumentException> { lesson.copy(durationMinutes = 0) }
        assertFailsWith<Exception> {
            Json.decodeFromString<PracticeSet>("""{"formatVersion":1,"contentVersion":1,"id":"practice_1","topicId":"topic_kana","title":"Reading","review":{}}""")
        }
    }
}
