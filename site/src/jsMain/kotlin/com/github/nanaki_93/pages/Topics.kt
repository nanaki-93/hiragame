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
import com.github.nanaki_93.components.widgets.StudyIcon
import com.github.nanaki_93.components.widgets.FocusStudyHeading
import com.github.nanaki_93.components.widgets.PrimaryButton
import com.github.nanaki_93.components.widgets.PreviewJapaneseText
import com.github.nanaki_93.components.widgets.SecondaryButton
import com.github.nanaki_93.content.BrowserContentTextSource
import com.github.nanaki_93.content.BundledContentLoader
import com.github.nanaki_93.content.CatalogViewOptions
import com.github.nanaki_93.content.Lesson
import com.github.nanaki_93.content.LessonCard
import com.github.nanaki_93.content.ChoiceExercise
import com.github.nanaki_93.content.CompletionExercise
import com.github.nanaki_93.content.ProductionExercise
import com.github.nanaki_93.content.ReadingExercise
import com.github.nanaki_93.content.LessonSavedStatus
import com.github.nanaki_93.content.LocalCatalogCoordinator
import com.github.nanaki_93.content.LocalCatalogState
import com.github.nanaki_93.content.projectLessonCatalog
import com.github.nanaki_93.progress.LessonStage
import com.github.nanaki_93.progress.SavePreferences
import com.github.nanaki_93.storage.LocalProgressOwner
import com.github.nanaki_93.storage.ProgressMutationResult
import com.varabyte.kobweb.compose.ui.attrsModifier
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.silk.components.navigation.Link
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.TagElement
import org.w3c.dom.HTMLElement
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
    var area by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    var selectedLessonId by remember { mutableStateOf<String?>(null) }
    var supportNotice by remember { mutableStateOf<String?>(null) }

    DisposableEffect(coordinator) {
        coordinator.load()
        onDispose { coordinator.dispose() }
    }

    Main(attrs = { classes("study-dashboard", "catalog-page") }) {
        Div(attrs = { classes("page-intro") }) {
            Span(attrs = { classes("eyebrow") }) { Text("WORDS YOU WILL ACTUALLY USE") }
            H1 { Text("Japanese for your real life.") }
            P { Text("A first introduction. A train to catch. A code review to ask for. Start with a situation that matters to you.") }
        }
        when (val current = state) {
            LocalCatalogState.Loading -> P(attrs = { attr("role", "status") }) { Text("Getting your lessons ready…") }
            is LocalCatalogState.Empty -> Section {
                H2 { Text("No lessons available yet") }
                P { Text("Try reloading, or explore kana practice on My path.") }
                PrimaryButton("Retry loading", onClick = coordinator::retryLoad)
            }
            is LocalCatalogState.Error -> Section {
                H2 { Text("Lessons could not load") }
                P(attrs = { attr("role", "alert") }) { Text(current.safeMessage) }
                PrimaryButton("Retry loading", onClick = coordinator::retryLoad)
            }
            is LocalCatalogState.Ready -> {
                val view = projectLessonCatalog(current.content.catalog, current.content.lessons,
                    saved.snapshot, CatalogViewOptions(topicId, beginnerPath))
                val allCards = view.topics.flatMap { it.cards }
                if (selectedLessonId != null) {
                    val lesson = current.content.lessons[selectedLessonId]?.takeIf { it.id == selectedLessonId }
                    val card = allCards.firstOrNull { it.lessonId == selectedLessonId }
                    if (lesson == null || card == null) Section {
                        H2 { Text("Lesson unavailable") }
                        P { Text("This lesson is not in the current catalog. Your saved place is retained.") }
                        SecondaryButton("Back to lessons", onClick = { selectedLessonId = null })
                    } else LessonPreview(lesson, card, allCards, saved.snapshot.preferences, progress,
                        supportNotice, onSupportNotice = { supportNotice = it }, onClose = { selectedLessonId = null; supportNotice = null })
                } else {
                    Div(attrs = { classes("catalog-toolbar") }) {
                        Fieldset(attrs = { classes("area-filters") }) {
                            Legend(attrs = { classes("visually-hidden") }) { Text("Lesson setting") }
                            for ((value, label) in listOf("all" to "All lessons", "life" to "Daily life", "work" to "Engineering & work")) {
                                Label(attrs = { classes("area-filter") }) {
                                    Input(type = InputType.Radio, attrs = {
                                        attr("name", "lesson-area"); checked(area == value)
                                        onChange { area = value }
                                    })
                                    Text(label)
                                }
                            }
                        }
                        Div(attrs = { classes("lesson-search") }) {
                            Label(attrs = { attr("for", "lesson-search"); classes("visually-hidden") }) { Text("Search lessons") }
                            Input(type = InputType.Search, attrs = {
                                id("lesson-search"); attr("placeholder", "Find a situation…"); value(query)
                                onInput { query = it.value }
                            })
                        }
                    }
                    TagElement<HTMLElement>("details", applyAttrs = { classes("catalog-options") }) {
                        TagElement<HTMLElement>("summary", applyAttrs = {}) { Text("More study options") }
                        Div(attrs = { classes("catalog-options-fields") }) {
                            Label(attrs = { attr("for", "lesson-topic") }) { Text("Topic") }
                            Select(attrs = { id("lesson-topic"); onChange { topicId = it.value?.takeIf { value -> value.isNotEmpty() } } }) {
                                Option(value = "", attrs = { prop({ option: org.w3c.dom.HTMLOptionElement, isSelected: Boolean -> option.selected = isSelected }, topicId == null) }) { Text("All topics") }
                                for (group in view.topics) Option(value = group.topic.id, attrs = { prop({ option: org.w3c.dom.HTMLOptionElement, isSelected: Boolean -> option.selected = isSelected }, topicId == group.topic.id) }) { Text(group.topic.title) }
                            }
                            Label(attrs = { classes("save-mode-choice") }) {
                                Input(type = InputType.Checkbox, attrs = { checked(beginnerPath); onChange { beginnerPath = it.value } })
                                Text("Show a suggested starter order")
                            }
                        }
                    }
                    if (view.beginnerPath) Section(attrs = { classes("starter-note") }) {
                        H2 { Text("A gentle place to start") }
                        P { Text("Try these short lessons in order, with readings and translations whenever you need them.") }
                        view.beginnerPathCards.forEachIndexed { index, card ->
                            P { Link("/lesson?lessonId=${card.lessonId}") { Text("${index + 1}. ${card.title}") } }
                        }
                    }
                    if (query.isBlank() && area == "all" && topicId == null && !beginnerPath) view.recommendation?.let { recommendation ->
                        allCards.firstOrNull { it.lessonId == recommendation.lessonId }?.let { card ->
                            Div(attrs = { classes("catalog-suggestion") }) {
                                StudyIcon("book")
                                Text(if (recommendation.revisit) "One to revisit: " else "A good place to start: ")
                                Link("/lesson?lessonId=${card.lessonId}") { Text(card.title) }
                            }
                        }
                    }
                    val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                    val matching = view.cards.filter { card ->
                        (area == "all" || if (area == "life") card.topicId.startsWith("life-") else card.topicId.startsWith("workplace-")) &&
                            terms.all { term -> listOf(card.title, card.communicationGoal, card.situation,
                                view.topics.firstOrNull { it.topic.id == card.topicId }?.topic?.title.orEmpty()).any { it.contains(term, ignoreCase = true) } }
                    }
                    P(attrs = { classes("catalog-results", "muted"); attr("role", "status") }) {
                        Text("${matching.size} ${if (matching.size == 1) "lesson" else "lessons"} · Read, practise, then try a conversation. All levels are suggestions.")
                    }
                    if (matching.isEmpty()) Section(attrs = { classes("empty-state") }) {
                        StudyIcon("book")
                        H2 { Text("No lessons found") }
                        P { Text("Try a different word or clear your filters to explore all situations.") }
                        SecondaryButton("Clear search & filters", onClick = { query = ""; area = "all"; topicId = null })
                    }
                    Div(attrs = { classes("lesson-grid") }) {
                        for (card in matching) LessonCatalogCard(card) {
                            selectedLessonId = card.lessonId; supportNotice = null
                        }
                    }
                    if (view.unavailableSavedLessons.isNotEmpty()) Section {
                        H2 { Text("Saved lessons not in this catalog") }
                        P { Text("These records remain in your local progress.") }
                        for (status in view.unavailableSavedLessons) P {
                            Text("${status.record?.lessonId}: saved checkpoint unavailable. ${lessonProgressLabel(status)}")
                        }
                    }
                    Div(attrs = { classes("beginner-link") }) {
                        Span { Text("Starting from zero? Your kana practice and reference sheets are on My path.") }
                        Link("/") { Text("Go to my path →") }
                    }
                }
            }
        }
    }
}

