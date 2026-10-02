package com.github.nanaki_93.components.widgets

import androidx.compose.runtime.*
import com.github.nanaki_93.content.*
import com.github.nanaki_93.conversation.ConversationSession
import com.github.nanaki_93.practice.PracticeAnswer
import com.github.nanaki_93.progress.SavePreferences
import org.jetbrains.compose.web.dom.*

@Composable
fun GuidedConversation(lesson: Lesson, preferences: SavePreferences) {
    if (lesson.conversationGraph == null) return
    var session by remember(lesson) { mutableStateOf<ConversationSession?>(null) }
    var support by remember(lesson) { mutableStateOf(false) }
    Section {
        H3 { Text("Guided conversation") }
        P { Text("Practise the authored branches, then try the open role-play. Completing a branch is not a grammar score. Responses stay in this session.") }
        val current = session
        if (current == null || current.exited) {
            PrimaryButton("Start guided conversation", onClick = { session = ConversationSession.start(lesson) })
        } else {
            val revision = current.revision
            when (val node = current.node) {
                is PromptNode -> {
                    P { Text(lesson.dialogue.speakers.first { it.id == node.speakerId }.name) }
                    JapaneseStudyText(node.text, preferences)
                    PrimaryButton("Respond", onClick = { session = session?.next(revision) })
                }
                is ChoiceInteractionNode -> {
                    val task = current.exercise as ChoiceExercise
                    P { Text(task.prompt) }
                    if (current.pendingNodeId == null) task.options.forEach { option ->
                        option.text?.let { JapaneseStudyText(it, preferences) }
                        SecondaryButton(option.label ?: "Say: ${option.text!!.surface}", onClick = {
                            session = session?.answer(revision, PracticeAnswer.Choice(option.id))
                        })
                    }
                }
                is CompletionInteractionNode -> key(node.id) {
                    val task = current.exercise as CompletionExercise
                    P { Text(task.prompt) }
                    P(attrs = { attr("lang", "ja") }) { Text(task.template.replace("{blank}", "＿＿＿")) }
                    var draft by remember { mutableStateOf("") }
                    val guard = remember { JapaneseSubmissionGuard() }
                    if (current.pendingNodeId == null) {
                        Label(attrs = { attr("for", "conversation-answer") }) { Text("Fill the blank") }
                        JapaneseAnswerInput(draft, { draft = it }, guard, {
                            if (guard.canSubmit(false)) session = session?.answer(revision, PracticeAnswer.Text(draft))
                        }, inputId = "conversation-answer")
                        PrimaryButton("Check response", onClick = {
                            if (guard.canSubmit(false)) session = session?.answer(revision, PracticeAnswer.Text(draft))
                        })
                        SecondaryButton("Reveal example and continue", onClick = { session = session?.reveal(revision) })
                    } else JapaneseStudyText(task.expectedCompletedExample, preferences)
                }
                is TerminalNode -> {
                    P { Text("Conversation complete. ${node.message}") }
                    P { Text("Continue with the role-play to self-assess your own response.") }
                }
            }
            current.feedback?.let { P(attrs = { attr("role", "status") }) { Text(it) } }
            if (current.pendingNodeId != null) PrimaryButton("Continue conversation", onClick = { session = session?.next(revision) })
            SecondaryButton(if (support) "Hide phrase bank" else "Show phrase bank", onClick = { support = !support })
            if (support) lesson.phrases.forEach { JapaneseStudyText(it.text, preferences); P { Text(it.usage) } }
            SecondaryButton("Restart conversation", onClick = { session = session?.restart() })
            SecondaryButton("Exit conversation", onClick = { session = session?.exit() })
        }
    }
}
