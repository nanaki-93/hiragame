package com.github.nanaki_93.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.nanaki_93.content.AnswerRepresentation
import com.github.nanaki_93.content.ChoiceExercise
import com.github.nanaki_93.content.CompletionExercise
import com.github.nanaki_93.content.Exercise
import com.github.nanaki_93.content.JapaneseText
import com.github.nanaki_93.content.ProductionExercise
import com.github.nanaki_93.content.ReadingExercise
import com.github.nanaki_93.practice.Assessment
import com.github.nanaki_93.practice.InvalidReason
import com.github.nanaki_93.practice.PracticeAnswer
import com.github.nanaki_93.practice.PracticeSession
import com.github.nanaki_93.components.styles.Styles
import com.github.nanaki_93.components.widgets.PrimaryButton
import com.github.nanaki_93.components.widgets.SecondaryButton
import com.github.nanaki_93.content.BrowserContentTextSource
import com.github.nanaki_93.content.BundledContentLoader
import com.github.nanaki_93.content.EmptyContentReason
import com.github.nanaki_93.practice.LocalPracticeCoordinator
import com.github.nanaki_93.practice.LocalPracticeState
import com.github.nanaki_93.practice.PracticeCommand
import com.github.nanaki_93.practice.SessionView
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.silk.style.toModifier
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.dom.Fieldset
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Legend
import org.jetbrains.compose.web.dom.TagElement
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.TextArea
import org.w3c.dom.HTMLElement
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Main
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

/** Home owns only this page's in-memory coordinator; no account or backend is required. */
@Page
@Composable
fun HomePage() {
    val scope = rememberCoroutineScope()
    val coordinator = remember { LocalPracticeCoordinator(scope, BundledContentLoader(BrowserContentTextSource())) }
    val state by coordinator.state.collectAsState()

    DisposableEffect(coordinator) {
        coordinator.load()
        onDispose { coordinator.dispose() }
    }

    Main {
        Box(Styles.GameContainer.toModifier()) {
            Column(
                modifier = Styles.Card.toModifier(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.cssRem),
            ) {
                H1 { Text("Hiragame") }
                when (val current = state) {
                    LocalPracticeState.Loading -> {
                        H2 { Text("Loading reviewed practice") }
                        P { Text("Reading this site's bundled content. No account is needed.") }
                    }
                    is LocalPracticeState.Empty -> {
                        H2 { Text("No practice available yet") }
                        P {
                            Text(when (current.reason) {
                                EmptyContentReason.EMPTY_CATALOG -> "The content catalog is empty. Reload to check again."
                                EmptyContentReason.NO_PRACTICE -> "No reviewed practice sets are listed yet. Reload to check again."
                                EmptyContentReason.EMPTY_PRACTICE_SETS -> "Reviewed practice sets have no exercises yet. Reload to check again."
                            })
                        }
                        PrimaryButton("Reload practice", onClick = coordinator::retryLoad)
                    }
                    is LocalPracticeState.Error -> {
                        H2 { Text("Practice could not load") }
                        P { Text(current.safeMessage) }
                        PrimaryButton("Retry loading", onClick = coordinator::retryLoad)
                    }
                    is LocalPracticeState.Ready -> {
                        val session = current.session
                        if (current.operationError != null) {
                            P { Text(current.operationError.safeMessage) }
                            SecondaryButton("Retry action", onClick = { coordinator.retryOperation(current.operationError) })
                            SecondaryButton("Back to practice sets", onClick = { coordinator.leave(current.operationError) })
                        } else if (session == null) {
                            H2 { Text("Practice locally") }
                            P { Text("A short session with reviewed exercises. Answers stay in this session only; reloading starts fresh.") }
                            // One clear invitation for the seed; extra sets get their own titled actions.
                            val sets = current.availablePracticeSets.values.toList()
                            for (set in sets) {
                                P { Text("${set.title} · ${set.exercises.size} exercises. ${set.description}") }
                                PrimaryButton(
                                    text = if (sets.size == 1) "Start practice" else "Start ${set.title}",
                                    onClick = { coordinator.start(set.id) },
                                )
                            }
                            if (sets.isEmpty()) P { Text("No exercises are available. Reload to check again.") }
                            SecondaryButton("Reload practice", onClick = coordinator::load)
                        } else {
                            when (val view = session.view) {
                                is SessionView.Prompt -> PracticePrompt(session, view) { answer ->
                                    coordinator.dispatch(PracticeCommand.Submit(session.id, session.revision, answer))
                                }
                                else -> {
                                    H2 { Text("Practice session") }
                                    P { Text("Session in progress. Feedback and navigation are shown in the next practice-surface step.") }
                                }
                            }
                            SecondaryButton("Back to practice sets", onClick = {
                                coordinator.dispatch(PracticeCommand.Leave(session.id, session.revision))
                            })
                        }
                    }
                }
            }
        }
    }
}

/** Keep raw drafts local to the prompt. The evaluator, not the UI, judges blank and authored answers. */
internal fun promptAnswer(
    exercise: Exercise, choiceId: String?, draft: String, assessment: Assessment?, exampleRevealed: Boolean,
): PracticeAnswer? = when (exercise) {
    is ChoiceExercise -> PracticeAnswer.Choice(choiceId ?: "")
    is ReadingExercise, is CompletionExercise -> PracticeAnswer.Text(draft)
    // The session reducer accepts typed self-assessments; the prompt must require a prior example reveal.
    is ProductionExercise -> if (exampleRevealed) assessment?.let { PracticeAnswer.SelfAssessment(draft, it) } else null
}

