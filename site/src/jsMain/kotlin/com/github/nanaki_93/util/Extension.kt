package com.github.nanaki_93.util

import com.github.nanaki_93.service.handleSessionExpiration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun CoroutineScope.launchSafe(block: suspend () -> Unit) {
    this.launch {
        handleSessionExpiration { block() }
    }
}
