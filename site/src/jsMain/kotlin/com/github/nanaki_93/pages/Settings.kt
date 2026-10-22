package com.github.nanaki_93.pages

import androidx.compose.runtime.Composable
import com.github.nanaki_93.LocalProgress
import com.varabyte.kobweb.core.Page
import org.jetbrains.compose.web.dom.*

@Page("/settings")
@Composable
fun SettingsPage() {
    Main(attrs = { classes("learning-page", "settings-page") }) {
        Span(attrs = { classes("eyebrow") }) { Text("MAKE YOURSELF COMFORTABLE") }
        H1 { Text("Your study space.") }
        P { Text("Adjust your reading support, appearance, and backups.") }
        P { Text("Progress stays in this browser. Export a backup before clearing site data or moving devices.") }
        SavePreferencesSection(LocalProgress.current)
        com.github.nanaki_93.components.widgets.PersonalGlossary(LocalProgress.current)
        com.github.nanaki_93.offline.OfflineStatus(controls = true)
    }
}
