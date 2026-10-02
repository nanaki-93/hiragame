package com.github.nanaki_93.content

import com.github.nanaki_93.progress.LessonProgress
import com.github.nanaki_93.progress.LessonStage
import com.github.nanaki_93.progress.SaveEnvelope
import com.github.nanaki_93.progress.SavePreferences
import com.github.nanaki_93.progress.PracticeCheckpoint
import com.github.nanaki_93.progress.CheckpointView
import com.github.nanaki_93.progress.CheckpointExerciseType
import com.github.nanaki_93.progress.CompactOutcome
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

    @Test fun savedPlaceWinsByUpdateTimeWithCanonicalTieBreakAndUnavailablePlaceSkipped() {
        val third = lesson.copy(id = "third", title = "Third title")
        val three = catalog.copy(entries = catalog.entries +
            ContentEntry("third", DocumentKind.LESSON, "work", "lessons/third.json"))
        val available = mapOf("first" to lesson, "second" to second, "third" to third)
        val saved = save(
            record("first").copy(updatedAtEpochMs = 20),
            record("second", checkpoint = "gone").copy(updatedAtEpochMs = 100),
            record("third").copy(updatedAtEpochMs = 20),
        )
        val view = projectLessonCatalog(three, available, saved)
        assertEquals("first", view.recommendation?.lessonId)
        assertTrue(view.recommendation!!.reason.contains("saved place"))
        assertFalse(view.recommendation!!.reason.contains("Resume"))
        assertFalse(view.recommendation!!.revisit)
        assertEquals(false, view.cards[1].status.checkpointAvailable)
        assertEquals("gone", saved.lessonProgress[1].checkpointId)
        assertEquals("third", projectLessonCatalog(three, available,
            saved.copy(lessonProgress = saved.lessonProgress.map {
                if (it.lessonId == "third") it.copy(updatedAtEpochMs = 21) else it
            })).recommendation?.lessonId)
    }

    @Test fun optionalBeginnerGuidanceDoesNotFilterOrPersistAndSupportIsExplicit() {
        val seed = lesson.copy(id = "lesson-confirm-meeting-time")
        val extended = catalog.copy(entries = catalog.entries + ContentEntry(
            seed.id, DocumentKind.LESSON, "work", "lessons/seed.json"))
        val resolved = lessons + (seed.id to seed)
        val noAids = save().copy(preferences = SavePreferences(
            showReadings = false, showTranslation = false, showRomaji = false))
        val default = projectLessonCatalog(extended, resolved, noAids)
        assertTrue(default.beginnerPathCards.isEmpty())
        assertEquals("first", default.recommendation?.lessonId)
        assertTrue(default.recommendation!!.reason.contains("catalog order"))
        val guided = projectLessonCatalog(extended, resolved, noAids,
            CatalogViewOptions(topicId = "other", beginnerPath = true))
        assertEquals(listOf("first"), guided.beginnerPathCards.map { it.lessonId })
        assertEquals(listOf("second"), guided.cards.map { it.lessonId })
        assertEquals(3, guided.topics.sumOf { it.cards.size })
        assertFalse(noAids.preferences.showReadings)
        assertFalse(noAids.preferences.showTranslation)
        assertFalse(noAids.preferences.showRomaji)
        listOf(
            SavePreferences(showReadings = true, showTranslation = false, showRomaji = false),
            SavePreferences(showReadings = false, showTranslation = true, showRomaji = false),
            SavePreferences(showReadings = false, showTranslation = false, showRomaji = true),
        ).forEach { preferences ->
            val suggestion = projectLessonCatalog(extended, resolved,
                noAids.copy(preferences = preferences)).recommendation!!
            assertEquals("first", suggestion.lessonId)
            assertTrue(suggestion.reason.contains("support is enabled"))
        }
    }

    @Test fun missingSavedCheckpointDoesNotWinOverBeginnerOrFallback() {
        val unavailable = save(record(checkpoint = "removed").copy(updatedAtEpochMs = 999))
        val guided = projectLessonCatalog(catalog, lessons, unavailable)
        assertEquals("first", guided.recommendation?.lessonId)
        assertTrue(guided.recommendation!!.reason.contains("support"))
        val unaided = projectLessonCatalog(catalog, lessons, unavailable.copy(preferences =
            SavePreferences(showReadings = false, showTranslation = false)))
        assertEquals("first", unaided.recommendation?.lessonId)
        assertTrue(unaided.recommendation!!.reason.contains("catalog order"))
    }

    @Test fun completedLessonsOfferRevisitWithoutUsingPracticeScores() {
        val practice = PracticeCheckpoint(
            "practice", 3, 100, "run", "transition", listOf("exercise"),
            listOf(CompactOutcome.CORRECT), 1, CheckpointView.COMPLETE,
            exerciseTypes = listOf(CheckpointExerciseType.CHOICE),
        )
        val scored = save().copy(practiceProgress = listOf(practice))
        assertEquals(projectLessonCatalog(catalog, lessons, save()).recommendation,
            projectLessonCatalog(catalog, lessons, scored).recommendation)
        val completed = scored.copy(lessonProgress = listOf(
            record(completed = 50), record("second", checkpoint = "gone", completed = 60)))
        val suggestion = projectLessonCatalog(catalog, lessons, completed).recommendation!!
        assertEquals("first", suggestion.lessonId)
        assertTrue(suggestion.revisit)
        assertTrue(suggestion.reason.contains("Revisit"))
        assertEquals(false, projectLessonCatalog(catalog, lessons, completed).cards[1].status.checkpointAvailable)
        assertNull(projectLessonCatalog(catalog.copy(entries = emptyList()), emptyMap(), completed).recommendation)
    }

    @Test fun invalidFilterDoesNotAlterCanonicalRecommendation() {
        val saved = save(record())
        val unfiltered = projectLessonCatalog(catalog, lessons, saved)
        val invalid = projectLessonCatalog(catalog, lessons, saved,
            CatalogViewOptions("unknown", beginnerPath = true))
        assertTrue(invalid.invalidTopicFilter)
        assertTrue(invalid.cards.isEmpty())
        assertEquals(unfiltered.recommendation, invalid.recommendation)
        assertEquals(listOf("first"), invalid.beginnerPathCards.map { it.lessonId })
    }

    @Test fun missingResolvedDocumentKeepsSavedRecordEvenWhenEntryExists() {
        val view = projectLessonCatalog(catalog, mapOf("second" to second), save(record()))
        assertEquals(listOf("second"), view.cards.map { it.lessonId })
        assertEquals("first", view.unavailableSavedLessons.single().record?.lessonId)
        assertFalse(view.unavailableSavedLessons.single().documentAvailable)
    }
}
