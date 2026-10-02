package com.github.nanaki_93.text

/** Canonical composition only; never compatibility-fold width, script, or spelling. */
internal expect fun normalizeNfc(value: String): String
