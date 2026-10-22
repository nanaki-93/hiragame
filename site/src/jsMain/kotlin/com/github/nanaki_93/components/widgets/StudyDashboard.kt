package com.github.nanaki_93.components.widgets

import androidx.compose.runtime.Composable
import com.github.nanaki_93.content.DocumentKind
import com.github.nanaki_93.content.ZeroToJapanRoadmap
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.attrsModifier
import com.varabyte.kobweb.silk.components.navigation.Link
import org.jetbrains.compose.web.dom.*

@Composable
internal fun StudyWelcome() {
    Div(attrs = { classes("study-welcome") }) {
        Div(attrs = { classes("welcome-copy") }) {
            Span(attrs = { classes("eyebrow") }) { Text("YOUR JAPANESE JOURNEY") }
            H1 { Text("A little closer to"); Br(); Em { Text("life in Japan.") } }
            P { Text("From your first kana to your next engineering team.\nOne small step, every day.") }
            Div(attrs = { classes("welcome-note") }) {
                StudyIcon("clock"); Text("Make a little space for learning today.")
            }
        }
        Div(attrs = { classes("welcome-art"); attr("aria-hidden", "true") }) {
            Img(src = "/hiragame/images/japan-journey.svg", alt = "", attrs = { attr("width", "640"); attr("height", "330") })
            Span(attrs = { classes("art-caption") }) { Text("THE JOURNEY STARTS WITH あ") }
        }
    }
}

@Composable
internal fun TodayStudySection(
    roadmap: ZeroToJapanRoadmap,
    dueCount: Int,
    resumeLesson: Pair<String, String>?,
    onPractice: (String, Boolean) -> Unit,
) {
    val nextPhase = roadmap.phases.firstOrNull { it.completedCount < it.totalCount }
    val recommended = roadmap.recommendedPractice
    val usePractice = recommended != null && (recommended.resumable || nextPhase?.nextItemKind == DocumentKind.PRACTICE || nextPhase == null)
    Div(attrs = { classes("today-grid") }) {
        Section(attrs = { classes("next-study-card"); attr("aria-labelledby", "next-study-title") }) {
            Div(attrs = { classes("next-study-symbol"); attr("aria-hidden", "true") }) {
                if (resumeLesson != null || !usePractice) StudyIcon("book")
                else Span(attrs = { attr("lang", "ja") }) { Text("あ") }
            }
            Div(attrs = { classes("next-study-copy") }) {
                Span(attrs = { classes("eyebrow") }) { Text(if (resumeLesson != null || recommended?.resumable == true) "PICK UP WHERE YOU LEFT OFF" else "YOUR NEXT SMALL STEP") }
                H2(attrs = { id("next-study-title") }) {
                    Text(resumeLesson?.second ?: if (usePractice) recommended!!.title else nextPhase?.nextItemTitle ?: "Explore a new situation")
                }
                P {
                    Text(if (resumeLesson != null) "Your saved place is ready when you are."
                    else if (usePractice) "${recommended!!.exerciseCount} exercises · ${recommended.topicTitle}"
                    else "Put your Japanese into practice, one conversation at a time.")
                }
                if (resumeLesson != null) {
                    Link("/lesson?lessonId=${resumeLesson.first}", modifier = Modifier.attrsModifier { classes("action-link") }) {
                        Text("Continue lesson"); StudyIcon("arrow")
                    }
                } else if (usePractice) {
                    PrimaryButton(if (recommended!!.resumable) "Continue practice →" else "Let's begin →", onClick = { onPractice(recommended.setId, recommended.resumable) })
                } else {
                    Link(nextPhase?.nextItemId?.let { "/lesson?lessonId=$it" } ?: "/topics", modifier = Modifier.attrsModifier { classes("action-link") }) {
                        Text("Explore lesson"); StudyIcon("arrow")
                    }
                }
            }
        }
        Section(attrs = { classes("review-summary"); attr("aria-labelledby", "review-summary-title") }) {
            Div(attrs = { classes("review-summary-top") }) {
                StudyIcon("review"); Span(attrs = { classes("eyebrow") }) { Text("A LITTLE REPETITION") }
            }
            H2(attrs = { id("review-summary-title") }) { Text(if (dueCount > 0) "$dueCount phrases to revisit" else "Let it sink in.") }
            P { Text(if (dueCount > 0) "A quick review helps yesterday's learning stay with you." else "As you learn, your phrases will be ready to review here.") }
            Link("/review", modifier = Modifier.attrsModifier { classes("text-link") }) {
                Text(if (dueCount > 0) "Review phrases" else "Your phrase review"); StudyIcon("arrow")
            }
        }
    }
}

@Composable
internal fun StudyProgress(label: String, completed: Int, total: Int) {
    Div(attrs = {
        classes("progress-track")
        attr("role", "progressbar"); attr("aria-label", label)
        attr("aria-valuemin", "0"); attr("aria-valuemax", total.coerceAtLeast(1).toString())
        attr("aria-valuenow", completed.coerceIn(0, total.coerceAtLeast(1)).toString())
        attr("aria-valuetext", "$completed of $total completed")
    }) {
        Div(attrs = {
            classes("progress-fill")
            style { property("width", "${if (total > 0) (completed.coerceIn(0, total) * 100) / total else 0}%") }
        })
    }
}
