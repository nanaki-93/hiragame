package com.github.nanaki_93.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.github.nanaki_93.LocalProgress
import com.github.nanaki_93.components.styles.Colors
import com.github.nanaki_93.components.styles.Styles
import com.github.nanaki_93.components.widgets.PrimaryButton
import com.github.nanaki_93.components.widgets.SecondaryButton
import com.github.nanaki_93.content.BrowserContentTextSource
import com.github.nanaki_93.content.BundledContentLoader
import com.github.nanaki_93.content.CatalogEmptyReason
import com.github.nanaki_93.content.CatalogViewOptions
import com.github.nanaki_93.content.LessonCard
import com.github.nanaki_93.content.LessonSavedStatus
import com.github.nanaki_93.content.LocalCatalogCoordinator
import com.github.nanaki_93.content.LocalCatalogState
import com.github.nanaki_93.content.projectLessonCatalog
import com.github.nanaki_93.progress.LessonStage
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.silk.components.navigation.Link
import com.varabyte.kobweb.silk.style.toModifier
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.dom.Fieldset
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Legend
import org.jetbrains.compose.web.dom.Main
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Section
import org.jetbrains.compose.web.dom.Text

/** Browsing controls belong to this page; the app-scoped save owner remains the only progress source. */
@Page("/topics")
@Composable
fun TopicsPage() {
    val scope = rememberCoroutineScope()
    val progress = LocalProgress.current
    val coordinator = remember(scope) { LocalCatalogCoordinator(scope, BundledContentLoader(BrowserContentTextSource())) }
    val state by coordinator.state.collectAsState()
    val saved by progress.state.collectAsState()
    var topicId by remember { mutableStateOf<String?>(null) }
    var beginnerPath by remember { mutableStateOf(false) }
    var selectedLessonId by remember { mutableStateOf<String?>(null) }
    val darkMode = ColorMode.current == ColorMode.DARK

    DisposableEffect(coordinator) {
        coordinator.load()
        onDispose { coordinator.dispose() }
    }

    Main {
        Box(Styles.GameContainer.toModifier().then(
            if (darkMode) Modifier.backgroundColor(Colors.DarkBackground) else Modifier
        )) {
            Column(modifier = Styles.Card.toModifier().then(
                if (darkMode) Modifier.backgroundColor(Colors.DarkCardBackground).color(Colors.DarkText) else Modifier
            )) {
                H1 { Text("Topics / Learn") }
                Link(path = "/") { Text("Back to Home") }
                when (val current = state) {
                    LocalCatalogState.Loading -> Section(attrs = { attr("aria-live", "polite") }) {
                        H2 { Text("Loading workplace lessons") }
                        P(attrs = { attr("role", "status") }) { Text("Reading reviewed lessons bundled with this site.") }
                    }
                    is LocalCatalogState.Empty -> Section {
                        H2 { Text("No workplace lessons available") }
                        P { Text(when (current.reason) {
                            CatalogEmptyReason.EMPTY_CATALOG -> "The content catalog is empty. Your local progress has not been removed."
                            CatalogEmptyReason.NO_WORKPLACE_LESSONS -> "No workplace lessons are listed yet. Practice may still be available on Home."
                        }) }
                        PrimaryButton("Retry loading", onClick = coordinator::retryLoad)
                    }
                    is LocalCatalogState.Error -> Section {
                        H2 { Text("Workplace lessons could not load") }
                        P(attrs = { attr("role", "alert") }) { Text(current.safeMessage) }
                        PrimaryButton("Retry loading", onClick = coordinator::retryLoad)
                    }
                    is LocalCatalogState.Ready -> {
                        // Reproject on every owner state update (including restore, reset, and reload).
                        // Never cache the save or derive completion from a practice score.
                        val view = projectLessonCatalog(current.content.catalog, current.content.lessons,
                            saved.snapshot, CatalogViewOptions(topicId, beginnerPath))
                        P(attrs = { attr("role", "status") }) { Text(saveStatusMessage(saved)) }
                        P { Text("Saving or recovery controls are on Home. Browsing here never changes lesson or review progress.") }
                        val allCards = view.topics.flatMap { it.cards }
                        if (selectedLessonId != null) {
                            val card = allCards.firstOrNull { it.lessonId == selectedLessonId }
                            Section {
                                if (card == null) {
                                    H2 { Text("Lesson unavailable") }
                                    P { Text("This lesson is not in the current validated catalog. Any saved place is retained.") }
                                } else {
                                    H2 { Text(card.title) }
                                    P { Text("Goal: ${card.communicationGoal}") }
                                    P { Text(card.situation) }
                                    P { Text(lessonProgressLabel(card.status)) }
                                    P { Text("This catalog summary is read-only. The full lesson preview and staged exercises are separate steps; opening this summary does not change your saved place.") }
                                }
                                SecondaryButton("Back to catalog", onClick = { selectedLessonId = null })
                            }
                        } else {
                            H2 { Text("Choose a workplace situation") }
                            P { Text("Every lesson is open. Difficulty and prerequisites are suggestions, not requirements; no path or proficiency level is assigned.") }
                            Fieldset {
                                Legend { Text("Browse lessons") }
                                P { Text("Filter by workplace topic") }
                                Label(attrs = { classes("save-mode-choice") }) {
                                    Input(type = InputType.Radio, attrs = {
                                        attr("name", "topic-filter")
                                        checked(topicId == null)
                                        onChange { topicId = null }
                                    })
                                    Text("All workplace topics")
                                }
                                for (group in view.topics) {
                                    Label(attrs = { classes("save-mode-choice") }) {
                                        Input(type = InputType.Radio, attrs = {
                                            attr("name", "topic-filter")
                                            checked(topicId == group.topic.id)
                                            onChange { topicId = group.topic.id }
                                        })
                                        Text(group.topic.title)
                                    }
                                }
                                Label(attrs = { classes("save-mode-choice") }) {
                                    Input(type = InputType.Checkbox, attrs = {
                                        checked(beginnerPath)
                                        onChange { beginnerPath = it.value }
                                    })
                                    Text("Show optional beginner path")
                                }
                            }
                            P { Text("Filter and beginner path are temporary to this page; they are not saved preferences. All lessons remain selectable.") }
                            if (view.beginnerPath) Section {
                                H3 { Text("Suggested beginner order") }
                                P { Text("Use readings, translations and phrase support if helpful, or explore independently. This order does not unlock lessons.") }
                                view.beginnerPathCards.forEachIndexed { index, card ->
                                    P { Text("${index + 1}. ${card.title}") }
                                }
                            }
                            view.recommendation?.let { recommendation ->
                                val card = allCards.firstOrNull { it.lessonId == recommendation.lessonId }
                                if (card != null) CatalogCard {
                                    H3 { Text(if (recommendation.revisit) "Revisit suggestion: ${card.title}" else "Suggested: ${card.title}") }
                                    P { Text(recommendation.reason) }
                                    P { Text("This is guidance, not an assessment or a requirement.") }
                                    SecondaryButton("Select lesson: ${card.title}", onClick = { selectedLessonId = card.lessonId })
                                }
                            }
                            if (view.cards.isEmpty()) Section {
                                H3 { Text("No lessons match this topic") }
                                P { Text("Other workplace lessons remain available. Your saved progress has not changed.") }
                                SecondaryButton("Clear filter", onClick = { topicId = null })
                            }
                            for (group in view.topics) {
                                val matching = view.cards.filter { it.topicId == group.topic.id }
                                if (matching.isNotEmpty()) Section {
                                    H2 { Text(group.topic.title) }
                                    P { Text(group.topic.description) }
                                    for (card in matching) LessonCatalogCard(card, allCards) {
                                        selectedLessonId = card.lessonId
                                    }
                                }
                            }
                            if (view.unavailableSavedLessons.isNotEmpty()) Section {
                                H2 { Text("Saved lessons not in this catalog") }
                                P { Text("These records remain in your local progress. Browsing does not remove or repair them.") }
                                for (status in view.unavailableSavedLessons) {
                                    P(attrs = { style { property("overflow-wrap", "anywhere") } }) {
                                        Text("${status.record?.lessonId}: lesson document unavailable; saved checkpoint unavailable. ${lessonProgressLabel(status)}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonCatalogCard(card: LessonCard, allCards: List<LessonCard>, onSelect: () -> Unit) {
    CatalogCard {
        H3 { Text(card.title) }
        P { Text("Goal: ${card.communicationGoal}") }
        P { Text(card.situation) }
        P { Text("Recommended difficulty: ${card.difficulty.name.lowercase()}; about ${card.durationMinutes} minutes.") }
        val prerequisites = card.prerequisiteLessonIds.map { id ->
            allCards.firstOrNull { it.lessonId == id }?.title ?: id
        }
        P { Text("Suggested prerequisites (not required): ${prerequisites.joinToString().ifEmpty { "none" }}.") }
        P { Text(lessonProgressLabel(card.status)) }
        P { Text("Lesson document: ${if (card.status.documentAvailable) "available" else "unavailable"}; saved checkpoint: ${when (card.status.checkpointAvailable) {
            true -> "reference available (not playable here)"
            false -> "unavailable; saved place retained"
            null -> "none"
        }}.") }
        PrimaryButton("Select lesson: ${card.title}", onClick = onSelect)
    }
}

/** Local card spacing uses inherited surface colors in both Silk modes; controls keep native focus. */
@Composable
private fun CatalogCard(content: @Composable () -> Unit) {
    Section(attrs = {
        classes("topic-card")
        style {
            property("border", "1px solid currentColor")
            property("border-radius", "0.75rem")
            property("padding", "1rem")
            property("margin-block", "1rem")
            property("text-align", "start")
            property("overflow-wrap", "anywhere")
        }
    }) { content() }
}

/** Completion and checkpoint availability are independent; this text never promises a Resume action. */
private fun lessonProgressLabel(status: LessonSavedStatus): String {
    val record = status.record ?: return "Not started."
    val stage = when (record.stage) {
        LessonStage.SITUATION -> "Situation"
        LessonStage.DIALOGUE -> "Dialogue"
        LessonStage.UNDERSTANDING -> "Understanding"
        LessonStage.GUIDED_PRACTICE -> "Guided practice"
        LessonStage.ROLE_PLAY -> "Role-play"
        LessonStage.SUMMARY -> "Summary"
    }
    return if (status.isCompleted) "Completed (saved completion timestamp: ${status.completedAtEpochMs}). Saved stage: $stage."
    else "Unfinished. Saved stage: $stage."
}
