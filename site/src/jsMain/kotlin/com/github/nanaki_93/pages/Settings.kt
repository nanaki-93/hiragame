package com.github.nanaki_93.pages

import androidx.compose.runtime.Composable
import com.github.nanaki_93.LocalProgress
import com.varabyte.kobweb.core.Page
import org.jetbrains.compose.web.dom.*

@Page("/settings")
@Composable
fun SettingsPage() {
    Main(attrs = { classes("learning-page") }) {
        H1 { Text("Settings & backup") }
        P { Text("Progress stays in this browser. Export a backup before clearing site data or moving devices.") }
        SavePreferencesSection(LocalProgress.current)
        com.github.nanaki_93.offline.OfflineStatus(controls = true)
    }
}
