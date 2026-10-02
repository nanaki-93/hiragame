package com.github.nanaki_93.storage

import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressStoreTest {
    @Test fun missingAndRawReadsAreDistinctIncludingEmptyText() {
        val backing = MemoryProgressBacking()
        val store = MemoryProgressStore(backing)
        assertEquals(StoreReadResult.Missing, store.read())
        backing.externalChange("")
        assertEquals(StoreReadResult.Raw(""), store.read())
        backing.externalChange(null)
        assertEquals(StoreReadResult.Missing, store.read())
    }

    @Test fun failuresAreInjectableAndRejectedWritesPreserveThePreviousRawText() {
        val backing = MemoryProgressBacking("original, even if invalid JSON")
        val store = MemoryProgressStore(backing)
        for (reason in StoreFailure.entries) {
            store.readFailure = reason
            assertEquals(StoreReadResult.Failure(reason), store.read())
            store.writeFailure = reason
            assertEquals(StoreWriteResult.Failure(reason), store.write(backing.raw, "replacement"))
            assertEquals("original, even if invalid JSON", backing.raw)
        }
        store.readFailure = null
        store.writeFailure = null
        assertEquals(StoreReadResult.Raw("original, even if invalid JSON"), store.read())
        assertEquals(
            StoreWriteResult.Conflict("original, even if invalid JSON"),
            store.write(null, "replacement"),
        )
        assertEquals("original, even if invalid JSON", backing.raw)
    }

    @Test fun writesCheckExactBaselineIncludingMissingAndEmptyAndNeverNotifyWriter() {
        val backing = MemoryProgressBacking()
        val first = MemoryProgressStore(backing)
        val second = MemoryProgressStore(backing)
        var firstEvents = 0
        var secondEvents = 0
        first.subscribe { firstEvents++ }
        second.subscribe { secondEvents++ }

        assertEquals(StoreWriteResult.Written, first.write(null, ""))
        assertEquals(StoreReadResult.Raw(""), second.read())
        assertEquals(0, firstEvents)
        assertEquals(1, secondEvents)
        assertEquals(StoreWriteResult.Conflict(""), second.write(null, "stale"))
        assertEquals(StoreWriteResult.Written, second.write("", "updated"))
        assertEquals(StoreWriteResult.Conflict("updated"), first.write("", "stale"))
        assertEquals("updated", backing.raw)
        assertEquals(1, firstEvents)
        assertEquals(1, secondEvents) // rejected writes never publish events

        backing.externalChange(null)
        assertEquals(StoreWriteResult.Conflict(null), second.write("updated", "stale"))
        assertEquals(StoreReadResult.Missing, first.read())
        assertEquals(2, firstEvents)
        assertEquals(2, secondEvents)
    }

    @Test fun externalEventsReachOtherOwnersAndDisposalSuppressesCallbacks() {
        val backing = MemoryProgressBacking("initial")
        val first = MemoryProgressStore(backing)
        val second = MemoryProgressStore(backing)
        val events = mutableListOf<String>()
        val disposed = first.subscribe { events += "disposed" }
        first.subscribe { events += "first" }
        val secondSubscription = second.subscribe { events += "second" }
        disposed.dispose()
        disposed.dispose()

        backing.signalExternalChange() // equal-value events may be ignored by the owner
        assertEquals(listOf("first", "second"), events)
        assertEquals(StoreWriteResult.Written, first.write("initial", "from first"))
        assertEquals(listOf("first", "second", "second"), events)
        secondSubscription.dispose()
        backing.externalChange("from elsewhere")
        assertEquals(listOf("first", "second", "second", "first"), events)
        assertEquals("from elsewhere", backing.raw)
    }

    @Test fun disposalDuringDeliverySuppressesPendingCallbackAndNewListenersWaitForNextEvent() {
        val backing = MemoryProgressBacking()
        val store = MemoryProgressStore(backing)
        var count = 0
        lateinit var pending: StoreSubscription
        store.subscribe {
            pending.dispose()
            store.subscribe { count += 10 }
        }
        pending = store.subscribe { count++ }
        backing.signalExternalChange()
        assertEquals(0, count)
        backing.signalExternalChange()
        assertEquals(10, count) // new listener was eligible only on the next event
    }
}
