package com.github.nanaki_93.offline

import androidx.compose.runtime.*
import com.github.nanaki_93.LocalProgress
import com.github.nanaki_93.storage.PersistenceStatus
import com.github.nanaki_93.components.widgets.*
import org.jetbrains.compose.web.dom.*
import kotlinx.browser.window

/** Activity state is runtime-only and starts conservatively until the owner is hydrated. */
@Composable
fun SessionActivity(active: Boolean) {
    SideEffect { window.asDynamic().hiragameSessionActive = active }
    DisposableEffect(Unit) { onDispose { window.asDynamic().hiragameSessionActive = false } }
}

@Composable
fun OfflineStatus(controls: Boolean = false) {
    val saved by LocalProgress.current.state.collectAsState()
    SideEffect {
        window.asDynamic().hiragameProgressSaved = (saved.status == PersistenceStatus.Saved || saved.status == PersistenceStatus.Fresh) && saved.rejectedUpdate == null
    }
    var status by remember { mutableStateOf("Offline caching is disabled in development, or is still initializing.") }
    DisposableEffect(Unit) {
        var unsubscribe: dynamic = null
        fun connect() {
            val api = window.asDynamic().hiragameOffline
            if (api != null && unsubscribe == null) unsubscribe = api.subscribe { value: String -> status = value }
        }
        connect()
        val listener: (org.w3c.dom.events.Event) -> Unit = { connect() }
        window.addEventListener("hiragame-offline-ready", listener)
        onDispose { if (unsubscribe != null) unsubscribe(); window.removeEventListener("hiragame-offline-ready", listener) }
    }
    P(attrs = { classes("local-save-status"); attr("role", "status") }) { Text(status) }
    if (controls) Section {
        H2 { Text("Offline app assets") }
        P { Text("A first visit needs a connection. After a complete download, this release can use cached text and any bundled recordings. Installation depends on your browser; use its install or Add to Home Screen action. Device voices may still need a connection.") }
        P { Text("Updates wait until all Hiragame tabs are saved and idle. Cache removal is separate from resetting learner progress. Clearing downloads means you need a connection to reload.") }
        fun invoke(method: String) {
            val api = window.asDynamic().hiragameOffline
            if (api == null) status = "Offline controls are available only in the production distribution."
            else api[method]()
        }
        SecondaryButton("Download app for offline use / retry", onClick = { invoke("download") })
        SecondaryButton("Check for update", onClick = { invoke("check") })
        SecondaryButton("Apply downloaded update", onClick = { invoke("apply") })
        SecondaryButton("Remove downloaded app assets", onClick = { invoke("clear") })
    }
}
