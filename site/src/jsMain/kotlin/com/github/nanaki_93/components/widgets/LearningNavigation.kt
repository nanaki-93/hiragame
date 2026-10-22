package com.github.nanaki_93.components.widgets

import androidx.compose.runtime.*
import com.github.nanaki_93.LocalProgress
import com.github.nanaki_93.pages.saveStatusMessage
import com.github.nanaki_93.pages.selectColorMode
import com.github.nanaki_93.progress.SavedColorMode
import com.github.nanaki_93.storage.PersistenceStatus
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.attrsModifier
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.components.navigation.Link
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import org.jetbrains.compose.web.dom.*

/** Decorative, locally bundled line icons; every control also has a text label. */
@Composable
fun StudyIcon(name: String) {
    Span(attrs = { classes("study-icon", "study-icon--$name"); attr("aria-hidden", "true") })
}

@Composable
fun LearningNavigation() {
    val path = rememberPageContext().route.path.removePrefix("/hiragame").trimEnd('/')
    val owner = LocalProgress.current
    val mode = ColorMode.currentState
    val dark = mode.value == ColorMode.DARK
    Header(attrs = { classes("site-header") }) {
        A(href = "#main-content", attrs = { classes("skip-link") }) { Text("Skip to learning") }
        Div(attrs = { classes("header-inner") }) {
            Link("/", modifier = Modifier.attrsModifier { classes("brand"); attr("aria-label", "Hiragame home") }) {
                Span(attrs = { classes("brand-mark"); attr("lang", "ja"); attr("aria-hidden", "true") }) { Text("あ") }
                Span(attrs = { classes("brand-name") }) { Text("hiragame"); Span { Text("A little Japanese, every day.") } }
            }
            Nav(attrs = { classes("learning-nav"); attr("aria-label", "Main navigation") }) {
                for ((destination, label, icon) in listOf(
                    Triple("/", "My path", "path"), Triple("/topics", "Lessons", "book"),
                    Triple("/review", "Review", "review"), Triple("/settings", "Settings", "settings"),
                )) {
                    val active = path == destination.trimEnd('/') || (destination == "/topics" && path == "/lesson")
                    Link(destination, modifier = Modifier.attrsModifier {
                        if (active) attr("aria-current", "page")
                    }) { StudyIcon(icon); Text(label) }
                }
            }
            Button(attrs = {
                classes("theme-toggle"); attr("type", "button")
                attr("aria-label", if (dark) "Switch to light theme" else "Switch to dark theme")
                attr("title", if (dark) "Switch to light theme" else "Switch to dark theme")
                onClick {
                    selectColorMode(owner, if (dark) SavedColorMode.LIGHT else SavedColorMode.DARK) {
                        mode.value = if (dark) ColorMode.LIGHT else ColorMode.DARK
                    }
                }
            }) { StudyIcon(if (dark) "sun" else "moon") }
        }
    }
}

/** Healthy saves are quiet; actual storage problems remain visible above every page. */
@Composable
fun LocalSaveStatus(warningsOnly: Boolean = false) {
    val saved by LocalProgress.current.state.collectAsState()
    val healthy = (saved.status == PersistenceStatus.Fresh || saved.status == PersistenceStatus.Saved) && saved.rejectedUpdate == null
    if (warningsOnly == healthy) return
    P(attrs = { classes(if (healthy) "save-indicator" else "save-warning"); attr("role", "status") }) {
        if (healthy) {
            StudyIcon("check")
            Text(if (saved.status == PersistenceStatus.Saved) "Progress saved on this device" else "Learn at your pace · No account needed")
        } else {
            Text(saveStatusMessage(saved)); Text(" ")
            Link("/settings") { Text("Manage save & backup") }
        }
    }
}
