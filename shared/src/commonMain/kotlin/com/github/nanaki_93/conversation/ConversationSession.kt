package com.github.nanaki_93.conversation

import com.github.nanaki_93.content.*
import com.github.nanaki_93.practice.*

/** Responses and branch history are transient. No transcript is serialized into learner progress. */
@ConsistentCopyVisibility
data class ConversationSession private constructor(
    val lesson: Lesson,
    val nodeId: String,
    val revision: Int = 0,
    val feedback: String? = null,
    val pendingNodeId: String? = null,
    val exited: Boolean = false,
) {
    val node: GraphNode get() = lesson.conversationGraph!!.nodes.first { it.id == nodeId }
    val complete: Boolean get() = node is TerminalNode
    val exercise: Exercise? get() = when (val current = node) {
        is ChoiceInteractionNode -> lesson.exercises.first { it.id == current.exerciseId }
        is CompletionInteractionNode -> lesson.exercises.first { it.id == current.exerciseId }
        else -> null
    }

    fun next(expectedRevision: Int): ConversationSession {
        if (expectedRevision != revision || exited) return this
        val target = pendingNodeId ?: (node as? PromptNode)?.nextNodeId ?: return this
        return copy(nodeId = target, revision = revision + 1, feedback = null, pendingNodeId = null)
    }

    fun answer(expectedRevision: Int, answer: PracticeAnswer): ConversationSession {
        if (expectedRevision != revision || exited || pendingNodeId != null) return this
        return when (val current = node) {
            is ChoiceInteractionNode -> {
                val option = (answer as? PracticeAnswer.Choice)?.optionId ?: return this
                val branch = current.transitions.firstOrNull { it.optionId == option } ?: return this
                copy(revision = revision + 1, feedback = branch.feedback, pendingNodeId = branch.nextNodeId)
            }
            is CompletionInteractionNode -> {
                val task = exercise as CompletionExercise
                when (val result = evaluate(task, answer)) {
                    is EvaluationResult.Objective -> if (result.correct)
                        copy(revision = revision + 1, feedback = current.feedback, pendingNodeId = current.nextNodeId)
                    else copy(revision = revision + 1, feedback = "Try again. ${task.explanation}")
                    else -> this
                }
            }
            else -> this
        }
    }

    fun reveal(expectedRevision: Int): ConversationSession {
        if (expectedRevision != revision || exited || pendingNodeId != null) return this
        val current = node as? CompletionInteractionNode ?: return this
        return copy(revision = revision + 1, feedback = "Example revealed; this is not a correct attempt. ${current.feedback}",
            pendingNodeId = current.nextNodeId)
    }
    fun restart(): ConversationSession = start(lesson).copy(revision = revision + 1)
    fun exit(): ConversationSession = copy(exited = true, revision = revision + 1)

    companion object {
        fun start(lesson: Lesson): ConversationSession {
            val graph = requireNotNull(lesson.conversationGraph) { "No authored conversation" }
            val nodes = graph.nodes.associateBy { it.id }
            require(nodes.size == graph.nodes.size)
            val active = mutableSetOf<String>()
            val visited = mutableSetOf<String>()
            fun visit(id: String) {
                require(id !in active) { "Conversation cycle" }
                if (id in visited) return
                val node = requireNotNull(nodes[id]) { "Missing conversation node" }
                active.add(id)
                val targets = when (node) {
                    is PromptNode -> {
                        require(lesson.dialogue.speakers.any { it.id == node.speakerId })
                        listOf(node.nextNodeId)
                    }
                    is ChoiceInteractionNode -> {
                        val exercise = lesson.exercises.firstOrNull { it.id == node.exerciseId } as? ChoiceExercise
                        require(exercise != null && exercise.options.map { it.id }.toSet() == node.transitions.map { it.optionId }.toSet())
                        node.transitions.map { it.nextNodeId }
                    }
                    is CompletionInteractionNode -> {
                        require(lesson.exercises.any { it.id == node.exerciseId && it is CompletionExercise })
                        listOf(node.nextNodeId)
                    }
                    is TerminalNode -> emptyList()
                }
                targets.forEach(::visit)
                active.remove(id); visited.add(id)
            }
            visit(graph.entryNodeId)
            require(visited.size == nodes.size) { "Unreachable conversation node" }
            return ConversationSession(lesson, graph.entryNodeId)
        }
    }
}
