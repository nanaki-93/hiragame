package com.github.nanaki_93.storage

import com.github.nanaki_93.progress.SavedColorMode

private const val LEGACY_COLOR_MODE_KEY = "hiragame:colorMode"

/** Silk 0.23.0 uses browser-ext's EnumStorageKey: enum.name, exactly "LIGHT" or "DARK".
 * Its loadFromLocalStorage returns null for missing/unrecognized values. This reader also
 * catches acquisition and getItem failures, and never writes or removes the legacy key.
 */
internal fun readLegacyColorMode(readRaw: () -> String?): SavedColorMode? = try {
    when (readRaw()) {
        "LIGHT" -> SavedColorMode.LIGHT
        "DARK" -> SavedColorMode.DARK
        else -> null
    }
} catch (_: Throwable) {
    null
}

internal fun readLegacyColorMode(): SavedColorMode? = readLegacyColorMode {
    val browser: dynamic = js("window")
    browser.localStorage.getItem(LEGACY_COLOR_MODE_KEY) as String?
}
