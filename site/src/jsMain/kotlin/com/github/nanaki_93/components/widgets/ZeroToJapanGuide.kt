package com.github.nanaki_93.components.widgets

import androidx.compose.runtime.Composable
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.attrsModifier
import com.github.nanaki_93.content.DocumentKind
import com.github.nanaki_93.content.ZeroBeginnerReference
import com.github.nanaki_93.content.ZeroToJapanRoadmap
import com.varabyte.kobweb.silk.components.navigation.Link
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Section
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.TagElement
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLElement

/** Guided 4-stage progression and reference desk for learners starting Japanese from zero. */
@Composable
internal fun ZeroToJapanRoadmapSection(
    roadmap: ZeroToJapanRoadmap,
    onStartOrResumePractice: (setId: String, resumable: Boolean) -> Unit,
) {
    val totalDone = roadmap.phases.sumOf { it.completedCount }
    val totalItems = roadmap.phases.sumOf { it.totalCount }
    val currentPhase = roadmap.phases.firstOrNull { it.completedCount < it.totalCount }?.id
    Section(attrs = { classes("roadmap-section"); attr("aria-labelledby", "roadmap-title") }) {
        Div(attrs = { classes("section-heading") }) {
            Div {
                Span(attrs = { classes("eyebrow") }) { Text("FROM ZERO TO YOUR EVERYDAY") }
                H2(attrs = { id("roadmap-title") }) { Text("Your path to Japan") }
            }
            Span(attrs = { classes("journey-count") }) { Text("$totalDone / $totalItems units completed") }
        }
        Div(attrs = { classes("roadmap-grid") }) {
            for (phase in roadmap.phases) {
                val (title, description, symbol) = when (phase.stepNumber) {
                    1 -> Triple("First, the kana", "Hiragana & katakana. Meet the sounds and signs of Japanese.", "あ")
                    2 -> Triple("Build your foundation", "Everyday words, essential grammar, and your first kanji.", "文")
                    3 -> Triple("Make yourself at home", "Trains, cafés, housing, and the small moments of daily life.", "暮")
                    else -> Triple("Find your work voice", "Standups, code reviews, and conversations with your team.", "働")
                }
                val done = phase.totalCount > 0 && phase.completedCount == phase.totalCount
                Section(attrs = {
                    classes("phase-card", "phase-card--${phase.stepNumber}")
                    if (phase.id == currentPhase) classes("phase-card--current")
                }) {
                    Div(attrs = { classes("phase-topline") }) {
                        Span(attrs = { classes("phase-number") }) { Text("0${phase.stepNumber}") }
                        Span(attrs = { classes("phase-status") }) { Text(if (done) "COMPLETED" else if (phase.id == currentPhase) "YOU ARE HERE" else "EXPLORE ANYTIME") }
                    }
                    Div(attrs = { classes("phase-symbol"); attr("lang", "ja"); attr("aria-hidden", "true") }) { Text(symbol) }
                    H3 { Text(title) }
                    P { Text(description) }
                    Div(attrs = { classes("phase-bottom") }) {
                        P(attrs = { classes("muted") }) { Text("${phase.completedCount} of ${phase.totalCount} ${if (phase.nextItemKind == DocumentKind.LESSON || phase.stepNumber > 2) "lessons" else "practice sets"} completed") }
                        StudyProgress("$title progress", phase.completedCount, phase.totalCount)
                        val nextId = phase.nextItemId
                        if (nextId != null) {
                            val action = if (phase.nextItemResumable) "Continue" else if (phase.id == currentPhase) "Start this stage" else "Explore stage"
                            when (phase.nextItemKind) {
                                DocumentKind.PRACTICE -> org.jetbrains.compose.web.dom.Button(attrs = {
                                    classes("text-link"); attr("type", "button")
                                    attr("aria-label", "$action: $title")
                                    onClick { onStartOrResumePractice(nextId, phase.nextItemResumable) }
                                }) { Text(action); StudyIcon("arrow") }
                                DocumentKind.LESSON -> Link("/lesson?lessonId=$nextId", modifier = Modifier.attrsModifier {
                                    classes("text-link"); attr("aria-label", "$action: $title")
                                }) { Text(action); StudyIcon("arrow") }
                                null -> Unit
                            }
                        } else P(attrs = { classes("stage-complete") }) { StudyIcon("check"); Text(" Stage complete") }
                    }
                }
            }
        }
        P(attrs = { classes("roadmap-note") }) { Text("A path to guide you, not a race. Every stage is open to explore.") }
    }
}