/** The same validated lesson drives every section; no exercise submission or checkpoint writes occur. */
@Composable
private fun LessonPreview(
    lesson: Lesson,
    card: LessonCard,
    allCards: List<LessonCard>,
    preferences: SavePreferences,
    progress: LocalProgressOwner,
    supportNotice: String?,
    onSupportNotice: (String?) -> Unit,
    onClose: () -> Unit,
) {
    FocusStudyHeading("lesson-preview-title", lesson.id)
    Section(attrs = { style { property("overflow-wrap", "anywhere"); property("min-width", "0") } }) {
        H2(attrs = { id("lesson-preview-title"); attr("tabindex", "-1") }) { Text(lesson.title) }
        P { Text("Take a look before you begin. Your saved place stays as it is.") }
        P { Text(lessonProgressLabel(card.status)) }
        if (card.status.checkpointAvailable == false) P(attrs = { attr("role", "status") }) {
            Text("The saved checkpoint is unavailable. Your saved place is retained; this preview cannot repair or replace it.")
        }
        LessonEntryLink(card.lessonId)
        SecondaryButton("Back to catalog", onClick = onClose)
        H3 { Text("Situation") }
        P { Text(lesson.situation) }
        P { Text("Goal: ${lesson.communicationGoal}") }
        P { Text("Recommended difficulty: ${lesson.difficulty.name.lowercase()}; about ${lesson.durationMinutes} minutes.") }
        val prerequisites = lesson.prerequisiteLessonIds.map { id ->
            allCards.firstOrNull { it.lessonId == id }?.title ?: id
        }
        P { Text("Suggested prerequisites (not required): ${prerequisites.joinToString().ifEmpty { "none" }}.") }

        Fieldset {
            Legend { Text("Study aids") }
            P { Text("Optional support for this preview. These choices use your local preferences; if saving fails, content stays available. Recovery and backup controls are on Home.") }
            for ((aid, label, enabled) in listOf(
                Triple(StudyAid.READINGS, "Show readings", preferences.showReadings),
                Triple(StudyAid.TRANSLATION, "Show translations", preferences.showTranslation),
                Triple(StudyAid.ROMAJI, "Show authored romaji", preferences.showRomaji),
            )) {
                Label(attrs = { classes("save-mode-choice") }) {
                    Input(type = InputType.Checkbox, attrs = {
                        checked(enabled)
                        onChange {
                            onSupportNotice(when (selectStudyAid(progress, aid, it.value)) {
                                is ProgressMutationResult.Rejected -> "That support change was not applied. See the save status above and Home for recovery."
                                else -> null // The owner publishes accepted memory-only and saved status.
                            })
                        }
                    })
                    Text(label)
                }
            }
        }
        supportNotice?.let { P(attrs = { attr("role", "status") }) { Text(it) } }

        H3 { Text("Dialogue") }
        for (turn in lesson.dialogue.turns) {
            val speaker = lesson.dialogue.speakers.firstOrNull { it.id == turn.speakerId }
            CatalogCard {
                P { Text("${speaker?.name ?: turn.speakerId} · ${speaker?.role ?: "Speaker"}") }
                PreviewJapaneseText(turn.text, preferences)
            }
        }
        H3 { Text("Useful phrases") }
        for (phrase in lesson.phrases) CatalogCard {
            PreviewJapaneseText(phrase.text, preferences)
            P { Text("Use: ${phrase.usage}") }
            P { Text("Register: ${phrase.register}") }
        }
        H3 { Text("Grammar") }
        for (note in lesson.grammarNotes) CatalogCard {
            P { Text(note.explanation) }
            note.examples.forEach { PreviewJapaneseText(it, preferences) }
        }
        H3 { Text("Guided exercises · preview only") }
        P { Text("These prompts are for orientation, not playable exercises. No answers are graded or saved here.") }
        for (exercise in lesson.exercises) CatalogCard {
            val type = when (exercise) {
                is ChoiceExercise -> "Choose a response"
                is CompletionExercise -> "Complete a phrase"
                is ReadingExercise -> "Read a phrase"
                is ProductionExercise -> "Self-assessed response"
            }
            val prompt = when (exercise) {
                is ChoiceExercise -> exercise.prompt
                is CompletionExercise -> exercise.prompt
                is ReadingExercise -> exercise.prompt
                is ProductionExercise -> exercise.prompt
            }
            P { Text("$type: $prompt") }
            if (exercise is ProductionExercise) {
                P { Text("Self-assessment criteria (examples are not the only valid responses):") }
                exercise.criteria.forEach { P { Text("• $it") } }
                exercise.exampleResponses.forEach { PreviewJapaneseText(it, preferences) }
            }
        }
        H3 { Text("Role-play · preview only") }
        P { Text(lesson.rolePlay.task) }
        P { Text("Self-assessment criteria:") }
        lesson.rolePlay.criteria.forEach { P { Text("• $it") } }
        if (lesson.rolePlay.hints.isNotEmpty()) P { Text("Hints and phrase support:") }
        lesson.rolePlay.hints.forEach { P { Text("• $it") } }
        if (lesson.rolePlay.examples.isNotEmpty()) P { Text("Example responses (not the only valid responses):") }
        lesson.rolePlay.examples.forEach { PreviewJapaneseText(it, preferences) }
        SecondaryButton("Back to catalog", onClick = onClose)
    }
}

