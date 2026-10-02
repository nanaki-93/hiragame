package com.github.nanaki_93.content

import com.github.nanaki_93.progress.LessonProgress
import com.github.nanaki_93.progress.LessonStage
import com.github.nanaki_93.progress.SaveEnvelope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LessonCatalogTest {
    private val text = JapaneseText("はい。", "はい。", translation = "Yes.")
    private val review = ReviewMetadata(
        ReviewStatus.REVIEWED, ReviewerType.AGENT, "2026-10-02", "note", "Original",
        RightsStatus.PUBLISHABLE, "Original",
    )
    private val production = ProductionExercise("production", "Speak", listOf(text), listOf("Polite"))
    private val exercise = ChoiceExercise(
        "exercise", "Choose", listOf(ChoiceOption("yes", label = "Yes"), ChoiceOption("no", label = "No")),
        "yes", "Yes",
    )
    private val lesson = Lesson(
        1, 3, "first", "work", "First title", "At work", "Introduce yourself",
        AdvisoryDifficulty.BEGINNER, 8, emptyList(),
        Dialogue(listOf(Speaker("speaker", "A", "Engineer")), listOf(DialogueTurn("turn", "speaker", text))),
        emptyList(), emptyList(), RolePlayObjective("Talk", listOf("Polite")), emptyList(), review,
        listOf(exercise, production),
        ConversationGraph("node", listOf(TerminalNode("node", "Done"))),
    )
    private val second = lesson.copy(
        id = "second", topicId = "other", title = "Second title", communicationGoal = "Confirm", durationMinutes = 12,
        difficulty = AdvisoryDifficulty.INTERMEDIATE, prerequisiteLessonIds = listOf("first"),
    )
    private val catalog = ContentCatalog(
        1, 3, listOf(Topic("other", "Other", "Other topic"), Topic("work", "Work", "Work topic"),
            Topic("unused", "Unused", "No lessons")),
        listOf(ContentEntry("first", DocumentKind.LESSON, "work", "lessons/first.json"),
            ContentEntry("practice", DocumentKind.PRACTICE, "work", "practice/one.json"),
            ContentEntry("second", DocumentKind.LESSON, "other", "lessons/second.json")),
    )
    private val lessons = mapOf("first" to lesson, "second" to second)
    private fun save(vararg records: LessonProgress) = SaveEnvelope(
        savedAtEpochMs = 1, snapshotId = "snapshot", revision = 0, lessonProgress = records.toList(),
    )
    private fun record(
        id: String = "first", stage: LessonStage = LessonStage.DIALOGUE,
        checkpoint: String? = "turn", completed: Long? = null,
    ) = LessonProgress(id, 2, 10, stage, checkpoint, completed)

    @Test fun canonicalOrderAndResolvedMetadataWithoutInventedProgress() {
        val snapshot = save()
        val view = projectLessonCatalog(catalog, lessons, snapshot)
        assertEquals(listOf("other", "work"), view.topics.map { it.topic.id })
        assertEquals(listOf("first", "second"), view.cards.map { it.lessonId })
        assertEquals(listOf("second"), view.topics.first().cards.map { it.lessonId })
        assertEquals("Introduce yourself", view.cards.first().communicationGoal)
        assertEquals(8, view.cards.first().durationMinutes)
        assertEquals(listOf("first"), view.cards.last().prerequisiteLessonIds)
        assertNull(view.cards.first().status.savedStage)
        assertNull(view.cards.first().status.checkpointAvailable)
        assertFalse(view.cards.first().status.isCompleted)
        assertTrue(view.unavailableSavedLessons.isEmpty())
        assertEquals(snapshot, save())
    }

    @Test fun stageSpecificItemsAndBoundaryWithoutVersionGateOrSaveMutation() {
        val cases = listOf(
            Triple(LessonStage.DIALOGUE, "turn", true),
            Triple(LessonStage.DIALOGUE, "exercise", false),
            Triple(LessonStage.UNDERSTANDING, "exercise", true),
            Triple(LessonStage.GUIDED_PRACTICE, "exercise", true),
            Triple(LessonStage.UNDERSTANDING, "turn", false),
            Triple(LessonStage.ROLE_PLAY, "production", true),
            Triple(LessonStage.ROLE_PLAY, "node", true),
            Triple(LessonStage.ROLE_PLAY, "exercise", false),
            Triple(LessonStage.SITUATION, "turn", false),
            Triple(LessonStage.SUMMARY, "production", false),
        )
        cases.forEach { (stage, id, expected) ->
            val saved = save(record(stage = stage, checkpoint = id))
            val status = projectLessonCatalog(catalog, lessons, saved).cards.first().status
            assertEquals(expected, status.checkpointAvailable, "$stage / $id")
            assertEquals(stage, status.savedStage)
            assertTrue(status.documentAvailable)
            assertFalse(status.isCompleted)
            assertEquals(saved.lessonProgress.single().checkpointId, id)
        }
        LessonStage.entries.forEach { stage ->
            assertEquals(true, projectLessonCatalog(catalog, lessons, save(record(stage = stage, checkpoint = null)))
                .cards.first().status.checkpointAvailable, "$stage boundary")
        }
    }

    @Test fun completionIsIndependentOfCheckpointAndMissingRecordsRemainVisible() {
        val existing = record(stage = LessonStage.SUMMARY, checkpoint = "old-item", completed = 42)
        val missing = record(id = "removed", completed = 55)
        val saved = save(existing, missing)
        val view = projectLessonCatalog(catalog, lessons, saved)
        val status = view.cards.first().status
        assertTrue(status.isCompleted)
        assertEquals(42, status.completedAtEpochMs)
        assertEquals(false, status.checkpointAvailable)
        assertEquals(listOf(missing), view.unavailableSavedLessons.map { it.record })
        assertEquals(false, view.unavailableSavedLessons.single().documentAvailable)
        assertEquals(false, view.unavailableSavedLessons.single().checkpointAvailable)
        assertTrue(view.unavailableSavedLessons.single().isCompleted)
        assertEquals(listOf(existing, missing), saved.lessonProgress)
        // Practice and review results are not lesson completion evidence.
        assertFalse(projectLessonCatalog(catalog, lessons, save()).cards.first().status.isCompleted)
    }

    @Test fun filteringIsClearableAndDoesNotChangeTheSaveOrOtherTopics() {
        val saved = save(record())
        val filtered = projectLessonCatalog(catalog, lessons, saved, CatalogViewOptions("other", beginnerPath = true))
        assertEquals(listOf("second"), filtered.cards.map { it.lessonId })
        assertEquals(2, filtered.topics.size)
        assertTrue(filtered.beginnerPath)
        assertFalse(filtered.invalidTopicFilter)
        val invalid = projectLessonCatalog(catalog, lessons, saved, CatalogViewOptions("not-a-topic"))
        assertTrue(invalid.invalidTopicFilter)
        assertEquals("not-a-topic", invalid.selectedTopicId)
        assertTrue(invalid.cards.isEmpty())
        assertEquals(listOf("first", "second"), projectLessonCatalog(catalog, lessons, saved).cards.map { it.lessonId })
        assertEquals(listOf(record()), saved.lessonProgress)
    }

    @Test fun missingResolvedDocumentKeepsSavedRecordEvenWhenEntryExists() {
        val view = projectLessonCatalog(catalog, mapOf("second" to second), save(record()))
        assertEquals(listOf("second"), view.cards.map { it.lessonId })
        assertEquals("first", view.unavailableSavedLessons.single().record?.lessonId)
        assertFalse(view.unavailableSavedLessons.single().documentAvailable)
    }
}