@Composable
private fun PracticePrompt(session: PracticeSession, view: SessionView.Prompt, submit: (PracticeAnswer) -> Unit) {
    val exercise = session.plan[view.index].exercise
    // This composable leaves composition on feedback; retry or another exercise gets fresh drafts.
    var draft by remember(session.id, exercise.id) { mutableStateOf("") }
    var selectedChoice by remember(session.id, exercise.id) { mutableStateOf<String?>(null) }
    var assessment by remember(session.id, exercise.id) { mutableStateOf<Assessment?>(null) }
    var missingAssessment by remember(session.id, exercise.id) { mutableStateOf(false) }
    var exampleRevealed by remember(session.id, exercise.id) { mutableStateOf(false) }

    H2 { Text("Exercise ${view.index + 1} of ${session.plan.size}") }
    P { Text("In-memory session · ${session.counts.completed} resolved of ${session.plan.size}. Answers are not saved.") }
    P { Text(when (exercise) {
        is ChoiceExercise -> exercise.prompt
        is ReadingExercise -> exercise.prompt
        is CompletionExercise -> exercise.prompt
        is ProductionExercise -> exercise.prompt
    }) }
    when (exercise) {
        is ChoiceExercise -> Fieldset {
            Legend { Text("Choose one answer") }
            for (option in exercise.options) {
                Label(attrs = { classes("practice-choice") }) {
                    Input(type = InputType.Radio, attrs = {
                        attr("name", "choice-${session.id}-${exercise.id}")
                        checked(selectedChoice == option.id)
                        onChange { selectedChoice = option.id }
                    })
                    val japanese = option.text
                    if (japanese != null) JapanesePassage(japanese) else Text(option.label.orEmpty())
                }
            }
        }
        is ReadingExercise -> {
            P { JapanesePassage(exercise.stimulus) }
            Label(attrs = { attr("for", "practice-response") }) {
                Text(if (exercise.answerRepresentation == AnswerRepresentation.KANA) "Reading in kana" else "Reading in romaji")
            }
            Input(type = InputType.Text, attrs = {
                id("practice-response")
                classes("practice-answer")
                attr("maxlength", "200")
                value(draft)
                onInput { draft = it.value }
            })
        }
        is CompletionExercise -> {
            val parts = exercise.template.split("{blank}")
            P { Span(attrs = { attr("lang", "ja"); classes("practice-japanese"); style { property("overflow-wrap", "anywhere") } }) {
                Text(parts[0]); Text("＿＿＿"); Text(parts[1])
            } }
            Label(attrs = { attr("for", "practice-response") }) { Text("Fill the blank in Japanese") }
            Input(type = InputType.Text, attrs = {
                id("practice-response")
                classes("practice-answer")
                attr("maxlength", "200")
                value(draft)
                onInput { draft = it.value }
            })
        }
        is ProductionExercise -> {
            Label(attrs = { attr("for", "practice-response") }) { Text("Your response in Japanese") }
            TextArea(value = draft, attrs = {
                id("practice-response")
                classes("practice-answer")
                attr("maxlength", "1000")
                onInput { draft = it.value }
            })
            if (!exampleRevealed) {
                P { Text("Reveal the authored example before assessing your response. This is not automatically graded.") }
                PrimaryButton("Reveal example and criteria", onClick = { exampleRevealed = true })
            } else {
                // A prompt-local reveal leaves the response available for self-assessment. The reducer's
                // Reveal command resolves an item as revealed instead, so it cannot serve this step.
                for ((index, example) in exercise.exampleResponses.withIndex()) {
                    P { Text("Example response ${index + 1} (not your answer):") }
                    P { JapanesePassage(example) }
                    val translation = example.translation
                    if (translation != null) P { Text(translation) }
                }
                P { Text("Compare your response with the authored criteria. Your assessment is not an automatic grade.") }
                for ((index, criterion) in exercise.criteria.withIndex()) {
                    P { Text("Criterion ${index + 1}: $criterion") }
                }
                Fieldset {
                    Legend { Text("Your self-assessment") }
                    for ((rating, label) in listOf(Assessment.MET_CRITERIA to "I met the criteria", Assessment.NEEDS_PRACTICE to "I need more practice")) {
                        Label(attrs = { classes("practice-choice") }) {
                            Input(type = InputType.Radio, attrs = {
                                attr("name", "assessment-${session.id}-${exercise.id}")
                                checked(assessment == rating)
                                onChange { assessment = rating; missingAssessment = false }
                            })
                            Text(label)
                        }
                    }
                }
            }
        }
    }
    val validation = view.validation
    if (validation != null) P(attrs = { attr("role", "alert") }) {
        Text(when (validation) {
            InvalidReason.BLANK_INPUT -> "Enter a response or choose an answer before submitting."
            InvalidReason.UNKNOWN_CHOICE -> "Choose one of the available answers."
            InvalidReason.WRONG_ANSWER_TYPE -> "This answer type does not match the exercise."
        })
    }
    if (missingAssessment) P(attrs = { attr("role", "alert") }) { Text("Choose a self-assessment before submitting.") }
    if (exercise !is ProductionExercise || exampleRevealed) {
        PrimaryButton("Submit answer", onClick = {
            val answer = promptAnswer(exercise, selectedChoice, draft, assessment, exampleRevealed)
            if (answer == null) missingAssessment = true else submit(answer)
        })
    }
}

/** Text nodes only: authored segments with readings become real ruby, never injected markup. */
@Composable
private fun JapanesePassage(text: JapaneseText) {
    Span(attrs = { attr("lang", "ja"); classes("practice-japanese"); style { property("overflow-wrap", "anywhere") } }) {
        if (text.segments.isEmpty()) Text(text.surface)
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
