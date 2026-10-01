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

    @Test fun negativeSharedFixturesFailClosed() {
        for (name in listOf("duplicate-key", "unknown-field", "unsupported-version", "malformed", "missing", "invalid-id", "blank", "unknown-enum")) {
            assertFailsWith<Exception>(name) { ContentCodec.decodeCatalog(fixture("catalog-$name.json")) }
        }
        for (name in listOf("unknown-type", "incompatible")) {
            assertFailsWith<Exception>(name) { ContentCodec.decodePracticeSet(fixture("practice-$name.json")) }
        }
    }
}