@Composable
private fun LessonCatalogCard(card: LessonCard, onSelect: () -> Unit) {
    CatalogCard {
        Div(attrs = { classes("lesson-card-top") }) {
            Span(attrs = { classes("lesson-card-icon", if (card.topicId.startsWith("life-")) "lesson-card-icon--life" else "lesson-card-icon--work") }) {
                StudyIcon(if (card.topicId.startsWith("life-")) "home" else "code")
            }
            Span(attrs = { classes("stage-badge") }) { Text("${card.durationMinutes} min · ${card.difficulty.name.lowercase()}") }
        }
        H3 { Text(card.title) }
        P { Text(card.communicationGoal) }
        Div(attrs = { classes("lesson-card-bottom") }) {
            P(attrs = { classes("lesson-card-status") }) {
                if (card.status.isCompleted) StudyIcon("check")
                Text(when {
                    card.status.checkpointAvailable == false -> "Saved place needs recovery"
                    card.status.isCompleted -> "Completed · Ready to revisit"
                    card.status.record != null -> "In progress · ${card.status.savedStage?.name?.lowercase()?.replace('_', ' ').orEmpty()}"
                    else -> "Ready when you are"
                })
            }
            Div(attrs = { classes("lesson-card-actions") }) {
                Link("/lesson?lessonId=${card.lessonId}", modifier = Modifier.attrsModifier {
                    classes("text-link"); attr("aria-label", "Open lesson: ${card.title}")
                }) {
                    Text(if (card.status.record != null && !card.status.isCompleted) "Continue lesson" else "Open lesson")
                    StudyIcon("arrow")
                }
                org.jetbrains.compose.web.dom.Button(attrs = {
                    classes("preview-button"); attr("type", "button"); attr("aria-label", "Preview: ${card.title}"); onClick { onSelect() }
                }) { Text("Preview") }
            }
        }
    }
}

/** Catalog IDs are validated stable keys; the player performs the entry action only after navigation. */
@Composable
private fun LessonEntryLink(lessonId: String) {
    Link(path = "/lesson?lessonId=$lessonId") { Text("Open lesson →") }
}

/** Local card spacing uses inherited surface colors in both Silk modes; controls keep native focus. */
@Composable
private fun CatalogCard(content: @Composable () -> Unit) {
    Section(attrs = {
        classes("topic-card")
        style {
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
    return if (status.isCompleted) "Completed. Saved stage: $stage."
    else "Unfinished. Saved stage: $stage."
}
