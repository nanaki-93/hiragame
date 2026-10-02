package com.github.nanaki_93.storage

/** Shared fake storage area. External changes notify every subscriber, including the initiating view. */
class MemoryProgressBacking(initialRaw: String? = null) {
    var raw: String? = initialRaw
        private set

    private class Listener(val owner: MemoryProgressStore, val callback: () -> Unit) {
        var active = true
    }

    private val listeners = mutableListOf<Listener>()

    /** Model a change in another tab (or a clear/removal) without passing through an adapter. */
    fun externalChange(replacementRaw: String?) {
        raw = replacementRaw
        notifyListeners(except = null)
    }

    /** Model an event even when the raw value has not changed. */
    fun signalExternalChange() = notifyListeners(except = null)

    internal fun replace(owner: MemoryProgressStore, replacementRaw: String) {
        raw = replacementRaw
        notifyListeners(except = owner)
    }

    internal fun listen(owner: MemoryProgressStore, callback: () -> Unit): StoreSubscription {
        val listener = Listener(owner, callback)
        listeners.add(listener)
        return StoreSubscription {
            if (listener.active) {
                listener.active = false
                listeners.remove(listener)
            }
        }
    }

    private fun notifyListeners(except: MemoryProgressStore?) {
        // Callbacks can dispose other callbacks or subscribe while an event is being delivered.
        for (listener in listeners.toList()) {
            if (listener.active && listener.owner !== except) listener.callback()
        }
    }
}

/** Node-safe adapter: failures belong to the adapter/view, while raw data is shared. */
class MemoryProgressStore(val backing: MemoryProgressBacking = MemoryProgressBacking()) : ProgressStore {
    var readFailure: StoreFailure? = null
    var writeFailure: StoreFailure? = null

    override fun read(): StoreReadResult {
        readFailure?.let { return StoreReadResult.Failure(it) }
        return backing.raw?.let(StoreReadResult::Raw) ?: StoreReadResult.Missing
    }

    override fun write(expectedRaw: String?, replacementRaw: String): StoreWriteResult {
        writeFailure?.let { return StoreWriteResult.Failure(it) }
        val observed = backing.raw
        if (observed != expectedRaw) return StoreWriteResult.Conflict(observed)
        backing.replace(this, replacementRaw)
        return StoreWriteResult.Written
    }

    override fun subscribe(onExternalChange: () -> Unit): StoreSubscription =
        backing.listen(this, onExternalChange)
}
