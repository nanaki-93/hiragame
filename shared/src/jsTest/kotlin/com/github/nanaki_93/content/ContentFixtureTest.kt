package com.github.nanaki_93.content

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Node reads the repository's shared conformance corpus, never a second curriculum copy. */
class ContentFixtureTest {
    private val fs: dynamic = js("require('fs')")
    private val path: dynamic = js("require('path')")

    private fun fixture(name: String): String {
        var directory: String = js("process.cwd()") as String
        while (true) {
            val file = path.join(directory, "tools", "fixtures", "content", name) as String
            if (fs.existsSync(file) as Boolean) return fs.readFileSync(file, "utf8") as String
            val parent = path.dirname(directory) as String
            if (parent == directory) error("Cannot locate tools/fixtures/content from Node working directory")
            directory = parent
        }
    }

    @Test fun positiveDocumentsAndExerciseDiscriminators() {
        val catalog = ContentCodec.decodeCatalog(fixture("catalog-valid.json"))
        assertEquals(catalog, ContentCodec.decodeCatalog(Json.encodeToString(catalog)))
        val lesson = ContentCodec.decodeLesson(fixture("lesson-valid.json"))
        assertEquals(lesson, ContentCodec.decodeLesson(Json.encodeToString(lesson)))
        val practice = ContentCodec.decodePracticeSet(fixture("practice-valid.json"))
        assertEquals(practice, ContentCodec.decodePracticeSet(Json.encodeToString(practice)))
        assertEquals(setOf("choice", "reading", "completion", "production"), practice.exercises.map {
            when (it) {
                is ChoiceExercise -> "choice"
                is ReadingExercise -> "reading"
                is CompletionExercise -> "completion"
                is ProductionExercise -> "production"
            }
        }.toSet())
        assertTrue(lesson.conversationGraph != null)
    }

    /** Load the checked-in public tree directly, without a test-only curriculum copy or HTTP. */
    @Test fun canonicalFoundationalPracticeDecodesAndResolves() {
        var directory: String = js("process.cwd()") as String
        var root: String
        while (true) {
            root = path.join(directory, "site", "src", "jsMain", "resources", "public", "content") as String
            if (fs.existsSync(path.join(root, "catalog.json")) as Boolean) break
            val parent = path.dirname(directory) as String
            if (parent == directory) error("Cannot locate canonical content from Node working directory")
            directory = parent
        }
        val catalog = ContentCodec.decodeCatalog(fs.readFileSync(path.join(root, "catalog.json"), "utf8") as String)
        assertEquals(1, catalog.topics.size)
        assertEquals("kana-foundations", catalog.topics.single().id)
        assertTrue(catalog.audioAssets.isEmpty())
        val entry = catalog.entries.single()
        assertEquals(DocumentKind.PRACTICE, entry.kind)
        assertEquals("practice-kana-a-i", entry.id)
        assertEquals("practice/kana-a-i.json", entry.path)
        val practice = ContentCodec.decodePracticeSet(fs.readFileSync(path.join(root, entry.path), "utf8") as String)
        assertEquals(entry.id, practice.id)
        assertEquals(entry.topicId, practice.topicId)
        assertEquals(catalog.formatVersion, practice.formatVersion)
        assertEquals(catalog.contentVersion, practice.contentVersion)
        assertEquals(ReviewStatus.REVIEWED, practice.review.status)
        assertEquals(ReviewerType.AGENT, practice.review.reviewerType)
        assertEquals(RightsStatus.PUBLISHABLE, practice.review.rights)
        assertEquals("f01-seed.md", practice.review.reviewNote)
        assertEquals(practice, ContentCodec.decodePracticeSet(Json.encodeToString(practice)))
        val choice = practice.exercises.filterIsInstance<ChoiceExercise>().single()
        assertEquals("exercise-kana-a-choice", choice.id)
        assertEquals("option-kana-a", choice.correctOptionId)
        assertEquals(listOf("あ", "い"), choice.options.map { it.text!!.surface })
        assertEquals(listOf("あ", "い"), choice.options.map { it.text!!.reading })
        assertTrue(choice.options.all { it.text!!.gloss != null })
        val reading = practice.exercises.filterIsInstance<ReadingExercise>().single()
        assertEquals("exercise-kana-i-reading", reading.id)
        assertEquals("い", reading.stimulus.reading)
        assertEquals(AnswerRepresentation.ROMAJI, reading.answerRepresentation)
        assertEquals(listOf("i"), reading.acceptedAnswers.map { (it as RomajiReadingAnswer).text })
        assertEquals(practice.exercises.map { it.id }.toSet(), practice.reviewItems.map { it.targetId }.toSet())
        assertTrue(practice.reviewItems.all { it.targetKind == ReviewTargetKind.EXERCISE })
    }

    @Test fun negativeSharedFixturesFailClosed() {
        for (name in listOf("duplicate-key", "unknown-field", "unsupported-version", "malformed", "missing", "invalid-id", "blank", "unknown-enum")) {
            assertFailsWith<Exception>(name) { ContentCodec.decodeCatalog(fixture("catalog-$name.json")) }
        }
        for (name in listOf("unknown-type", "incompatible")) {
            assertFailsWith<Exception>(name) { ContentCodec.decodePracticeSet(fixture("practice-$name.json")) }
        }
    }
}
