package com.github.nanaki_93.content

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/** These are authored prompts and answers, not evaluation or normalization rules. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class Exercise {
    abstract val id: String
}

/** Either a translated/English label or authored Japanese text, never both. */
@Serializable
@SerialName("choiceOption")
data class ChoiceOption(
    @SerialName("id") val id: String,
    @SerialName("label") val label: String? = null,
    @SerialName("text") val text: JapaneseText? = null,
) {
    init {
        requireId(id)
        require((label != null) != (text != null)) { "Choice option requires exactly one label or Japanese text" }
        label?.let { requireText(it, "choice label") }
    }
}

@Serializable
@SerialName("choice")
data class ChoiceExercise(
    @SerialName("id") override val id: String,
    @SerialName("prompt") val prompt: String,
    @SerialName("options") val options: List<ChoiceOption>,
    @SerialName("correctOptionId") val correctOptionId: String,
    @SerialName("explanation") val explanation: String,
) : Exercise() {
    init {
        requireId(id)
        requireText(prompt, "choice prompt")
        require(options.size >= 2) { "Choice needs at least two options" }
        requireUniqueIds(options.map { it.id }, "choice options")
        requireId(correctOptionId)
        require(options.any { it.id == correctOptionId }) { "Correct option must exist" }
        requireText(explanation, "choice explanation")
    }
}

@Serializable
enum class AnswerRepresentation {
    @SerialName("kana") KANA,
    @SerialName("romaji") ROMAJI,
}

/** Each accepted reading carries its representation; kana must retain authored reading and gloss/translation. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class ReadingAnswer

@Serializable
@SerialName("kana")
data class KanaReadingAnswer(
    @SerialName("text") val text: JapaneseText,
) : ReadingAnswer()

@Serializable
@SerialName("romaji")
data class RomajiReadingAnswer(
    @SerialName("text") val text: String,
) : ReadingAnswer() {
    init {
        requireText(text, "romaji answer")
    }
}

/** Accepted readings are explicitly authored; no normalization or inferred variants. */
@Serializable
@SerialName("reading")
data class ReadingExercise(
    @SerialName("id") override val id: String,
    @SerialName("prompt") val prompt: String,
    @SerialName("stimulus") val stimulus: JapaneseText,
    @SerialName("answerRepresentation") val answerRepresentation: AnswerRepresentation,
    @SerialName("acceptedAnswers") val acceptedAnswers: List<ReadingAnswer>,
    @SerialName("explanation") val explanation: String,
) : Exercise() {
    init {
        requireId(id)
        requireText(prompt, "reading prompt")
        require(acceptedAnswers.isNotEmpty()) { "Reading needs accepted answers" }
        require(acceptedAnswers.all {
            when (answerRepresentation) {
                AnswerRepresentation.KANA -> it is KanaReadingAnswer
                AnswerRepresentation.ROMAJI -> it is RomajiReadingAnswer
            }
        }) { "Accepted reading representation must match answerRepresentation" }
        requireUniqueIds(acceptedAnswers.map {
            when (it) {
                is KanaReadingAnswer -> it.text.surface
                is RomajiReadingAnswer -> it.text
            }
        }, "accepted readings")
        requireText(explanation, "reading explanation")
    }
}

/** The template contains exactly one literal {blank}; fills are authored Japanese text. */
@Serializable
@SerialName("completion")
data class CompletionExercise(
    @SerialName("id") override val id: String,
    @SerialName("prompt") val prompt: String,
    @SerialName("template") val template: String,
    @SerialName("acceptedAnswers") val acceptedAnswers: List<JapaneseText>,
    @SerialName("expectedCompletedExample") val expectedCompletedExample: JapaneseText,
    @SerialName("explanation") val explanation: String,
) : Exercise() {
    init {
        requireId(id)
        requireText(prompt, "completion prompt")
        require(template.split("{blank}").size == 2) { "Completion template needs exactly one {blank}" }
        require(acceptedAnswers.isNotEmpty()) { "Completion needs accepted answers" }
        requireUniqueIds(acceptedAnswers.map { it.surface }, "completion answer surfaces")
        requireText(explanation, "completion explanation")
    }
}

/** No correctAnswer or acceptedAnswers field: learners self-assess production. */
@Serializable
@SerialName("production")
data class ProductionExercise(
    @SerialName("id") override val id: String,
    @SerialName("prompt") val prompt: String,
    @SerialName("exampleResponses") val exampleResponses: List<JapaneseText>,
    @SerialName("criteria") val criteria: List<String>,
) : Exercise() {
    init {
        requireId(id)
        requireText(prompt, "production prompt")
        require(exampleResponses.isNotEmpty()) { "Production needs example responses" }
        require(criteria.isNotEmpty()) { "Production needs self-assessment criteria" }
        criteria.forEach { requireText(it, "production criterion") }
    }
}

/** Graph edges and exercise references are local to the owning lesson. */
@Serializable
@SerialName("conversationGraph")
data class ConversationGraph(
    @SerialName("entryNodeId") val entryNodeId: String,
    @SerialName("nodes") val nodes: List<GraphNode>,
) {
    init {
        requireId(entryNodeId)
        require(nodes.isNotEmpty()) { "Graph needs nodes" }
        requireUniqueIds(nodes.map { it.id }, "graph nodes")
        require(nodes.any { it.id == entryNodeId }) { "Graph entry must exist" }
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class GraphNode {
    abstract val id: String
}

@Serializable
@SerialName("prompt")
data class PromptNode(
    @SerialName("id") override val id: String,
    @SerialName("speakerId") val speakerId: String,
    @SerialName("text") val text: JapaneseText,
    @SerialName("nextNodeId") val nextNodeId: String,
) : GraphNode() {
    init {
        requireId(id)
        requireId(speakerId)
        requireId(nextNodeId)
    }
}

@Serializable
@SerialName("choiceTransition")
data class ChoiceTransition(
    @SerialName("optionId") val optionId: String,
    @SerialName("feedback") val feedback: String,
    @SerialName("nextNodeId") val nextNodeId: String,
) {
    init {
        requireId(optionId)
        requireText(feedback, "choice feedback")
        requireId(nextNodeId)
    }
}

@Serializable
@SerialName("choiceInteraction")
data class ChoiceInteractionNode(
    @SerialName("id") override val id: String,
    @SerialName("exerciseId") val exerciseId: String,
    @SerialName("transitions") val transitions: List<ChoiceTransition>,
) : GraphNode() {
    init {
        requireId(id)
        requireId(exerciseId)
        require(transitions.isNotEmpty()) { "Choice interaction needs transitions" }
        requireUniqueIds(transitions.map { it.optionId }, "choice transitions")
    }
}

@Serializable
@SerialName("completionInteraction")
data class CompletionInteractionNode(
    @SerialName("id") override val id: String,
    @SerialName("exerciseId") val exerciseId: String,
    @SerialName("feedback") val feedback: String,
    @SerialName("nextNodeId") val nextNodeId: String,
) : GraphNode() {
    init {
        requireId(id)
        requireId(exerciseId)
        requireText(feedback, "completion feedback")
        requireId(nextNodeId)
    }
}

@Serializable
@SerialName("terminal")
data class TerminalNode(
    @SerialName("id") override val id: String,
    @SerialName("message") val message: String,
) : GraphNode() {
    init {
        requireId(id)
        requireText(message, "terminal message")
    }
}
