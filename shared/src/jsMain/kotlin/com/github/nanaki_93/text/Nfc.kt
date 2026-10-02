package com.github.nanaki_93.text

internal actual fun normalizeNfc(value: String): String = value.asDynamic().normalize("NFC") as String
