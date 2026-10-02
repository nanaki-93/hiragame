package com.github.nanaki_93.content

import com.github.nanaki_93.conversation.ConversationSession
import com.github.nanaki_93.practice.PracticeAnswer
import kotlin.test.*

class ConversationIntegrationTest {
    private fun lessons(): List<Lesson> {
        val fs = js("require('fs')")
        val path = js("require('path')")
        var root = js("process.cwd()") as String
        while (!(fs.existsSync(path.join(root, "PLAN.md")) as Boolean)) root = path.dirname(root) as String
        val dir = path.join(root, "site/src/jsMain/resources/public/content/lessons")
        return (fs.readdirSync(dir) as Array<String>).map { ContentCodec.decodeLesson(fs.readFileSync(path.join(dir, it), "utf8") as String) }
    }
    @Test fun everyCanonicalConversationTerminatesThroughEveryChoice() {
        for (lesson in lessons()) {
            val start = ConversationSession.start(lesson)
            fun walk(state: ConversationSession, depth: Int) {
                assertTrue(depth <= lesson.conversationGraph!!.nodes.size * 2)
                when (val node = state.node) {
                    is TerminalNode -> assertTrue(state.complete)
                    is PromptNode -> walk(state.next(state.revision), depth + 1)
                    is ChoiceInteractionNode -> node.transitions.forEach { branch ->
                        val next = state.answer(state.revision, PracticeAnswer.Choice(branch.optionId))
                        assertNotNull(next.feedback)
                        assertSame(next, next.answer(state.revision, PracticeAnswer.Choice(branch.optionId)))
                        walk(next.next(next.revision), depth + 1)
                    }
                    is CompletionInteractionNode -> {
                        val exercise = state.exercise as CompletionExercise
                        val wrong = state.answer(state.revision, PracticeAnswer.Text("unrelated"))
                        assertNull(wrong.pendingNodeId)
                        assertEquals(state.nodeId, wrong.nodeId)
                        val next = wrong.answer(wrong.revision, PracticeAnswer.Text(exercise.acceptedAnswers.first().surface))
                        walk(next.next(next.revision), depth + 1)
                    }
                }
            }
            walk(start, 0)
            val left = start.exit()
            assertSame(left, left.next(left.revision))
            val restarted = left.restart()
            assertFalse(restarted.exited)
            assertSame(restarted, restarted.next(start.revision))
        }
    }
    @Test fun cyclesMissingReferencesAndUnreachableNodesAreRejected() {
        val lesson = lessons().first()
        val entry = lesson.conversationGraph!!.entryNodeId
        assertFailsWith<IllegalArgumentException> { ConversationSession.start(lesson.copy(conversationGraph =
            ConversationGraph(entry, listOf(PromptNode(entry, lesson.dialogue.speakers.first().id, lesson.dialogue.turns.first().text, entry))))) }
        assertFailsWith<IllegalArgumentException> { ConversationSession.start(lesson.copy(conversationGraph =
            ConversationGraph(entry, listOf(TerminalNode(entry, "End"), TerminalNode("unreachable", "End"))))) }
        assertFailsWith<IllegalArgumentException> { ConversationSession.start(lesson.copy(conversationGraph =
            ConversationGraph(entry, listOf(PromptNode(entry, lesson.dialogue.speakers.first().id, lesson.dialogue.turns.first().text, "missing"))))) }
    }
}
