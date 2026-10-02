package com.github.nanaki_93.storage

import kotlin.test.Test
import kotlin.test.assertEquals

class BrowserProgressStoreTest {
    @Test fun constructionDoesNotNeedWindowInNode() {
        val store = BrowserProgressStore()
        if (js("typeof window === 'undefined'") as Boolean) {
            assertEquals(StoreReadResult.Failure(StoreFailure.OTHER), store.read())
            assertEquals(StoreWriteResult.Failure(StoreFailure.OTHER), store.write(null, "new"))
            store.subscribe { error("unexpected browser event") }.dispose()
        }
    }

    @Test fun oneKeyOneSetAndExactRawBaseline() {
        val platform = FakeBrowserPlatform()
        val store = BrowserProgressStore(platform)
        assertEquals(StoreReadResult.Missing, store.read())
        assertEquals(StoreWriteResult.Written, store.write(null, "{\"whole\":1}"))
        assertEquals(listOf("get:hiragame:state", "get:hiragame:state", "set:hiragame:state"), platform.calls)
        assertEquals("{\"whole\":1}", platform.raw)
        assertEquals(StoreReadResult.Raw("{\"whole\":1}"), store.read())

        platform.calls.clear()
        assertEquals(StoreWriteResult.Conflict("{\"whole\":1}"), store.write(null, "stale"))
        assertEquals(listOf("get:hiragame:state"), platform.calls)
        assertEquals("{\"whole\":1}", platform.raw)
        assertEquals(StoreWriteResult.Written, store.write("{\"whole\":1}", ""))
        assertEquals(listOf("get:hiragame:state", "get:hiragame:state", "set:hiragame:state"), platform.calls)
        assertEquals(StoreReadResult.Raw(""), store.read())
        platform.raw = null // external removal without an event
        assertEquals(StoreWriteResult.Conflict(null), store.write("", "stale"))
        assertEquals(null, platform.raw)
    }

    @Test fun acquisitionGetAndSetExceptionsAreClassifiedAndDoNotRemoveOriginal() {
        val platform = FakeBrowserPlatform().apply { raw = "original" }
        val store = BrowserProgressStore(platform)
        for ((name, reason) in listOf(
            "SecurityError" to StoreFailure.DENIED,
            "NotAllowedError" to StoreFailure.DENIED,
            "QuotaExceededError" to StoreFailure.QUOTA,
            "NS_ERROR_DOM_QUOTA_REACHED" to StoreFailure.QUOTA,
            "TypeError" to StoreFailure.OTHER,
        )) {
            val failure = browserError(name)
            platform.acquireError = failure
            assertEquals(StoreReadResult.Failure(reason), store.read())
            assertEquals(StoreWriteResult.Failure(reason), store.write("original", "replacement"))
            platform.acquireError = null
            platform.getError = failure
            assertEquals(StoreReadResult.Failure(reason), store.read())
            assertEquals(StoreWriteResult.Failure(reason), store.write("original", "replacement"))
            platform.getError = null
            platform.setError = failure
            assertEquals(StoreWriteResult.Failure(reason), store.write("original", "replacement"))
            platform.setError = null
            assertEquals("original", platform.raw)
        }
        assertEquals(StoreReadResult.Raw("original"), store.read())
        assertEquals(0, platform.calls.count { it.startsWith("remove:") || it.startsWith("clear:") })
    }

    @Test fun onlySameAreaStateOrClearEventsNotifyAndDisposeIsIdempotent() {
        val platform = FakeBrowserPlatform()
        val store = BrowserProgressStore(platform)
        var notifications = 0
        val subscription = store.subscribe { notifications++ }
        assertEquals(1, platform.listenerCount)
        platform.emit("hiragame:state", Any())
        platform.emit(null, Any())
        platform.emit(null, null)
        platform.emit("hiragame:colorMode")
        assertEquals(0, notifications)
        platform.emit("hiragame:state")
        platform.emit(null)
        assertEquals(2, notifications)
        subscription.dispose()
        subscription.dispose()
        assertEquals(0, platform.listenerCount)
        assertEquals(1, platform.removals)
        platform.emit("hiragame:state")
        assertEquals(2, notifications)
    }

    @Test fun subscriptionAcquisitionRegistrationAndRemovalFailuresAreContained() {
        val platform = FakeBrowserPlatform()
        val store = BrowserProgressStore(platform)
        var notifications = 0
        platform.acquireError = browserError("SecurityError")
        store.subscribe { notifications++ }.dispose()
        assertEquals(0, platform.listenerCount)
        platform.acquireError = null
        platform.listenError = browserError("SecurityError")
        store.subscribe { notifications++ }.dispose()
        assertEquals(0, platform.listenerCount)
        platform.listenError = null
        val subscription = store.subscribe { notifications++ }
        platform.removeError = browserError("SecurityError")
        subscription.dispose()
        platform.emit(null) // a failed native removal cannot revive this callback
        assertEquals(0, notifications)
    }
}

private fun browserError(name: String): Throwable {
    val error: dynamic = js("new Error('storage failure')")
    error.name = name
    return error as Throwable
}

private class FakeBrowserPlatform : BrowserStoragePlatform {
    var raw: String? = null
    var acquireError: Throwable? = null
    var getError: Throwable? = null
    var setError: Throwable? = null
    var listenError: Throwable? = null
    var removeError: Throwable? = null
    val calls = mutableListOf<String>()
    private val identity = Any()
    private val listeners = mutableListOf<(BrowserStorageChange) -> Unit>()
    val listenerCount get() = listeners.size
    var removals = 0
        private set

    override fun acquire(): BrowserStorageArea {
        acquireError?.let { throw it }
        return object : BrowserStorageArea {
            override val identity: Any = this@FakeBrowserPlatform.identity
            override fun getItem(key: String): String? {
                calls += "get:$key"
                getError?.let { throw it }
                return raw
            }
            override fun setItem(key: String, value: String) {
                calls += "set:$key"
                setError?.let { throw it }
                raw = value
            }
        }
    }

    override fun listen(onChange: (BrowserStorageChange) -> Unit): StoreSubscription {
        listenError?.let { throw it }
        listeners += onChange
        return StoreSubscription {
            removeError?.let { throw it }
            removals++
            listeners.remove(onChange)
        }
    }

    fun emit(key: String?, area: Any? = identity) {
        listeners.toList().forEach { it(BrowserStorageChange(key, area)) }
    }
}
