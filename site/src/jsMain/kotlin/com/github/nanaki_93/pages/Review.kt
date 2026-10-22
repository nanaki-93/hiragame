package com.github.nanaki_93.pages

import androidx.compose.runtime.*
import com.github.nanaki_93.LocalProgress
import com.github.nanaki_93.content.*
import com.github.nanaki_93.components.widgets.*
import com.github.nanaki_93.practice.authoredFeedback
import com.github.nanaki_93.progress.*
import com.github.nanaki_93.review.*
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.silk.components.navigation.Link
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.web.dom.*

@Page("/review")
@Composable
fun ReviewPage() {
    val owner = LocalProgress.current
    val saved by owner.state.collectAsState()
    var bundle by remember { mutableStateOf<BundledContent?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var load by remember { mutableStateOf(0) }
    var session by remember { mutableStateOf<LocalReviewSession?>(null) }
    var tick by remember { mutableStateOf(0) }
    com.github.nanaki_93.offline.SessionActivity(session != null && session?.complete != true && session?.expired != true)
    LaunchedEffect(load) {
        error = null
        try { bundle = (BundledContentLoader(BrowserContentTextSource()).load() as? CatalogLoad.Ready)?.content
            if (bundle == null) error = "No reviewed content is available." }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Content could not load. Retry or return to Topics." }
    }
    Main(attrs = { classes("learning-page", "review-page") }) {
        Span(attrs = { classes("eyebrow") }) { Text("A LITTLE REPETITION GOES A LONG WAY") }
        H1 { Text("Make it stay.") }
        P { Text("Bring familiar words back to mind. A few phrases, a little more confidence.") }
        val data = bundle
        when {
            error != null -> { P { Text(error!!) }; PrimaryButton("Retry content", onClick = { load++ }) }
            data == null -> P { Text("Loading reviewed phrases…") }
            else -> {
                val now = kotlin.js.Date.now().toLong()
                val cards = eligibleReviewCards(data.lessons.values, data.practiceSets.values, saved.snapshot)
                val due = reviewDueCount(cards, saved.snapshot, now)
                if (session == null) Div(attrs = { classes("review-stats") }) {
                    for ((count, label) in listOf(due to "ready to revisit", saved.snapshot.reviewItems.size to "phrases practised",
                        saved.snapshot.lessonProgress.count { it.completedAtEpochMs != null } to "lessons completed")) {
                        Div { Span(attrs = { classes("review-stat-value") }) { Text("$count") }; Span { Text(label) } }
                    }
                }
                val current = session
                // Force a render after transient reveal/advance; saved changes alone do not cover reveal.
                key(tick) {
                    when {
                        current == null -> Section(attrs = { classes("review-start") }) {
                            val plan = planReview(cards, saved.snapshot, now)
                            val ahead = planReview(cards, saved.snapshot, now, ahead = true)
                            Div(attrs = { classes("empty-state-symbol"); attr("aria-hidden", "true") }) { StudyIcon("review") }
                            H2 { Text(if (plan.isNotEmpty()) "Ready for a little review?" else if (cards.isEmpty()) "Your first phrases are ahead." else "All caught up.") }
                            P { Text(if (plan.isNotEmpty()) "Recall a phrase, reveal the example, then tell us how it felt. Up to 10 phrases at a time."
                                else if (cards.isEmpty()) "Finish a practice set or a lesson, then return here to help your new words stick."
                                else "Nothing is due right now. Explore a new lesson or revisit familiar phrases at your own pace.") }
                            if (plan.isNotEmpty()) PrimaryButton("Start review (${plan.size})", onClick = { session = LocalReviewSession(plan, owner) })
                            if (ahead.isNotEmpty()) SecondaryButton("Practise ahead", onClick = { session = LocalReviewSession(ahead, owner) })
                            if (plan.isEmpty()) P { Link("/") { Text("Continue learning →") } }
                        }
                        current.expired -> P { Text("Progress was replaced or reloaded. Start a fresh review to use the current schedule.") }
                        current.complete -> Section(attrs = { classes("empty-state") }) {
                            FocusStudyHeading("review-heading", "complete")
                            StudyIcon("check")
                            H2(attrs = { id("review-heading"); attr("tabindex", "-1") }) { Text("A little practice, well spent.") }
                            P { Text("${current.index} phrases revisited. Again returns in 10 minutes; other intervals range from 1 to 30 days.") }
                            Link("/") { Text("Back to my path →") }
                        }
                        else -> current.current?.let { planned ->
                            val card = planned.card
                            FocusStudyHeading("review-heading", current.index to current.revealed)
                            StudyProgress("Review phrases completed", current.index, current.plan.size)
                            H2(attrs = { id("review-heading"); attr("tabindex", "-1") }) { Text("Recall ${current.index + 1} of ${current.plan.size}") }
                            P(attrs = { classes("muted") }) { Text(card.context) }
                            P(attrs = { classes("review-cue") }) { Text(card.cue) }
                            planned.previous?.dueAtEpochMs?.let { dueAt ->
                                val offset = -kotlin.js.Date(dueAt.toDouble()).getTimezoneOffset().toInt()
                                val todayOffset = -kotlin.js.Date(now.toDouble()).getTimezoneOffset().toInt()
                                val day = localReviewDay(dueAt, offset) - localReviewDay(now, todayOffset)
                                P { Text("Due: ${kotlin.js.Date(dueAt.toDouble()).toLocaleString()} (${if (day < 0) "earlier day" else if (day == 0L) "today" else "upcoming"})") }
                            }
                            when (val exercise = card.exercise) {
                                is CompletionExercise -> { P { Text(exercise.prompt) }; P(attrs = { attr("lang", "ja") }) { Text(exercise.template.replace("{blank}", "＿＿＿")) } }
                                is ReadingExercise -> { P { Text(exercise.prompt) }; JapanesePassage(exercise.stimulus, false, practiceTypography = false) }
                                is ChoiceExercise -> P { Text(exercise.prompt) }
                                is ProductionExercise -> P { Text(exercise.prompt) }
                                null -> Unit
                            }
                            P { Text("Recall a response aloud or silently, then reveal the reviewed example.") }
                            val index = current.index
                            if (!current.revealed) PrimaryButton("Reveal example", onClick = { current.reveal(index); tick++ })
                            else {
                                card.phrase?.let { JapaneseStudyText(it.text, saved.snapshot.preferences.copy(showReadings = true, showTranslation = true)); P { Text("${it.usage} · ${it.register}") } }
                                card.exercise?.let { AuthoredFeedbackContent(authoredFeedback(it), saved.snapshot.preferences.copy(showReadings = true, showTranslation = true)) }
                                Div(attrs = { classes("review-ratings"); attr("role", "group"); attr("aria-label", "How well did you recall this phrase?") }) {
                                    for ((rating, label) in listOf(ReviewOutcome.AGAIN to "Again", ReviewOutcome.HARD to "Hard", ReviewOutcome.GOOD to "Good"))
                                        SecondaryButton(label, onClick = { current.rate(index, rating); tick++ })
                                }
                                P(attrs = { classes("muted") }) { Text("Again: in 10 minutes · Hard: sooner · Good: a longer interval") }
                            }
                            current.error?.let { P(attrs = { attr("role", "alert") }) { Text(it) } }
                        }
                    }
                    if (current != null) SecondaryButton("Exit review", onClick = { session = null; tick++ })
                }
                P(attrs = { classes("review-footnote") }) { Text("A session includes at most 3 new items. Ratings describe recall, not fluency. Due dates use your device's local time.") }
            }
        }
    }
}
