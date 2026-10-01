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

    @Test fun completionDocumentFixturesRequireProducibleExamples() {
        val valid = ContentCodec.decodePracticeSet(fixture("practice-completion-multiple-valid.json"))
        val completion = valid.exercises.single() as CompletionExercise
        assertEquals("連絡します。", completion.expectedCompletedExample.surface)
        assertEquals("連絡", completion.acceptedAnswers[1].surface)
        assertEquals(valid, ContentCodec.decodePracticeSet(Json.encodeToString(valid)))
        assertFailsWith<Exception> {
            ContentCodec.decodePracticeSet(fixture("practice-completion-impossible.json"))
        }
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
        assertEquals(2, catalog.topics.size)
        assertTrue(catalog.topics.any { it.id == "kana-foundations" })
        assertTrue(catalog.audioAssets.isEmpty())
        val entry = catalog.entries.single { it.kind == DocumentKind.PRACTICE }
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

    /** Canonical lesson and practice live in the public tree; exercise and graph wire types round-trip. */
    @Test fun canonicalClarificationLessonDecodesAndBranches() {
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
        assertEquals(2, catalog.entries.size)
        val entry = catalog.entries.single { it.kind == DocumentKind.LESSON }
        assertTrue(catalog.topics.any { it.id == entry.topicId })
        val lesson = ContentCodec.decodeLesson(fs.readFileSync(path.join(root, entry.path), "utf8") as String)
        assertEquals(entry.id, lesson.id)
        assertEquals(entry.topicId, lesson.topicId)
        assertEquals(catalog.formatVersion, lesson.formatVersion)
        assertEquals(catalog.contentVersion, lesson.contentVersion)
        assertTrue(lesson.prerequisiteLessonIds.all { id -> catalog.entries.any { it.id == id && it.kind == DocumentKind.LESSON } })
        assertEquals(ReviewStatus.REVIEWED, lesson.review.status)
        assertEquals(ReviewerType.AGENT, lesson.review.reviewerType)
        assertEquals(RightsStatus.PUBLISHABLE, lesson.review.rights)
        assertEquals("f01-seed.md", lesson.review.reviewNote)
        assertEquals(lesson, ContentCodec.decodeLesson(Json.encodeToString(lesson)))
        assertEquals(listOf("turn-time", "turn-check", "turn-confirm"), lesson.dialogue.turns.map { it.id })
        val meetingStatement = lesson.dialogue.turns.first().text
        assertEquals("会議は三時です。", meetingStatement.surface)
        assertEquals("かいぎわさんじです。", meetingStatement.reading)
        assertEquals("は", meetingStatement.segments[1].surface)
        assertEquals("わ", meetingStatement.segments[1].reading)
        assertEquals("さんじ", meetingStatement.segments[2].reading)
        assertTrue(lesson.dialogue.turns.all { turn -> lesson.dialogue.speakers.any { it.id == turn.speakerId } })
        assertEquals(setOf("phrase-three-right", "phrase-excuse-me"), lesson.phrases.map { it.id }.toSet())
        assertEquals(setOf("phrase-three-right", "exercise-complete-time"), lesson.reviewItems.map { it.targetId }.toSet())
        val choice = lesson.exercises.filterIsInstance<ChoiceExercise>().single()
        val completion = lesson.exercises.filterIsInstance<CompletionExercise>().single()
        val production = lesson.exercises.filterIsInstance<ProductionExercise>().single()
        assertEquals("option-check-three", choice.correctOptionId)
        assertEquals("三時", completion.acceptedAnswers.single().surface)
        assertEquals("三時ですね。", completion.expectedCompletedExample.surface)
        assertTrue(production.criteria.isNotEmpty())
        val graph = lesson.conversationGraph!!
        assertEquals(graph, ContentCodec.decodeLesson(Json.encodeToString(lesson)).conversationGraph)
        val nodes = graph.nodes.associateBy { it.id }
        val graphStatement = (nodes["node-time"] as PromptNode).text
        assertEquals(meetingStatement.surface, graphStatement.surface)
        assertEquals(meetingStatement.reading, graphStatement.reading)
        assertEquals(meetingStatement.segments, graphStatement.segments)
        assertTrue(graph.entryNodeId in nodes)
        val branch = nodes["node-choice"] as ChoiceInteractionNode
        assertEquals(choice.options.map { it.id }.toSet(), branch.transitions.map { it.optionId }.toSet())
        assertEquals(setOf("node-completion", "node-guidance"), branch.transitions.map { it.nextNodeId }.toSet())
        assertEquals(completion.id, (nodes["node-completion"] as CompletionInteractionNode).exerciseId)
        assertTrue(nodes.values.filterIsInstance<PromptNode>().all { it.nextNodeId in nodes })
        assertTrue(branch.transitions.all { it.nextNodeId in nodes && it.feedback.isNotBlank() })
        assertTrue(nodes[(nodes["node-completion"] as CompletionInteractionNode).nextNodeId] is TerminalNode)
        assertTrue(catalog.audioAssets.isEmpty())
    }

    @Test fun negativeSharedFixturesFailClosed() {
        for (name in listOf("duplicate-key", "unknown-field", "unsupported-version", "malformed", "missing", "invalid-id", "blank", "unknown-enum")) {
            assertFailsWith<Exception>(name) { ContentCodec.decodeCatalog(fixture("catalog-$name.json")) }
        }
        assertFailsWith<Exception>("catalog-out-of-range-content-version") {
            ContentCodec.decodeCatalog(fixture("catalog-out-of-range-content-version.json"))
        }
        for (name in listOf("missing-phrases", "missing-review-items", "null-phrases", "null-review-items",
                            "out-of-range-content-version", "out-of-range-duration")) {
            assertFailsWith<Exception>("lesson-$name") { ContentCodec.decodeLesson(fixture("lesson-$name.json")) }
        }
        // Practice collections have defaults when absent, but an explicit null is not an empty collection.
        for (field in listOf("phrases", "reviewItems")) {
            val practice = fixture("practice-valid.json").replace("\"exercises\":", "\"$field\":null,\"exercises\":")
            assertFailsWith<Exception>("practice-$field-null") { ContentCodec.decodePracticeSet(practice) }
        }
        for (name in listOf("unknown-type", "incompatible")) {
            assertFailsWith<Exception>(name) { ContentCodec.decodePracticeSet(fixture("practice-$name.json")) }
        }
    }
}
