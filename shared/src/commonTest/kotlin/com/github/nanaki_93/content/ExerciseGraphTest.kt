package com.github.nanaki_93.content

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExerciseGraphTest {
    private val json = Json { encodeDefaults = true }
    private val text = JapaneseText("確認します。", "かくにんします。", translation = "I will confirm.")
    private val fill = JapaneseText("確認", "かくにん", translation = "confirm")
    private val kanaAnswer = KanaReadingAnswer(JapaneseText("かくにんします。", "かくにんします。", translation = "I will confirm."))
    private val choice = ChoiceExercise(
        "ex_choice", "Select the meaning", listOf(
            ChoiceOption("opt_yes", label = "I will confirm."),
            ChoiceOption("opt_no", text = JapaneseText("いいえ", "いいえ", translation = "No")),
        ), "opt_yes", "This is a polite confirmation.",
    )
    private val reading = ReadingExercise(
        "ex_reading", "Write the reading", text, AnswerRepresentation.KANA,
        listOf(kanaAnswer), "The kanji 確認 reads かくにん.",
    )
    private val completion = CompletionExercise(
        "ex_completion", "Fill the blank", "{blank}します。", listOf(fill), text,
        "Use 確認 to confirm a detail.",
    )
    private val production = ProductionExercise(
        "ex_production", "Confirm the schedule politely", listOf(text),
        listOf("Use a polite ending", "Make the confirmation explicit"),
    )

    @Test fun exerciseDiscriminatorsAndDocumentRoundTrips() {
        val exercises: List<Exercise> = listOf(choice, reading, completion, production)
        for ((exercise, type) in exercises.zip(listOf("choice", "reading", "completion", "production"))) {
            val wire = json.encodeToString<Exercise>(exercise)
            assertTrue(wire.contains("\"type\":\"$type\""), wire)
            assertEquals(exercise, json.decodeFromString<Exercise>(wire))
        }
        val review = ReviewMetadata(ReviewStatus.REVIEWED, ReviewerType.AGENT, "2026-10-01", "seed.md", "Original", RightsStatus.PUBLISHABLE, "Original")
        val practice = PracticeSet(1, 1, "practice_1", "topic_1", "Reading", "Practice readings", review = review, exercises = exercises)
        assertEquals(practice, json.decodeFromString<PracticeSet>(json.encodeToString(practice)))
        val lesson = Lesson(
            1, 1, "lesson_1", "topic_1", "Confirm", "Office", "Confirm details",
            AdvisoryDifficulty.BEGINNER, 5, emptyList(),
            Dialogue(listOf(Speaker("colleague", "Colleague", "Coworker")), listOf(DialogueTurn("turn_1", "colleague", text))),
            emptyList(), emptyList(), RolePlayObjective("Confirm details", listOf("Be polite")),
            emptyList(), review, exercises, graph(),
        )
        assertEquals(lesson, json.decodeFromString<Lesson>(json.encodeToString(lesson)))
    }

    private fun graph() = ConversationGraph("start", listOf(
        PromptNode("start", "colleague", text, "pick"),
        ChoiceInteractionNode("pick", choice.id, listOf(
            ChoiceTransition("opt_yes", "Good confirmation.", "finish"),
            ChoiceTransition("opt_no", "Try clarifying first.", "fill"),
        )),
        CompletionInteractionNode("fill", completion.id, "Now confirm it.", "finish"),
        TerminalNode("finish", "Conversation complete."),
    ))

    @Test fun graphNodeDiscriminatorsAndTerminalPath() {
        val graph = graph()
        val wire = json.encodeToString(graph)
        for (type in listOf("prompt", "choiceInteraction", "completionInteraction", "terminal")) {
            assertTrue(wire.contains("\"type\":\"$type\""), wire)
        }
        assertEquals(graph, json.decodeFromString<ConversationGraph>(wire))
        assertEquals("finish", (graph.nodes[1] as ChoiceInteractionNode).transitions.first().nextNodeId)
        assertEquals("finish", (graph.nodes[2] as CompletionInteractionNode).nextNodeId)
        assertFalse(json.encodeToString<GraphNode>(graph.nodes.last()).contains("nextNodeId"))
    }

    @Test fun incompatibleFieldsAndUnknownTypesFailClosed() {
        val productionWire = json.encodeToString<Exercise>(production)
        assertFalse(productionWire.contains("acceptedAnswers"))
        assertFalse(productionWire.contains("correctOptionId"))
        for (extra in listOf("\"acceptedAnswers\":[\"answer\"]", "\"correctOptionId\":\"opt_yes\"", "\"explanation\":\"graded\"")) {
            assertFailsWith<Exception> { json.decodeFromString<Exercise>(productionWire.dropLast(1) + ",$extra}") }
        }
        val choiceWire = json.encodeToString<Exercise>(choice)
        assertFailsWith<Exception> { json.decodeFromString<Exercise>(choiceWire.dropLast(1) + ",\"criteria\":[\"extra\"]}") }
        val readingWire = json.encodeToString<Exercise>(reading)
        assertFailsWith<Exception> { json.decodeFromString<Exercise>(readingWire.dropLast(1) + ",\"template\":\"{blank}\"}") }
        val authoredAnswerWire = json.encodeToString<ReadingAnswer>(kanaAnswer)
        assertTrue(readingWire.contains(authoredAnswerWire), readingWire)
        assertFailsWith<Exception> { json.decodeFromString<Exercise>(readingWire.replace(authoredAnswerWire, "\"かくにんします。\"")) }
        assertFailsWith<Exception> { json.decodeFromString<Exercise>(readingWire.replace(authoredAnswerWire, "{\"type\":\"romaji\",\"text\":\"kakunin\"}")) }
        val completionWire = json.encodeToString<Exercise>(completion)
        assertFailsWith<Exception> { json.decodeFromString<Exercise>(completionWire.dropLast(1) + ",\"options\":[]}") }
        assertFailsWith<Exception> { json.decodeFromString<Exercise>(choiceWire.replace("\"type\":\"choice\"", "\"type\":\"unknown\"")) }
        val nodeWire = json.encodeToString<GraphNode>(TerminalNode("end", "Done"))
        assertFailsWith<Exception> { json.decodeFromString<GraphNode>(nodeWire.dropLast(1) + ",\"nextNodeId\":\"end\"}") }
        assertFailsWith<Exception> { json.decodeFromString<GraphNode>(nodeWire.replace("\"terminal\"", "\"unknown\"")) }
    }

    @Test fun localExerciseAndGraphInvariants() {
        assertFailsWith<IllegalArgumentException> { choice.copy(options = listOf(choice.options[0], choice.options[0])) }
        assertFailsWith<IllegalArgumentException> { choice.copy(correctOptionId = "missing") }
        assertFailsWith<IllegalArgumentException> { ChoiceOption("bad.id", label = "No") }
        assertFailsWith<IllegalArgumentException> { ChoiceOption("opt", label = "No", text = text) }
        assertFailsWith<IllegalArgumentException> { reading.copy(acceptedAnswers = listOf(kanaAnswer, kanaAnswer)) }
        assertFailsWith<IllegalArgumentException> { reading.copy(acceptedAnswers = listOf(kanaAnswer, KanaReadingAnswer(kanaAnswer.text.copy(translation = "Same sound")))) }
        assertFailsWith<IllegalArgumentException> { reading.copy(acceptedAnswers = emptyList()) }
        assertFailsWith<IllegalArgumentException> { reading.copy(acceptedAnswers = listOf(RomajiReadingAnswer("kakunin"))) }
        assertFailsWith<IllegalArgumentException> { RomajiReadingAnswer(" ") }
        val romajiReading = reading.copy(answerRepresentation = AnswerRepresentation.ROMAJI, acceptedAnswers = listOf(RomajiReadingAnswer("kakunin shimasu")))
        assertEquals(romajiReading, json.decodeFromString<Exercise>(json.encodeToString<Exercise>(romajiReading)))
        assertFailsWith<IllegalArgumentException> { romajiReading.copy(acceptedAnswers = listOf(RomajiReadingAnswer("same"), RomajiReadingAnswer("same"))) }
        assertFailsWith<IllegalArgumentException> { completion.copy(template = "No blank") }
        assertFailsWith<IllegalArgumentException> { completion.copy(acceptedAnswers = listOf(fill, fill)) }
        val secondFill = JapaneseText("連絡", "れんらく", translation = "contact")
        val secondExample = JapaneseText("連絡します。", "れんらくします。", translation = "I will contact.")
        val multiple = completion.copy(acceptedAnswers = listOf(fill, secondFill), expectedCompletedExample = secondExample)
        assertEquals(multiple, json.decodeFromString<Exercise>(json.encodeToString<Exercise>(multiple)))
        assertFailsWith<IllegalArgumentException> {
            multiple.copy(expectedCompletedExample = secondExample.copy(surface = "報告します。"))
        }
        assertFailsWith<IllegalArgumentException> { production.copy(criteria = emptyList()) }
        assertFailsWith<IllegalArgumentException> { graph().copy(entryNodeId = "missing") }
        assertFailsWith<IllegalArgumentException> { graph().copy(nodes = listOf(TerminalNode("a", "End"), TerminalNode("a", "End"))) }
        assertFailsWith<IllegalArgumentException> { ChoiceInteractionNode("pick", "ex", listOf(ChoiceTransition("a", "Yes", "end"), ChoiceTransition("a", "No", "end"))) }
    }
}
