package com.github.nanaki_93.lesson

import com.github.nanaki_93.content.AdvisoryDifficulty
import com.github.nanaki_93.content.ChoiceExercise
import com.github.nanaki_93.content.ChoiceOption
import com.github.nanaki_93.content.CompletionExercise
import com.github.nanaki_93.content.ConversationGraph
import com.github.nanaki_93.content.Dialogue
import com.github.nanaki_93.content.DialogueTurn
import com.github.nanaki_93.content.JapaneseText
import com.github.nanaki_93.content.Lesson
import com.github.nanaki_93.content.ProductionExercise
import com.github.nanaki_93.content.ReadingExercise
import com.github.nanaki_93.content.ReviewMetadata
import com.github.nanaki_93.content.ReviewStatus
import com.github.nanaki_93.content.ReviewerType
import com.github.nanaki_93.content.RightsStatus
import com.github.nanaki_93.content.RolePlayObjective
import com.github.nanaki_93.content.Speaker
import com.github.nanaki_93.content.TerminalNode
import com.github.nanaki_93.content.AnswerRepresentation
import com.github.nanaki_93.content.KanaReadingAnswer
import com.github.nanaki_93.progress.LessonProgress
import com.github.nanaki_93.progress.LessonStage
import com.github.nanaki_93.progress.SaveEnvelope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class LessonCheckpointTest {
    private val text = JapaneseText("はい。", "はい。", translation = "Yes.")
    private val lesson = Lesson(
        1, 3, "lesson-one", "work", "Title", "Situation", "Goal",
        AdvisoryDifficulty.BEGINNER, 5, emptyList(),
        Dialogue(listOf(Speaker("speaker", "A", "Colleague")), listOf(DialogueTurn("turn", "speaker", text))),
        emptyList(), emptyList(), RolePlayObjective("Respond", listOf("Be polite")), emptyList(),
        ReviewMetadata(ReviewStatus.REVIEWED, ReviewerType.AGENT, "2026-10-02", "note",
            "Original", RightsStatus.PUBLISHABLE, "Original"),
        listOf(
            ChoiceExercise("choice", "Choose", listOf(ChoiceOption("yes", label = "Yes"), ChoiceOption("no", label = "No")), "yes", "Why"),
            ReadingExercise("reading", "Read", text, AnswerRepresentation.KANA,
                listOf(KanaReadingAnswer(text)), "Why"),
            CompletionExercise("fill", "Fill", "{blank}", listOf(text), text, "Why"),
            ProductionExercise("produce", "Respond", listOf(text), listOf("Polite")),
        ),
        ConversationGraph("node", listOf(TerminalNode("node", "Done"))),
    )
    private fun record(stage: LessonStage, id: String? = null) = LessonProgress(
        "lesson-one", 2, 10, stage, id, 8,
    )

    @Test fun noRecordAndAllStageEntriesAreAvailableIncludingEmptyStages() {
        assertEquals(LessonCheckpointResolution.NoRecord, resolveLessonCheckpoint(lesson, null))
        val empty = lesson.copy(dialogue = Dialogue(emptyList(), emptyList()), exercises = emptyList())
        LessonStage.entries.forEach { stage ->
            val saved = record(stage)
            val result = assertIs<LessonCheckpointResolution.Available>(resolveLessonCheckpoint(empty, saved))
            assertSame(saved, result.record)
            assertEquals(stage, result.stage.stage)
            assertNull(result.item)
        }
    }

    @Test fun sameStageStableIdsResumeDespiteVersionChangeAndDoNotChangeSave() {
        val cases = listOf(
            LessonStage.DIALOGUE to "turn", LessonStage.UNDERSTANDING to "choice",
            LessonStage.GUIDED_PRACTICE to "reading", LessonStage.GUIDED_PRACTICE to "fill",
            LessonStage.ROLE_PLAY to "produce",
        )
        cases.forEach { (stage, id) ->
            val saved = record(stage, id)
            val envelope = SaveEnvelope(savedAtEpochMs = 11, snapshotId = "snapshot", revision = 1,
                lessonProgress = listOf(saved))
            val before = envelope.copy(lessonProgress = envelope.lessonProgress.toList())
            val result = assertIs<LessonCheckpointResolution.Available>(
                resolveLessonCheckpoint(lesson, envelope.lessonProgress.single()))
            assertSame(saved, result.record)
            assertEquals(id, result.item?.checkpointId)
            assertEquals(stage, result.stage.stage)
            assertEquals(before, envelope)
            assertEquals(2, saved.contentVersion) // content is version 3
        }
    }

    @Test fun removedMovedGraphAndDifferentLessonAreIncompatibleWithoutRepair() {
        val cases = listOf(
            Triple(record(LessonStage.UNDERSTANDING, "missing"), lesson, LessonCheckpointMismatch.REMOVED_ITEM),
            Triple(record(LessonStage.GUIDED_PRACTICE, "choice"), lesson,
                LessonCheckpointMismatch.MOVED_TO_ANOTHER_STAGE),
            Triple(record(LessonStage.ROLE_PLAY, "node"), lesson,
                LessonCheckpointMismatch.UNSUPPORTED_GRAPH_NODE),
            Triple(record(LessonStage.SITUATION, "turn"), lesson,
                LessonCheckpointMismatch.MOVED_TO_ANOTHER_STAGE),
            Triple(record(LessonStage.SUMMARY, "produce"), lesson,
                LessonCheckpointMismatch.MOVED_TO_ANOTHER_STAGE),
            Triple(record(LessonStage.DIALOGUE, "turn").copy(lessonId = "other"), lesson,
                LessonCheckpointMismatch.DIFFERENT_LESSON),
        )
        cases.forEach { (saved, document, expected) ->
            val result = assertIs<LessonCheckpointResolution.Incompatible>(
                resolveLessonCheckpoint(document, saved))
            assertSame(saved, result.record)
            assertEquals(expected, result.reason)
        }
        val removed = lesson.copy(exercises = lesson.exercises.filterNot { it.id == "choice" }, contentVersion = 4)
        assertEquals(LessonCheckpointMismatch.REMOVED_ITEM,
            assertIs<LessonCheckpointResolution.Incompatible>(
                resolveLessonCheckpoint(removed, record(LessonStage.UNDERSTANDING, "choice"))).reason)
        val moved = lesson.copy(exercises = lesson.exercises.filterNot { it.id == "choice" } +
            ReadingExercise("choice", "Read", text, AnswerRepresentation.KANA,
                listOf(KanaReadingAnswer(text)), "Why"), contentVersion = 4)
        assertEquals(LessonCheckpointMismatch.MOVED_TO_ANOTHER_STAGE,
            assertIs<LessonCheckpointResolution.Incompatible>(
                resolveLessonCheckpoint(moved, record(LessonStage.UNDERSTANDING, "choice"))).reason)
        // A graph node is not a role-play item, even in an objective-only role-play stage.
        val objectiveOnly = lesson.copy(exercises = lesson.exercises.filterNot { it is ProductionExercise })
        assertEquals(LessonCheckpointMismatch.UNSUPPORTED_GRAPH_NODE,
            assertIs<LessonCheckpointResolution.Incompatible>(
                resolveLessonCheckpoint(objectiveOnly, record(LessonStage.ROLE_PLAY, "node"))).reason)
    }
}
