package com.github.nanaki_93.storage

private const val STATE_KEY = "hiragame:state"

/** Identity is the native Storage object, not the wrapper returned by acquire(). */
internal interface BrowserStorageArea {
    val identity: Any
    fun getItem(key: String): String?
    fun setItem(key: String, value: String)
}

internal data class BrowserStorageChange(val key: String?, val storageArea: Any?)

/** All browser operations (including listener registration) are replaceable in Node tests. */
internal interface BrowserStoragePlatform {
    fun acquire(): BrowserStorageArea
    fun listen(onChange: (BrowserStorageChange) -> Unit): StoreSubscription
}

/** No browser global is accessed on module import or adapter construction. */
class BrowserProgressStore internal constructor(private val platform: BrowserStoragePlatform) : ProgressStore {
    constructor() : this(WindowStoragePlatform)

    override fun read(): StoreReadResult = try {
        platform.acquire().getItem(STATE_KEY)?.let(StoreReadResult::Raw) ?: StoreReadResult.Missing
    } catch (error: Throwable) {
        StoreReadResult.Failure(classifyStorageFailure(error))
    }

    override fun write(expectedRaw: String?, replacementRaw: String): StoreWriteResult = try {
        val area = platform.acquire()
        val observedRaw = area.getItem(STATE_KEY)
        if (observedRaw != expectedRaw) {
            StoreWriteResult.Conflict(observedRaw)
        } else {
            // The previous value stays in place if setItem throws. This is not a cross-tab CAS.
            area.setItem(STATE_KEY, replacementRaw)
            StoreWriteResult.Written
        }
    } catch (error: Throwable) {
        StoreWriteResult.Failure(classifyStorageFailure(error))
    }

    override fun subscribe(onExternalChange: () -> Unit): StoreSubscription {
        // A denied acquisition or registration must not prevent memory-only use.
        val identity = try {
            platform.acquire().identity
        } catch (_: Throwable) {
            return StoreSubscription {}
        }
        var active = true
        val listener = try {
            platform.listen { event ->
                if (active && event.storageArea === identity && (event.key == STATE_KEY || event.key == null)) {
                    onExternalChange()
                }
            }
        } catch (_: Throwable) {
            return StoreSubscription {}
        }
        return StoreSubscription {
            if (active) {
                active = false
                try {
                    listener.dispose()
                } catch (_: Throwable) {
                    // A stale callback is still suppressed even if the browser refuses removal.
                }
            }
        }
    }
}

private fun classifyStorageFailure(error: Throwable): StoreFailure = when (error.asDynamic().name as? String) {
    "SecurityError", "NotAllowedError" -> StoreFailure.DENIED
    "QuotaExceededError", "NS_ERROR_DOM_QUOTA_REACHED" -> StoreFailure.QUOTA
    else -> StoreFailure.OTHER
}

private object WindowStoragePlatform : BrowserStoragePlatform {
    override fun acquire(): BrowserStorageArea {
        val browser: dynamic = js("window")
        val storage: dynamic = browser.localStorage // acquisition itself can throw SecurityError
        return object : BrowserStorageArea {
            override val identity: Any = storage as Any
            override fun getItem(key: String): String? = storage.getItem(key) as String?
            override fun setItem(key: String, value: String) { storage.setItem(key, value) }
        }
    }

    override fun listen(onChange: (BrowserStorageChange) -> Unit): StoreSubscription {
        val browser: dynamic = js("window")
        val handler: (dynamic) -> Unit = { event ->
            onChange(BrowserStorageChange(event.key as String?, event.storageArea as Any?))
        }
        browser.addEventListener("storage", handler)
        return StoreSubscription { browser.removeEventListener("storage", handler) }
    }
}
