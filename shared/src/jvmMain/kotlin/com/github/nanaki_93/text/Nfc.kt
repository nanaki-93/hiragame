package com.github.nanaki_93.text

import java.text.Normalizer

internal actual fun normalizeNfc(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFC)
