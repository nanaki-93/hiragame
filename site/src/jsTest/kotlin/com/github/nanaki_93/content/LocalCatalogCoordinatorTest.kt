package com.github.nanaki_93.content

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LocalCatalogCoordinatorTest {
    private val review = """{"status":"reviewed","reviewerType":"agent","reviewDate":"2026-10-01","reviewNote":"seed.md","provenance":"Original","rights":"publishable","rightsBasis":"Original"}"""
    private val topic = """{"id":"workplace","title":"Workplace","description":"At work"}"""
    private fun entry(id: String, kind: String, path: String) =
        """{"id":"$id","kind":"$kind","topicId":"workplace","path":"$path"}"""
    private fun catalog(entries: String) =
        """{"formatVersion":1,"contentVersion":1,"topics":[$topic],"entries":[$entries]}"""
    private fun lesson(id: String) =
        """{"formatVersion":1,"contentVersion":1,"id":"$id","topicId":"workplace","title":"Lesson","situation":"At work","communicationGoal":"Ask","difficulty":"beginner","durationMinutes":5,"prerequisiteLessonIds":[],"dialogue":{"speakers":[],"turns":[]},"phrases":[],"grammarNotes":[],"rolePlay":{"task":"Ask","criteria":["Polite"]},"reviewItems":[],"review":$review,"exercises":[],"conversationGraph":null}"""
    private fun practice() =
        """{"formatVersion":1,"contentVersion":1,"id":"set","topicId":"workplace","title":"Practice","description":"At work","review":$review,"phrases":[],"reviewItems":[],"exercises":[]}"""
    private fun loader(files: Map<String, String>) = BundledContentLoader(object : ContentTextSource {
        override suspend fun readText(relativePath: String): String = files[relativePath] ?: error("missing asset")
    })
    private fun lessonFiles() = mapOf("catalog.json" to catalog(entry("one", "lesson", "lessons/one.json")),
        "lessons/one.json" to lesson("one"))

    @Test fun validLessonOnlyAndMixedBundlesPublishOnlyAfterValidation() = runTest {
        for (files in listOf(lessonFiles(), lessonFiles() + mapOf(
            "catalog.json" to catalog(entry("set", "practice", "practice/set.json") + "," +
                entry("one", "lesson", "lessons/one.json")), "practice/set.json" to practice()))) {
            val coordinator = LocalCatalogCoordinator(this, loader(files))
            coordinator.load()
            assertIs<LocalCatalogState.Loading>(coordinator.state.value)
            runCurrent()
            val content = assertIs<LocalCatalogState.Ready>(coordinator.state.value).content
            assertEquals(listOf("one"), content.lessons.keys.toList())
            assertEquals(if ("practice/set.json" in files) listOf("set") else emptyList(),
                content.practiceSets.keys.toList())
            coordinator.dispose()
        }
    }

    @Test fun emptyManifestAndPracticeOnlyHaveDistinctEmptyStates() = runTest {
        val files = listOf(mapOf("catalog.json" to catalog("")) to CatalogEmptyReason.EMPTY_CATALOG,
            mapOf("catalog.json" to catalog(entry("set", "practice", "practice/set.json")),
                "practice/set.json" to practice()) to CatalogEmptyReason.NO_WORKPLACE_LESSONS)
        for ((assets, expected) in files) {
            val coordinator = LocalCatalogCoordinator(this, loader(assets))
            coordinator.load()
            runCurrent()
            assertEquals(LocalCatalogState.Empty(expected), coordinator.state.value)
            coordinator.dispose()
        }
    }

    @Test fun invalidLaterDocumentNeverLeaksEarlierValidatedLessonAndRetryRecovers() = runTest {
        var files = lessonFiles() + mapOf("catalog.json" to catalog(
            entry("one", "lesson", "lessons/one.json") + "," + entry("two", "lesson", "lessons/two.json")),
            "lessons/two.json" to "<html>untrusted server response</html>")
        val coordinator = LocalCatalogCoordinator(this) { loader(files).load() }
        coordinator.load()
        runCurrent()
        val error = assertIs<LocalCatalogState.Error>(coordinator.state.value)
        assertEquals(CatalogErrorKind.CONTENT, error.kind)
        assertEquals("Unable to load reviewed content. Retry the load.", error.safeMessage)
        assertTrue("html" !in error.safeMessage && "two.json" !in error.safeMessage)
        files = files + ("lessons/two.json" to lesson("two"))
        coordinator.retryLoad()
        assertIs<LocalCatalogState.Loading>(coordinator.state.value)
        runCurrent()
        assertEquals(listOf("one", "two"), assertIs<LocalCatalogState.Ready>(coordinator.state.value).content.lessons.keys.toList())
        coordinator.dispose()
    }

    @Test fun errorCategoriesHaveOnlyFixedTextAndCanBeRetried() = runTest {
        var failure: Exception? = BundledContentException("catalog.json", "secret response", ContentHttpException(403))
        val coordinator = LocalCatalogCoordinator(this) {
            failure?.let { throw it }
            loader(lessonFiles()).load()
        }
        for ((exception, kind, message) in listOf(
            Triple(BundledContentException("catalog.json", "secret response", ContentHttpException(403)),
                CatalogErrorKind.HTTP, "Unable to read bundled content from this site. Retry the load."),
            Triple(IllegalStateException("raw network body"), CatalogErrorKind.UNEXPECTED,
                "Unable to load local content. Retry the load."),
        )) {
            failure = exception
            coordinator.retryLoad()
            runCurrent()
            val error = assertIs<LocalCatalogState.Error>(coordinator.state.value)
            assertEquals(kind, error.kind)
            assertEquals(message, error.safeMessage)
            assertTrue("secret" !in error.safeMessage && "raw" !in error.safeMessage && "403" !in error.safeMessage)
        }
        failure = null
        coordinator.retryLoad()
        runCurrent()
        assertIs<LocalCatalogState.Ready>(coordinator.state.value)
        coordinator.dispose()
    }

    @Test fun latestRetryWinsEvenWhenPreviousFakeIgnoresCancellationAndThrows() = runTest {
        val stale = CompletableDeferred<CatalogLoad>()
        val ready = assertIs<CatalogLoad.Ready>(loader(lessonFiles()).load())
        var calls = 0
        val coordinator = LocalCatalogCoordinator(this) {
            if (++calls == 1) withContext(NonCancellable) { stale.await() } else ready
        }
        coordinator.load()
        runCurrent()
        coordinator.retryLoad()
        runCurrent()
        val published = assertIs<LocalCatalogState.Ready>(coordinator.state.value)
        assertSame(ready.content, published.content)
        stale.completeExceptionally(IllegalStateException("stale response"))
        runCurrent()
        assertSame(published, coordinator.state.value)
        coordinator.dispose()
    }

    @Test fun staleSuccessAndDisposalCannotPublishEvenWithCancellationIgnoringFake() = runTest {
        val first = CompletableDeferred<CatalogLoad>()
        val second = CompletableDeferred<CatalogLoad>()
        val ready = loader(lessonFiles()).load()
        var calls = 0
        val coordinator = LocalCatalogCoordinator(this) {
            withContext(NonCancellable) { if (++calls == 1) first.await() else second.await() }
        }
        coordinator.load()
        runCurrent()
        coordinator.retryLoad()
        runCurrent()
        first.complete(ready)
        runCurrent()
        assertIs<LocalCatalogState.Loading>(coordinator.state.value)
        coordinator.dispose()
        coordinator.dispose()
        second.complete(ready)
        runCurrent()
        assertIs<LocalCatalogState.Loading>(coordinator.state.value)
        coordinator.retryLoad() // disposed coordinators cannot relaunch
        runCurrent()
        assertEquals(2, calls)
    }

    @Test fun cancelledParentCannotPublishWhenTransportIgnoresCancellation() = runTest {
        val parent = Job()
        val late = CompletableDeferred<CatalogLoad>()
        val coordinator = LocalCatalogCoordinator(CoroutineScope(coroutineContext + parent)) {
            withContext(NonCancellable) { late.await() }
        }
        coordinator.load()
        runCurrent()
        parent.cancel()
        late.complete(loader(lessonFiles()).load())
        runCurrent()
        assertIs<LocalCatalogState.Loading>(coordinator.state.value)
        coordinator.dispose()
    }

    @Test fun cancellationPropagatesInsteadOfBecomingAnError() = runTest {
        var cancelled = false
        val coordinator = LocalCatalogCoordinator(this) {
            try { awaitCancellation() } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
        }
        coordinator.load()
        runCurrent()
        coordinator.dispose()
        runCurrent()
        assertTrue(cancelled)
        assertIs<LocalCatalogState.Loading>(coordinator.state.value)
    }
}
