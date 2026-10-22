package com.github.nanaki_93.components.widgets

import androidx.compose.runtime.Composable
import com.github.nanaki_93.content.DocumentKind
import com.github.nanaki_93.content.ZeroBeginnerReference
import com.github.nanaki_93.content.ZeroToJapanRoadmap
import com.varabyte.kobweb.silk.components.navigation.Link
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
        style {
            property("width", "100%")
            property("text-align", "start")
            property("overflow-wrap", "anywhere")
        }
    }) {
        H2 { Text("From 0 to Working & Living in Japan") }
        P {
            Text("Follow the 4-stage progression from your first Hiragana sign to daily life in Japan and software engineering workplace conversations.")
        }

        roadmap.recommendedPractice?.let { recommended ->
            Section(attrs = {
                style {
                    property("border", "1px solid currentColor")
                    property("border-radius", "0.75rem")
                    property("padding", "1rem")
                    property("margin-block", "0.75rem")
                }
            }) {
                H3 {
                    Text(
                        if (recommended.resumable) "Next recommended step: Resume ${recommended.title}"
                        else "Next recommended step: ${recommended.title}"
                    )
                }
                P { Text("${recommended.topicTitle} · ${recommended.exerciseCount} exercises · ${recommended.reason}") }
                P { Text("Goal: ${recommended.description}") }
                PrimaryButton(
                    text = if (recommended.resumable) "Resume recommended practice" else "Start recommended practice",
                    onClick = { onStartOrResumePractice(recommended.setId, recommended.resumable) },
                )
            }
        }

        TagElement<HTMLElement>("details", applyAttrs = {
            style {
                property("width", "100%")
                property("text-align", "start")
                property("margin-block", "0.5rem")
                property("overflow-wrap", "anywhere")
            }
        }) {
            TagElement<HTMLElement>("summary", applyAttrs = {}) {
                val totalDone = roadmap.phases.sumOf { it.completedCount }
                val totalItems = roadmap.phases.sumOf { it.totalCount }
                H3 { Text("4-Stage Curriculum Roadmap ($totalDone / $totalItems completed)") }
            }
            for (phase in roadmap.phases) {
                Section(attrs = {
                    style {
                        property("border", "1px solid currentColor")
                        property("border-radius", "0.5rem")
                        property("padding", "0.75rem")
                        property("margin-block", "0.5rem")
                    }
                }) {
                    H3 { Text("${phase.title} · ${phase.completedCount} / ${phase.totalCount} completed") }
                    P { Text(phase.subtitle) }
                    if (phase.inProgressCount > 0) {
                        P { Text("${phase.inProgressCount} item(s) currently in progress.") }
                    }
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
                                P { Text("Suggested next lesson: $nextTitle") }
                                Link(path = "/lesson?lessonId=$nextId") {
                                    Text(if (phase.nextItemResumable) "Open & resume lesson: $nextTitle" else "Open lesson: $nextTitle")
                                }
                            }
                            null -> Unit
                        }
                    } else if (phase.totalCount > 0) {
                        P { Text("All items in this stage have been completed at least once.") }
                    }
                }
            }
        }
    }
}

/** Interactive study charts for Kana, pronunciation rules, grammar foundations, and survival phrases. */
@Composable
internal fun ZeroBeginnerReferenceDesk() {
    Section(attrs = {
        style {
            property("width", "100%")
            property("text-align", "start")
            property("overflow-wrap", "anywhere")
        }
    }) {
        H2 { Text("Beginner Study Reference Desk (Study Before & During Practice)") }
        P {
            Text("Starting from zero? Open these reference sheets anytime to study Hiragana & Katakana signs, sentence grammar rules, and essential Japan survival & workplace expressions before testing yourself.")
        }

        TagElement<HTMLElement>("details", applyAttrs = {
            style {
                property("width", "100%")
                property("text-align", "start")
                property("margin-block", "0.5rem")
                property("overflow-wrap", "anywhere")
            }
        }) {
            TagElement<HTMLElement>("summary", applyAttrs = {}) {
                H3 { Text("Reference 1: Hiragana & Katakana Chart + Pronunciation Rules") }
            }
            H3 { Text("Key Pronunciation & Spelling Rules") }
            for (rule in ZeroBeginnerReference.pronunciationRules) {
                P { Text("• $rule") }
            }
            H3 { Text("Hiragana / Katakana Sign Chart") }
            for (row in ZeroBeginnerReference.kanaRows) {
                Section(attrs = {
                    style {
                        property("border", "1px solid currentColor")
                        property("border-radius", "0.5rem")
                        property("padding", "0.75rem")
                        property("margin-block", "0.5rem")
                    }
                }) {
                    H3 { Text("${row.rowTitle} (${row.category})") }
                    P {
                        row.signs.forEachIndexed { index, sign ->
                            if (index > 0) Text("  ·  ")
                            Span(attrs = { attr("lang", "ja") }) {
                                Text("${sign.hiragana} / ${sign.katakana}")
                            }
                            Text(" (${sign.romaji})")
                        }
                    }
                    for (sign in row.signs.filter { it.note != null }) {
                        P {
                            Span(attrs = { attr("lang", "ja") }) { Text("${sign.hiragana} / ${sign.katakana}") }
                            Text(": ${sign.note}")
                        }
                    }
                }
            }
        }

        TagElement<HTMLElement>("details", applyAttrs = {
            style {
                property("width", "100%")
                property("text-align", "start")
                property("margin-block", "0.5rem")
                property("overflow-wrap", "anywhere")
            }
        }) {
            TagElement<HTMLElement>("summary", applyAttrs = {}) {
                H3 { Text("Reference 2: Japanese Grammar from 0 (6 Core Sentence Pillars)") }
            }
            for (card in ZeroBeginnerReference.grammarFoundations) {
                Section(attrs = {
                    style {
                        property("border", "1px solid currentColor")
                        property("border-radius", "0.5rem")
                        property("padding", "0.75rem")
                        property("margin-block", "0.5rem")
                    }
                }) {
                    H3 { Text(card.title) }
                    P { Text("Pattern: ${card.pattern}") }
                    P { Text(card.explanation) }
                    P(attrs = { attr("lang", "ja") }) { Text("Example: ${card.exampleSurface}") }
                    P(attrs = { attr("lang", "ja") }) { Text("Reading: ${card.exampleReading}") }
                    P { Text("Meaning: ${card.exampleTranslation}") }
                }
            }
        }

        TagElement<HTMLElement>("details", applyAttrs = {
            style {
                property("width", "100%")
                property("text-align", "start")
                property("margin-block", "0.5rem")
                property("overflow-wrap", "anywhere")
            }
        }) {
            TagElement<HTMLElement>("summary", applyAttrs = {}) {
                H3 { Text("Reference 3: Workplace Etiquette & Daily Life Survival Phrases") }
            }
            for (phrase in ZeroBeginnerReference.survivalPhrases) {
                Section(attrs = {
                    style {
                        property("border", "1px solid currentColor")
                        property("border-radius", "0.5rem")
                        property("padding", "0.75rem")
                        property("margin-block", "0.5rem")
                    }
                }) {
                    H3 {
                        Span(attrs = { attr("lang", "ja") }) { Text(phrase.surface) }
                        Text(" (${phrase.category})")
                    }
                    P(attrs = { attr("lang", "ja") }) { Text("Reading: ${phrase.reading}") }
                    P { Text("Romaji: ${phrase.romaji} · Meaning: ${phrase.meaning}") }
                    P { Text("When to use: ${phrase.usageNote}") }
                }
            }
        }
    }
}
