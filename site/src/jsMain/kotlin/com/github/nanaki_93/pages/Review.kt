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
    Main(attrs = { classes("learning-page") }) {
        H1 { Text("Phrase review") }
        Link("/") { Text("Home") }; Link("/topics") { Text("Learn a lesson") }
        P(attrs = { attr("role", "status") }) { Text("Local save: ${saveStatusMessage(saved)}") }
        val data = bundle
        when {
            error != null -> { P { Text(error!!) }; PrimaryButton("Retry content", onClick = { load++ }) }
            data == null -> P { Text("Loading reviewed phrases…") }
            else -> {
                val now = kotlin.js.Date.now().toLong()
                val cards = eligibleReviewCards(data.lessons.values, data.practiceSets.values, saved.snapshot)
                val due = reviewDueCount(cards, saved.snapshot, now)
                P { Text("$due due · ${saved.snapshot.reviewItems.size} phrases practised · ${saved.snapshot.lessonProgress.count { it.completedAtEpochMs != null }} lessons completed") }
                P { Text("Due dates appear in your device's local time. Up to 10 items, including at most 3 new items, per session. Ratings describe recall, not fluency.") }
                val current = session
                // Force a render after transient reveal/advance; saved changes alone do not cover reveal.
                key(tick) {
                    when {
                        current == null -> {
                            val plan = planReview(cards, saved.snapshot, now)
                            if (due == 0) P { Text("Nothing is due. Learn something new, introduce a few studied phrases, or deliberately practise ahead.") }
                            if (plan.isNotEmpty()) PrimaryButton("Start review (${plan.size})", onClick = { session = LocalReviewSession(plan, owner) })
                            val ahead = planReview(cards, saved.snapshot, now, ahead = true)
                            if (ahead.isNotEmpty()) SecondaryButton("Practise ahead", onClick = { session = LocalReviewSession(ahead, owner) })
                        }
                        current.expired -> P { Text("Progress was replaced or reloaded. Start a fresh review to use the current schedule.") }
                        current.complete -> P { Text("Session complete: ${current.index} phrases practised. Again returns in 10 minutes; other intervals range from 1 to 30 days.") }
                        else -> current.current?.let { planned ->
                            val card = planned.card
                            H2 { Text("Recall ${current.index + 1} of ${current.plan.size}") }
                            P { Text(card.context) }; P { Text(card.cue) }
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
                                for ((rating, label) in listOf(ReviewOutcome.AGAIN to "Again", ReviewOutcome.HARD to "Hard", ReviewOutcome.GOOD to "Good"))
                                    SecondaryButton(label, onClick = { current.rate(index, rating); tick++ })
                            }
                            current.error?.let { P(attrs = { attr("role", "alert") }) { Text(it) } }
                        }
                    }
                    if (current != null) SecondaryButton("Exit review", onClick = { session = null; tick++ })
                }
            }
        }
    }
}
