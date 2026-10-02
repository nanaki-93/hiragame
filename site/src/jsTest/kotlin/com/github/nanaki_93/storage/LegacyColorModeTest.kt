package com.github.nanaki_93.storage

import com.github.nanaki_93.initialSilkMode
import com.github.nanaki_93.progress.SaveEnvelope
import com.github.nanaki_93.progress.SavePreferences
import com.github.nanaki_93.progress.SavedColorMode
import com.github.nanaki_93.progress.changePreferences
import com.github.nanaki_93.progress.encodeSave
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Fixtures from installed Silk 0.23.0: ColorMode.load/saveFromLocalStorage uses
 * browser-ext EnumStorageKey, which serializes the enum.name (LIGHT / DARK).
 */
class LegacyColorModeTest {
    private class CountingStore(private val backing: MemoryProgressBacking) : ProgressStore {
        private val delegate = MemoryProgressStore(backing)
        var reads = 0
        var writes = 0
        override fun read(): StoreReadResult { reads++; return delegate.read() }
        override fun write(expectedRaw: String?, replacementRaw: String): StoreWriteResult {
            writes++
            return delegate.write(expectedRaw, replacementRaw)
        }
        override fun subscribe(onExternalChange: () -> Unit) = delegate.subscribe(onExternalChange)
    }

    private fun owner(store: ProgressStore, legacy: () -> SavedColorMode?) =
        LocalProgressOwner(store, { 100L }, { "snapshot_1" }, legacy)

    @Test fun silkWireFixturesAreExactAndMalformedValuesFallBackToSystem() {
        assertEquals(SavedColorMode.LIGHT, readLegacyColorMode { "LIGHT" })
        assertEquals(SavedColorMode.DARK, readLegacyColorMode { "DARK" })
        for (raw in listOf(null, "", "light", "dark", "0", "1", "SYSTEM", "Dark", " DARK ")) {
            assertNull(readLegacyColorMode { raw }, "Unexpected legacy mode: $raw")
        }
        assertNull(readLegacyColorMode { throw IllegalStateException("denied") })
        assertNull(readLegacyColorMode()) // Node has no window; acquisition must be safe.
    }

    @Test fun absentMainKeyImportsLegacyIntoMemoryWithoutWritingOrDeletingIt() {
        for ((wire, mode) in listOf("LIGHT" to SavedColorMode.LIGHT, "DARK" to SavedColorMode.DARK)) {
            val main = MemoryProgressBacking()
            val store = CountingStore(main)
            var legacyReads = 0
            var legacyRaw = wire
            val subject = owner(store) { legacyReads++; readLegacyColorMode { legacyRaw } }
            assertEquals(PersistenceStatus.Fresh, subject.state.value.status)
            assertEquals(mode, subject.state.value.snapshot.preferences.colorMode)
            assertEquals(if (mode == SavedColorMode.DARK) ColorMode.DARK else ColorMode.LIGHT,
                initialSilkMode(subject) { error("Explicit preference must not probe system") })
            assertEquals(1, legacyReads)
            assertEquals(1, store.reads)
            assertEquals(0, store.writes)
            assertNull(main.raw)
            assertEquals(wire, legacyRaw)
            // The next explicit change carries the imported mode in the main snapshot.
            subject.mutate { changePreferences(it, it.preferences.copy(showReadings = false)) }
            assertEquals(1, store.writes)
            assertEquals(mode, subject.state.value.snapshot.preferences.colorMode)
            assertEquals(wire, legacyRaw)
            subject.dispose()
        }
    }

    @Test fun validMainWinsAndProtectedOrDeniedMainNeverImportsLegacy() {
        fun saved(mode: SavedColorMode) = encodeSave(SaveEnvelope(
            savedAtEpochMs = 50L, snapshotId = "existing", revision = 0L,
            preferences = SavePreferences(colorMode = mode),
        ))
        val validSaves = SavedColorMode.entries.map { saved(it) to it }
        for ((raw, expected) in validSaves + listOf("corrupt" to SavedColorMode.SYSTEM,
            "" to SavedColorMode.SYSTEM, """{"schemaVersion":99}""" to SavedColorMode.SYSTEM)) {
            val backing = MemoryProgressBacking(raw)
            val store = CountingStore(backing)
            var legacyReads = 0
            val subject = owner(store) { legacyReads++; SavedColorMode.DARK }
            assertEquals(expected, subject.state.value.snapshot.preferences.colorMode)
            assertEquals(if (expected == SavedColorMode.DARK) ColorMode.DARK else ColorMode.LIGHT,
                initialSilkMode(subject) { error("matchMedia unavailable") })
            if (raw !in validSaves.map { it.first }) assertIs<PersistenceStatus.Protected>(subject.state.value.status)
            assertEquals(0, legacyReads)
            assertEquals(1, store.reads)
            assertEquals(0, store.writes)
            assertEquals(raw, backing.raw)
            subject.dispose()
        }
        val denied = object : ProgressStore {
            override fun read() = StoreReadResult.Failure(StoreFailure.DENIED)
            override fun write(expectedRaw: String?, replacementRaw: String): StoreWriteResult =
                error("Must not write during initialization")
            override fun subscribe(onExternalChange: () -> Unit) = StoreSubscription {}
        }
        var legacyReads = 0
        val subject = owner(denied) { legacyReads++; SavedColorMode.DARK }
        assertIs<PersistenceStatus.MemoryOnly>(subject.state.value.status)
        assertEquals(SavedColorMode.SYSTEM, subject.state.value.snapshot.preferences.colorMode)
        assertEquals(ColorMode.DARK, initialSilkMode(subject) { ColorMode.DARK })
        assertEquals(ColorMode.LIGHT, initialSilkMode(subject) { error("matchMedia unavailable") })
        assertEquals(0, legacyReads)
        subject.dispose()
    }

    @Test fun missingMainWithUnusableLegacyDefaultsWithoutWrites() {
        for (legacy in listOf<() -> SavedColorMode?>(
            { readLegacyColorMode { "dark" } },
            { readLegacyColorMode { throw IllegalStateException("denied") } },
            { throw IllegalStateException("acquisition failed") },
        )) {
            val store = CountingStore(MemoryProgressBacking())
            val subject = owner(store, legacy)
            assertEquals(SavedColorMode.SYSTEM, subject.state.value.snapshot.preferences.colorMode)
            assertEquals(PersistenceStatus.Fresh, subject.state.value.status)
            assertEquals(0, store.writes)
            assertEquals(1, store.reads)
            subject.dispose()
        }
    }
}
