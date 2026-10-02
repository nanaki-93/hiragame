package com.github.nanaki_93.components.widgets

import androidx.compose.runtime.*
import com.github.nanaki_93.LocalProgress
import com.github.nanaki_93.pages.saveStatusMessage
import com.varabyte.kobweb.silk.components.navigation.Link
import org.jetbrains.compose.web.dom.*

@Composable
fun LearningNavigation() {
    Nav(attrs = { classes("learning-nav"); attr("aria-label", "Main navigation") }) {
        Link("/") { Text("Home") }
        Link("/topics") { Text("Topics / Learn") }
        Link("/review") { Text("Review") }
        Link("/settings") { Text("Settings / Backup") }
    }
}

@Composable
fun LocalSaveStatus() {
    val owner = LocalProgress.current
    val saved by owner.state.collectAsState()
    P(attrs = { classes("local-save-status"); attr("role", "status") }) { Text("Local save: ${saveStatusMessage(saved)}") }
}
