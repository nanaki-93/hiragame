package com.github.nanaki_93.components.widgets

import androidx.compose.runtime.Composable
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
    Section(attrs = {
        classes("roadmap-section")
    }) {
        Div(attrs = { classes("badge-row") }) {
            Span(attrs = { classes("stage-badge") }) { Text("0 → Japan Path") }
            val totalDone = roadmap.phases.sumOf { it.completedCount }
            val totalItems = roadmap.phases.sumOf { it.totalCount }
            Span(attrs = { classes("stage-badge") }) { Text("$totalDone / $totalItems units completed") }
        }
        H2 { Text("From 0 to Working & Living in Japan") }
        P {
            Text("Follow the 4-stage progression from your first Hiragana sign to daily life in Japan and software engineering workplace conversations.")
        }

        roadmap.recommendedPractice?.let { recommended ->
            Section(attrs = {
                classes("recommended-card")
            }) {
                Div(attrs = { classes("badge-row") }) {
                    Span(attrs = { classes("stage-badge") }) {
                        Text(if (recommended.resumable) "In Progress" else "Recommended Next Step")
                    }
                    Span(attrs = { classes("stage-badge") }) { Text(recommended.topicTitle) }
                }
                H3 {
                    Text(
                        if (recommended.resumable) "Resume: ${recommended.title}"
                        else recommended.title
                    )
                }
                P { Text("${recommended.exerciseCount} exercises · ${recommended.reason}") }
                P { Text("Goal: ${recommended.description}") }
                PrimaryButton(
                    text = if (recommended.resumable) "Resume recommended practice" else "Start recommended practice",
                    onClick = { onStartOrResumePractice(recommended.setId, recommended.resumable) },
                )
            }
        }

        TagElement<HTMLElement>("details", applyAttrs = {
            attr("open", "")
        }) {
            TagElement<HTMLElement>("summary", applyAttrs = {}) {
                val totalDone = roadmap.phases.sumOf { it.completedCount }
                val totalItems = roadmap.phases.sumOf { it.totalCount }
                H3 { Text("4-Stage Curriculum Roadmap ($totalDone / $totalItems completed)") }
            }
            Div(attrs = { classes("roadmap-grid") }) {
                for (phase in roadmap.phases) {
                    val percent = if (phase.totalCount > 0) (phase.completedCount * 100) / phase.totalCount else 0
                    Section(attrs = {
                        classes("phase-card")
                    }) {
                        Div {
                            Div(attrs = { classes("badge-row") }) {
                                Span(attrs = {
                                    if (phase.completedCount == phase.totalCount && phase.totalCount > 0) {
                                        classes("stage-badge", "stage-badge--done")
                                    } else {
                                        classes("stage-badge")
                                    }
                                }) {
                                    Text("${phase.completedCount} / ${phase.totalCount} completed")
                                }
                                if (phase.inProgressCount > 0) {
                                    Span(attrs = { classes("stage-badge") }) {
                                        Text("${phase.inProgressCount} active")
                                    }
                                }
                            }
                            H3 { Text(phase.title) }
                            Div(attrs = { classes("progress-track") }) {
                                Div(attrs = {
                                    classes("progress-fill")
                                    style { property("width", "$percent%") }
                                })
                            }
                            P { Text(phase.subtitle) }
                        }
                        Div {
                            val nextId = phase.nextItemId
                            val nextTitle = phase.nextItemTitle
                            if (nextId != null && nextTitle != null) {
                                when (phase.nextItemKind) {
                                    DocumentKind.PRACTICE -> {
                                        SecondaryButton(
                                            text = if (phase.nextItemResumable) "Resume: $nextTitle" else "Start next: $nextTitle",
                                            onClick = { onStartOrResumePractice(nextId, phase.nextItemResumable) },
                                        )
                                    }
                                    DocumentKind.LESSON -> {
                                        P { Text("Up next: $nextTitle") }
                                        Link(path = "/lesson?lessonId=$nextId") {
                                            Text(if (phase.nextItemResumable) "Resume lesson: $nextTitle" else "Open lesson: $nextTitle")
                                        }
                                    }
                                    null -> Unit
                                }
                            } else if (phase.totalCount > 0) {
                                P { Text("✓ All units in this stage completed.") }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Interactive study charts for Kana, pronunciation rules, grammar foundations, and survival phrases. */
@Composable
internal fun ZeroBeginnerReferenceDesk() {
    Section {
        Div(attrs = { classes("badge-row") }) {
            Span(attrs = { classes("stage-badge") }) { Text("Always-Open Study Reference") }
        }
        H2 { Text("Beginner Study Reference Desk") }
        P {
            Text("Starting from zero? Open these reference sheets anytime before or during practice to inspect Hiragana & Katakana signs, core grammar patterns, and workplace survival expressions.")
        }

        TagElement<HTMLElement>("details", applyAttrs = {}) {
            TagElement<HTMLElement>("summary", applyAttrs = {}) {
                H3 { Text("Reference 1: Hiragana & Katakana Visual Chart + Pronunciation Rules") }
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
                H3 { Text("Reference 2: Japanese Grammar from 0 (6 Core Sentence Pillars)") }
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
                H3 { Text("Reference 3: Workplace Etiquette & Daily Life Survival Phrases") }
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
