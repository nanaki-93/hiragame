package com.github.nanaki_93.components.widgets

import androidx.compose.runtime.Composable
import com.github.nanaki_93.content.JapaneseText
import com.github.nanaki_93.practice.AuthoredFeedback
import com.github.nanaki_93.practice.ReadingExpectedAnswer
import com.github.nanaki_93.progress.SavePreferences
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.TagElement
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLElement

/** An unresolved answer-bearing stimulus or choice receives NO aids, regardless of preferences. */
internal data class VisibleAids(val ruby: Boolean, val reading: String?, val meaning: String?, val romaji: String?)

internal fun visibleAids(text: JapaneseText, preferences: SavePreferences, answerHidden: Boolean): VisibleAids {
    if (answerHidden) return VisibleAids(false, null, null, null)
    return VisibleAids(
        preferences.showReadings, text.reading.takeIf { preferences.showReadings },
        (text.translation ?: text.gloss).takeIf { preferences.showTranslation },
        text.romaji?.takeIf { preferences.showRomaji },
    )
}

/** All authored content is emitted as escaped text nodes; only authored segments can become ruby. */
@Composable
internal fun JapanesePassage(text: JapaneseText, showRuby: Boolean, practiceTypography: Boolean = true) {
    Span(attrs = {
        attr("lang", "ja")
        if (practiceTypography) classes("practice-japanese")
        style { property("overflow-wrap", "anywhere"); property("min-width", "0") }
    }) {
        if (text.segments.isEmpty() || !showRuby) Text(text.surface)
        else for (segment in text.segments) {
            val reading = segment.reading
            if (reading == null) Text(segment.surface)
            else TagElement<HTMLElement>("ruby", applyAttrs = null) {
                Text(segment.surface)
                TagElement<HTMLElement>("rt", applyAttrs = null) { Text(reading) }
            }
        }
    }
}

/** Optional support for non-answer-bearing content and deliberately revealed examples. */
@Composable
internal fun JapaneseStudyText(text: JapaneseText, preferences: SavePreferences) {
    val aids = visibleAids(text, preferences, answerHidden = false)
    P { JapanesePassage(text, aids.ruby) }
    aids.reading?.let { P { Text("Reading: $it") } }
    aids.meaning?.let { P { Text("Meaning/gloss: $it") } }
    aids.romaji?.let { P { Text("Authored romaji: $it") } }
}

/** Feedback is not an optional hint: provide authored reading and meaning even with aids off. */
@Composable
internal fun JapaneseFeedbackText(text: JapaneseText, preferences: SavePreferences) {
    P { JapanesePassage(text, showRuby = preferences.showReadings) }
    P { Text("Reading: ${text.reading}") }
    text.translation?.let { P { Text("Meaning: $it") } }
    text.gloss?.let { P { Text("Gloss: $it") } }
    text.romaji?.takeIf { preferences.showRomaji }?.let { P { Text("Authored romaji: $it") } }
}

/** The read-only catalog keeps its original labels and contextual metadata. */
@Composable
internal fun PreviewJapaneseText(text: JapaneseText, preferences: SavePreferences) {
    val aids = visibleAids(text, preferences, answerHidden = false)
    P(attrs = { style { property("overflow-wrap", "anywhere"); property("min-width", "0") } }) {
        JapanesePassage(text, aids.ruby, practiceTypography = false)
    }
    aids.reading?.let { P { Text("Reading: $it") } }
    aids.meaning?.let { P { Text("Meaning: $it") } }
    aids.romaji?.let { P { Text("Authored romaji: $it") } }
    text.context?.let { P { Text("Context: $it") } }
    text.register?.let { P { Text("Register: $it") } }
}

/** Shared authored feedback presentation; evaluation belongs exclusively to the session reducer. */
@Composable
internal fun AuthoredFeedbackContent(feedback: AuthoredFeedback, preferences: SavePreferences) {
    when (feedback) {
        is AuthoredFeedback.Choice -> {
            P { Text("Correct option:") }
            val option = feedback.correctOption
            val japanese = option.text
            if (japanese != null) JapaneseFeedbackText(japanese, preferences) else P { Text(option.label.orEmpty()) }
            P { Text("Explanation: ${feedback.explanation}") }
        }
        is AuthoredFeedback.Reading -> {
            P { Text("Reading stimulus:") }
            JapaneseFeedbackText(feedback.stimulus, preferences)
            for ((number, answer) in feedback.acceptedAnswers.withIndex()) {
                P { Text("Accepted reading ${number + 1} (${feedback.representation.name.lowercase()}):") }
                when (answer) {
                    is ReadingExpectedAnswer.Kana -> JapaneseFeedbackText(answer.text, preferences)
                    is ReadingExpectedAnswer.Romaji -> P { Text(answer.text) }
                }
            }
            P { Text("Explanation: ${feedback.explanation}") }
        }
        is AuthoredFeedback.Completion -> {
            for ((number, fill) in feedback.acceptedFills.withIndex()) {
                P { Text("Accepted fill ${number + 1}:") }
                JapaneseFeedbackText(fill, preferences)
            }
            P { Text("Completed example:") }
            JapaneseFeedbackText(feedback.completedExample, preferences)
            P { Text("Explanation: ${feedback.explanation}") }
        }
        is AuthoredFeedback.Production -> {
            P { Text("Authored examples, not a unique correct answer:") }
            for ((number, example) in feedback.examples.withIndex()) {
                P { Text("${example.label} ${number + 1}:") }
                JapaneseFeedbackText(example.text, preferences)
            }
            for ((number, criterion) in feedback.criteria.withIndex()) {
                P { Text("${criterion.label} ${number + 1}: ${criterion.text}") }
            }
        }
    }
}