/** Interactive study charts for Kana, pronunciation rules, grammar foundations, and survival phrases. */
@Composable
internal fun ZeroBeginnerReferenceDesk() {
    Section(attrs = { classes("reference-desk") }) {
        Div(attrs = { classes("section-heading") }) {
            Div {
                Span(attrs = { classes("eyebrow") }) { Text("KEEP THESE CLOSE") }
                H2 { Text("Your study desk") }
            }
            Span(attrs = { classes("muted") }) { Text("A little help, whenever you need it.") }
        }

        TagElement<HTMLElement>("details", applyAttrs = {}) {
            TagElement<HTMLElement>("summary", applyAttrs = {}) {
                H3 { Text("あ / ア  ·  Kana chart & pronunciation") }
            }
            Section {
                H3 { Text("Key Pronunciation & Spelling Rules") }
                for (rule in ZeroBeginnerReference.pronunciationRules) {
                    P { Text("• $rule") }
                }
            }
            for (row in ZeroBeginnerReference.kanaRows) {
                Section(attrs = { classes("kana-row-card") }) {
                    Div(attrs = { classes("badge-row") }) {
                        Span(attrs = { classes("stage-badge") }) { Text(row.category) }
                    }
                    H3 { Text(row.rowTitle) }
                    Div(attrs = { classes("kana-grid") }) {
                        for (sign in row.signs) {
                            Div(attrs = { classes("kana-tile") }) {
                                Span(attrs = { attr("lang", "ja"); classes("kana-tile__glyph") }) {
                                    Text("${sign.hiragana} · ${sign.katakana}")
                                }
                                Span(attrs = { classes("kana-tile__romaji") }) {
                                    Text(sign.romaji)
                                }
                            }
                        }
                    }
                    for (sign in row.signs.filter { it.note != null }) {
                        P {
                            Span(attrs = { attr("lang", "ja") }) { Text("${sign.hiragana} / ${sign.katakana}") }
                            Text(" — ${sign.note}")
                        }
                    }
                }
            }
        }

        TagElement<HTMLElement>("details", applyAttrs = {}) {
            TagElement<HTMLElement>("summary", applyAttrs = {}) {
                H3 { Text("文  ·  Six essential grammar patterns") }
            }
            Div(attrs = { classes("grammar-grid") }) {
                for (card in ZeroBeginnerReference.grammarFoundations) {
                    Section {
                        Div(attrs = { classes("badge-row") }) {
                            Span(attrs = { classes("stage-badge") }) { Text(card.pattern) }
                        }
                        H3 { Text(card.title) }
                        P { Text(card.explanation) }
                        P(attrs = { attr("lang", "ja") }) { Text("Example: ${card.exampleSurface}") }
                        P(attrs = { attr("lang", "ja") }) { Text("Reading: ${card.exampleReading}") }
                        P { Text("Meaning: ${card.exampleTranslation}") }
                    }
                }
            }
        }

        TagElement<HTMLElement>("details", applyAttrs = {}) {
            TagElement<HTMLElement>("summary", applyAttrs = {}) {
                H3 { Text("話  ·  Phrases for life & work") }
            }
            Div(attrs = { classes("phrase-grid") }) {
                for (phrase in ZeroBeginnerReference.survivalPhrases) {
                    Section {
                        Div(attrs = { classes("badge-row") }) {
                            Span(attrs = { classes("stage-badge") }) { Text(phrase.category) }
                        }
                        H3 {
                            Span(attrs = { attr("lang", "ja") }) { Text(phrase.surface) }
                        }
                        P(attrs = { attr("lang", "ja") }) { Text("Reading: ${phrase.reading}") }
                        P { Text("Romaji: ${phrase.romaji} · Meaning: ${phrase.meaning}") }
                        P { Text("When to use: ${phrase.usageNote}") }
                    }
                }
            }
        }
    }
}
