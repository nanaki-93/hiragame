package com.github.nanaki_93.components.layouts

import androidx.compose.runtime.Composable
import com.github.nanaki_93.components.widgets.LearningNavigation
import com.github.nanaki_93.components.widgets.LocalSaveStatus
import com.github.nanaki_93.offline.OfflineStatus
import com.varabyte.kobweb.core.layout.Layout
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLElement

/** Router-backed links require the PageContext that Kobweb provides to layouts and pages. */
@Layout
@Composable
fun PageLayout(content: @Composable () -> Unit) {
    LearningNavigation()
    LocalSaveStatus(warningsOnly = true)
    Div(attrs = { id("main-content"); attr("tabindex", "-1"); classes("page-content") }) { content() }
    Footer(attrs = { classes("site-footer") }) {
        Div(attrs = { classes("footer-line") }) {
            Span { Text("Small steps. A whole new world.") }
            LocalSaveStatus()
        }
        TagElement<HTMLElement>("details", applyAttrs = { classes("connection-details") }) {
            TagElement<HTMLElement>("summary", applyAttrs = {}) { Text("Offline & device status") }
            OfflineStatus()
        }
    }
}
