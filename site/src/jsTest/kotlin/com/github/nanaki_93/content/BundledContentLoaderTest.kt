package com.github.nanaki_93.content

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BundledContentLoaderTest {
    private val review = """{"status":"reviewed","reviewerType":"agent","reviewDate":"2026-10-01","reviewNote":"seed.md","provenance":"Original","rights":"publishable","rightsBasis":"Original"}"""
    private val choice = """{"type":"choice","id":"question-1","prompt":"Which?","options":[{"id":"yes","label":"Yes"},{"id":"no","label":"No"}],"correctOptionId":"yes","explanation":"Yes."}"""
    private val lessonChoice = choice.replace("\"question-1\"", "\"lesson-question\"")
    private val practicePath = "practice/nested/one.json"
    private val lessonPath = "lessons/nested/one.json"
    private fun entry(id: String, kind: String, path: String): String {
        val escaped = path.replace("\\", "\\\\")
        return """{"id":"$id","kind":"$kind","topicId":"topic","path":"$escaped"}"""
    }
    private fun catalog(entries: String, topics: String = """[{"id":"topic","title":"Topic","description":"Description"}]""") =
        """{"formatVersion":1,"contentVersion":1,"topics":$topics,"entries":[$entries],"audioAssets":[{"id":"audio","path":"audio/optional.mp3","transcript":"Hi","permissionNote":"Own"}]}"""
    private fun practice(id: String = "set", topic: String = "topic", exercises: String = choice,
                         version: Int = 1, metadata: String = review, phrases: String = "",
                         items: String = "") =
        """{"formatVersion":1,"contentVersion":$version,"id":"$id","topicId":"$topic","title":"Practice","description":"Description","review":$metadata,"phrases":[$phrases],"reviewItems":[$items],"exercises":[$exercises]}"""
    private fun lesson(prerequisites: String = "", id: String = "lesson", metadata: String = review,
                       dialogue: String = """{"speakers":[],"turns":[]}""", phrases: String = "",
                       items: String = "", exercises: String = "", graph: String = "null") =
        """{"formatVersion":1,"contentVersion":1,"id":"$id","topicId":"topic","title":"Lesson","situation":"At work","communicationGoal":"Ask","difficulty":"beginner","durationMinutes":5,"prerequisiteLessonIds":[$prerequisites],"dialogue":$dialogue,"phrases":[$phrases],"grammarNotes":[],"rolePlay":{"task":"Ask","criteria":["Polite"]},"reviewItems":[$items],"review":$metadata,"exercises":[$exercises],"conversationGraph":$graph}"""
    private val jp = """{"surface":"あ","reading":"あ","gloss":"a"}"""
    private fun phrase(source: String? = null, id: String = "phrase-1") =
        """{"id":"$id","text":$jp,"usage":"Greeting","register":"plain"${source?.let { ",\"sourceTurnId\":\"$it\"" } ?: ""}}"""
    private fun item(kind: String, target: String, id: String = "item-1") =
        """{"id":"$id","targetKind":"$kind","targetId":"$target","skill":"read","direction":"prompt"}"""
    private val both = entry("set", "practice", practicePath) + "," + entry("lesson", "lesson", lessonPath)

    private class Fake(val files: Map<String, String>) : ContentTextSource {
        val reads = mutableListOf<String>()
        override suspend fun readText(relativePath: String): String {
            reads += relativePath
            return files[relativePath] ?: error("missing fixture: $relativePath")
        }
    }
    private fun fake(manifest: String = catalog(both), practiceDoc: String = practice(),
                     lessonDoc: String = lesson()) = Fake(mapOf("catalog.json" to manifest,
        practicePath to practiceDoc, lessonPath to lessonDoc))
    private suspend fun failure(source: Fake, path: String): BundledContentException {
        val exception = assertFailsWith<BundledContentException> { BundledContentLoader(source).load() }
        assertEquals(path, exception.affectedPath)
        return exception
    }

    @Test fun mixedNestedContentIsFullyLoadedWithoutAudio() = runTest {
        val source = fake()
        val result = assertIs<CatalogLoad.Ready>(BundledContentLoader(source).load())
        assertEquals(listOf("catalog.json", practicePath, lessonPath), source.reads)
        assertEquals("question-1", result.content.practiceSets.getValue("set").exercises.single().id)
        assertEquals("lesson", result.content.lessons.getValue("lesson").id)
        assertEquals(1, result.content.catalog.contentVersion)
    }

    @Test fun everyManifestPathIsCheckedBeforeAnyDocumentRead() = runTest {
        val unsafe = listOf("/practice/one.json", "https://host/one.json", "../one.json",
            "a/../one.json", "a\\one.json", "a?x/one.json", "a#x/one.json",
            "a/%2e%2e/one.json", "a/%2Fone.json", "a/%5cone.json", "a/%252e/one.json",
            "a//one.json", "a/./one.json", "catalog.json", "//host/one.json")
        for (path in unsafe) {
            val source = fake(manifest = catalog(entry("set", "practice", practicePath) + "," +
                entry("lesson", "lesson", path)))
            failure(source, path)
            assertEquals(listOf("catalog.json"), source.reads, path)
        }
        val duplicate = fake(manifest = catalog(entry("set", "practice", practicePath) + "," +
            entry("lesson", "lesson", practicePath)))
        failure(duplicate, practicePath)
        assertEquals(listOf("catalog.json"), duplicate.reads)
    }

    @Test fun malformedHtmlUnsupportedAndWrongKindFailClosed() = runTest {
        for (bad in listOf("<html>Not found</html>", "{broken", practice(version = 2),
            practice().replace("\"formatVersion\":1", "\"formatVersion\":2"),
            lesson())) {
            val source = fake(practiceDoc = bad)
            failure(source, practicePath)
            assertEquals(listOf("catalog.json", practicePath), source.reads)
        }
        failure(fake(manifest = "<html>Error</html>"), "catalog.json")
        failure(fake(manifest = catalog(both).replace("\"formatVersion\":1", "\"formatVersion\":2")), "catalog.json")
    }

    @Test fun mismatchedIdentityTopicVersionsAndReviewFailClosed() = runTest {
        val unreviewed = review.replace("\"reviewed\"", "\"unreviewed\"")
        val uncleared = review.replace("\"publishable\"", "\"notCleared\"")
        for (doc in listOf(practice(id = "other"), practice(topic = "other"), practice(version = 2),
            practice(metadata = unreviewed), practice(metadata = uncleared))) {
            failure(fake(practiceDoc = doc), practicePath)
        }
        failure(fake(manifest = catalog(entry("set", "practice", practicePath), topics = "[]")), practicePath)
        failure(fake(lessonDoc = lesson(metadata = uncleared)), lessonPath)
        failure(fake(lessonDoc = lesson().replace("\"topicId\":\"topic\"", "\"topicId\":\"other\"")), lessonPath)
    }

    @Test fun missingAndCyclicLessonReferencesRejectWholeSnapshot() = runTest {
        failure(fake(lessonDoc = lesson(prerequisites = "\"absent\"")), lessonPath)
        failure(fake(lessonDoc = lesson(prerequisites = "\"lesson\"")), lessonPath)
    }

    @Test fun documentLocalReviewAndSourceTurnReferencesMustResolveInTheSameDocument() = runTest {
        for (doc in listOf(
            practice(items = item("exercise", "absent")),
            practice(items = item("phrase", "absent")),
            practice(phrases = phrase("absent")),
            // A target in the other document cannot satisfy a local reference.
            practice(items = item("phrase", "phrase-1")),
        )) {
            val error = failure(fake(practiceDoc = doc, lessonDoc = lesson(phrases = phrase())), practicePath)
            assertTrue(error.message!!.contains("local reference"))
        }
        for (doc in listOf(
            lesson(items = item("exercise", "absent")),
            lesson(items = item("phrase", "absent")),
            lesson(phrases = phrase("absent")),
        )) failure(fake(lessonDoc = doc), lessonPath)
        // Empty classifications must not hide invalid listed lessons.
        failure(fake(manifest = catalog(entry("lesson", "lesson", lessonPath)),
            lessonDoc = lesson(items = item("exercise", "absent"))), lessonPath)
        assertIs<CatalogLoad.Ready>(BundledContentLoader(fake(
            practiceDoc = practice(phrases = phrase(), items = item("phrase", "phrase-1")),
            lessonDoc = lesson(exercises = lessonChoice,
                items = item("exercise", "lesson-question", id = "lesson-item"))
        )).load())
    }

    @Test fun lessonTurnsAndConversationReferencesAreValidatedBeforeReady() = runTest {
        val badTurn = """{"speakers":[],"turns":[{"id":"turn-1","speakerId":"missing","text":$jp}]}"""
        assertTrue(failure(fake(lessonDoc = lesson(dialogue = badTurn)), lessonPath)
            .message!!.contains("speakerId"))
        val dialogue = """{"speakers":[{"id":"speaker-1","name":"A","role":"Teacher"}],"turns":[{"id":"turn-1","speakerId":"speaker-1","text":$jp}]}"""
        val graph = """{"entryNodeId":"start","nodes":[{"type":"prompt","id":"start","speakerId":"speaker-1","text":$jp,"nextNodeId":"choice"},{"type":"choiceInteraction","id":"choice","exerciseId":"lesson-question","transitions":[{"optionId":"yes","feedback":"Yes","nextNodeId":"end"},{"optionId":"no","feedback":"No","nextNodeId":"end"}]},{"type":"terminal","id":"end","message":"Done"}]}"""
        assertIs<CatalogLoad.Ready>(BundledContentLoader(fake(lessonDoc = lesson(
            dialogue = dialogue, phrases = phrase("turn-1"), exercises = lessonChoice, graph = graph
        ))).load())
        for (bad in listOf(
            graph.replace("\"speakerId\":\"speaker-1\"", "\"speakerId\":\"missing\""),
            graph.replace("\"exerciseId\":\"lesson-question\"", "\"exerciseId\":\"missing\""),
            graph.replace("\"optionId\":\"no\"", "\"optionId\":\"missing\""),
            graph.replace("{\"optionId\":\"no\",\"feedback\":\"No\",\"nextNodeId\":\"end\"}",
                "{\"optionId\":\"yes-other\",\"feedback\":\"No\",\"nextNodeId\":\"end\"}"),
            graph.replace("\"nextNodeId\":\"end\"", "\"nextNodeId\":\"missing\""),
            graph.replace("\"nextNodeId\":\"end\"", "\"nextNodeId\":\"start\""),
        )) failure(fake(lessonDoc = lesson(dialogue = dialogue, exercises = lessonChoice, graph = bad)), lessonPath)
        val wrongType = graph.replace("\"type\":\"choiceInteraction\"", "\"type\":\"completionInteraction\"")
            .replace("\"transitions\":[{\"optionId\":\"yes\",\"feedback\":\"Yes\",\"nextNodeId\":\"end\"},{\"optionId\":\"no\",\"feedback\":\"No\",\"nextNodeId\":\"end\"}]",
                "\"feedback\":\"Try\",\"nextNodeId\":\"end\"")
        failure(fake(lessonDoc = lesson(dialogue = dialogue, exercises = lessonChoice, graph = wrongType)), lessonPath)
    }

    @Test fun duplicateIdsAcrossPracticeAndLessonRejectWholeSnapshot() = runTest {
        val duplicates = listOf(
            "exercises" to fake(lessonDoc = lesson(exercises = choice)),
            "phrases" to fake(practiceDoc = practice(phrases = phrase()),
                lessonDoc = lesson(phrases = phrase())),
            "reviewItems" to fake(practiceDoc = practice(items = item("exercise", "question-1")),
                lessonDoc = lesson(exercises = lessonChoice, items = item("exercise", "lesson-question"))),
        )
        for ((kind, source) in duplicates) {
            val error = failure(source, lessonPath)
            assertTrue(error.message!!.contains("duplicate catalog-wide $kind ID"), kind)
            assertEquals(listOf("catalog.json", practicePath, lessonPath), source.reads)
        }
    }

    @Test fun duplicateIdsAcrossPracticeDocumentsAlsoRejectEmptyClassification() = runTest {
        val secondPath = "practice/nested/two.json"
        val manifest = catalog(entry("set", "practice", practicePath) + "," +
            entry("second", "practice", secondPath))
        val duplicates = listOf(
            "exercises" to (practice() to practice(id = "second")),
            "phrases" to (practice(exercises = "", phrases = phrase()) to
                practice(id = "second", exercises = "", phrases = phrase())),
            "reviewItems" to (practice(exercises = "", phrases = phrase(), items = item("phrase", "phrase-1")) to
                practice(id = "second", exercises = "", phrases = phrase(id = "phrase-2"),
                    items = item("phrase", "phrase-2"))),
        )
        for ((kind, documents) in duplicates) {
            val source = Fake(mapOf("catalog.json" to manifest, practicePath to documents.first,
                secondPath to documents.second))
            val error = failure(source, secondPath)
            assertTrue(error.message!!.contains("duplicate catalog-wide $kind ID"), kind)
            assertEquals(listOf("catalog.json", practicePath, secondPath), source.reads)
        }
    }

    @Test fun laterBrokenDocumentNeverReturnsPartialReady() = runTest {
        val source = fake(lessonDoc = "<html>Not JSON</html>")
        failure(source, lessonPath)
        assertEquals(listOf("catalog.json", practicePath, lessonPath), source.reads)
        val missing = Fake(mapOf("catalog.json" to catalog(both), practicePath to practice()))
        assertTrue(failure(missing, lessonPath).message!!.contains("unable to read"))
        assertEquals(listOf("catalog.json", practicePath, lessonPath), missing.reads)
    }

    @Test fun emptyCasesAreDistinctFromMalformedContent() = runTest {
        assertEquals(EmptyContentReason.EMPTY_CATALOG,
            assertIs<CatalogLoad.Empty>(BundledContentLoader(fake(manifest = catalog(""))).load()).reason)
        assertEquals(EmptyContentReason.NO_PRACTICE,
            assertIs<CatalogLoad.Empty>(BundledContentLoader(fake(manifest = catalog(entry("lesson", "lesson", lessonPath)))).load()).reason)
        assertEquals(EmptyContentReason.EMPTY_PRACTICE_SETS,
            assertIs<CatalogLoad.Empty>(BundledContentLoader(fake(practiceDoc = practice(exercises = ""))).load()).reason)
        val missing = fake(manifest = catalog(both).replace("\"topicId\":\"topic\"", "\"topicId\":\"absent\""))
        assertTrue(failure(missing, practicePath).message!!.contains("topic"))
        assertEquals(listOf("catalog.json"), missing.reads)
    }
}
