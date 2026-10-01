package com.github.nanaki_93.content

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test fun browserSourceOnlyAddressesOwnedStaticPathsAndRejectsHttpBeforeDecoding() = runTest {
        val urls = mutableListOf<String>()
        val source = BrowserContentTextSource(object : ContentHttpTransport {
            override suspend fun get(url: String): ContentHttpResponse {
                urls += url
                return ContentHttpResponse(404, false, "<html>login</html>")
            }
        })
        for (path in listOf("/other.json", "../escape.json", "https://elsewhere/x.json",
            "practice/%2e%2e/x.json", "x.json?api=1", "x\\\\y.json")) {
            assertFailsWith<IllegalArgumentException> { source.readText(path) }
        }
        assertTrue(urls.isEmpty())
        val error = assertFailsWith<BundledContentException> { BundledContentLoader(source).load() }
        assertEquals("catalog.json", error.affectedPath)
        assertTrue(error.message!!.contains("HTTP 404"))
        assertTrue(!error.message!!.contains("login"))
        assertEquals(listOf("/hiragame/content/catalog.json"), urls)
        assertFailsWith<ContentHttpException> { source.readText("practice/nested/one.json") }
        assertEquals("/hiragame/content/practice/nested/one.json", urls.last())
    }

    @Test fun laterHttpFailureIdentifiesPathAndDoesNotPublishPartialContent() = runTest {
        val urls = mutableListOf<String>()
        val source = BrowserContentTextSource(object : ContentHttpTransport {
            override suspend fun get(url: String): ContentHttpResponse {
                urls += url
                return if (url.endsWith(lessonPath)) ContentHttpResponse(503, false, "untrusted body")
                    else ContentHttpResponse(200, true, fake().files.getValue(url.removePrefix("/hiragame/content/")))
            }
        })
        val error = assertFailsWith<BundledContentException> { BundledContentLoader(source).load() }
        assertEquals(lessonPath, error.affectedPath)
        assertTrue(error.message!!.contains("HTTP 503"))
        assertTrue(!error.message!!.contains("untrusted body"))
        assertEquals(listOf("catalog.json", practicePath, lessonPath).map { "/hiragame/content/$it" }, urls)
    }

    @Test fun individualReadTimesOutAndCancelsItsTransport() = runTest {
        val reads = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        val source = BrowserContentTextSource(object : ContentHttpTransport {
            override suspend fun get(url: String): ContentHttpResponse {
                val path = url.removePrefix("/hiragame/content/")
                reads += url
                if (path == lessonPath) {
                    try { awaitCancellation() } finally { cancelled += url }
                }
                return ContentHttpResponse(200, true, fake().files.getValue(path))
            }
        })
        val pending = async { assertFailsWith<BundledContentException> { BundledContentLoader(source).load() } }
        runCurrent()
        assertEquals(listOf("catalog.json", practicePath, lessonPath).map { "/hiragame/content/$it" }, reads)
        advanceTimeBy(10_000)
        runCurrent()
        val error = pending.await()
        assertEquals(lessonPath, error.affectedPath)
        assertTrue(error.message!!.contains("10 seconds"))
        assertEquals(listOf("/hiragame/content/$lessonPath"), cancelled)
    }

    @Test fun wholeLoadDeadlineCancelsLaterReadWithoutPublishingReady() = runTest {
        val thirdPath = "practice/nested/two.json"
        val manifest = catalog(both + "," + entry("second", "practice", thirdPath))
        val documents = fake().files + ("catalog.json" to manifest) +
            (thirdPath to practice(id = "second", exercises = choice.replace("question-1", "question-2")))
        val reads = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        val source = object : ContentTextSource {
            override suspend fun readText(relativePath: String): String {
                reads += relativePath
                try {
                    delay(9_000)
                    return documents.getValue(relativePath)
                } finally {
                    // Only the fourth read is interrupted by the 30-second deadline.
                    if (relativePath == thirdPath) cancelled += relativePath
                }
            }
        }
        val pending = async { assertFailsWith<BundledContentException> { BundledContentLoader(source).load() } }
        advanceTimeBy(30_000)
        runCurrent()
        val error = pending.await()
        assertEquals(thirdPath, error.affectedPath)
        assertTrue(error.message!!.contains("30 seconds"))
        assertEquals(listOf("catalog.json", practicePath, lessonPath, thirdPath), reads)
        assertEquals(listOf(thirdPath), cancelled)
    }

    @Test fun callerCancellationIsPropagatedAndCancelsTheRead() = runTest {
        var cancelled = false
        val source = BrowserContentTextSource(object : ContentHttpTransport {
            override suspend fun get(url: String): ContentHttpResponse = try {
                assertEquals("/hiragame/content/catalog.json", url)
                awaitCancellation()
            } finally { cancelled = true }
        })
        val pending = async { BundledContentLoader(source).load() }
        runCurrent()
        pending.cancel()
        assertFailsWith<CancellationException> { pending.await() }
        assertTrue(cancelled)
    }

    @Test fun browserFetchAbortsWhenReadIsCancelledWithoutWindowOrDom() = runTest {
        val runtime: dynamic = js("globalThis")
        val previousFetch: dynamic = runtime.fetch
        val previousController: dynamic = runtime.AbortController
        try {
            runtime.AbortController = js("(function() { this.signal = {aborted: false}; this.abort = function() { this.signal.aborted = true; }; })")
            runtime.fetch = js("(function(url, options) { globalThis.__f02Signal = options.signal; return new Promise(function() {}); })")
            val pending = async { BrowserContentTextSource().readText("catalog.json") }
            runCurrent()
            assertTrue(runtime.__f02Signal != null)
            pending.cancel()
            assertFailsWith<CancellationException> { pending.await() }
            assertEquals(true, runtime.__f02Signal.aborted as Boolean)
        } finally {
            runtime.fetch = previousFetch
            runtime.AbortController = previousController
            js("delete globalThis.__f02Signal")
        }
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
