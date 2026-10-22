package com.github.nanaki_93.content

import com.github.nanaki_93.progress.CheckpointExerciseType
import com.github.nanaki_93.progress.CheckpointView
import com.github.nanaki_93.progress.CompactOutcome
import com.github.nanaki_93.progress.LessonProgress
import com.github.nanaki_93.progress.LessonStage
import com.github.nanaki_93.progress.PracticeCheckpoint
import com.github.nanaki_93.progress.SaveEnvelope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LearningRoadmapTest {
    private val text = JapaneseText("あ", "あ", gloss = "Hiragana a")
    private val wordText = JapaneseText("水", "みず", translation = "water")
    private val review = ReviewMetadata(
        ReviewStatus.REVIEWED, ReviewerType.AGENT, "2026-10-02", "note", "Original",
        RightsStatus.PUBLISHABLE, "Original",
    )
    private val choiceEx = ChoiceExercise(
        "ex-1", "Choose", listOf(ChoiceOption("opt-a", text = text), ChoiceOption("opt-b", label = "No")),
        "opt-a", "Explanation",
    )
    private val readingEx = ReadingExercise(
        "ex-2", "Read", wordText, AnswerRepresentation.KANA,
        listOf(KanaReadingAnswer(JapaneseText("みず", "みず", translation = "water"))), "Explanation",
    )
    private val seedSet = PracticeSet(1, 3, "practice-kana-a-i", "kana-foundations", "Seed romaji", "Seed", review = review, exercises = listOf(choiceEx))
    private val hiraganaSet = PracticeSet(1, 3, "practice-hiragana-1", "kana-foundations", "Hiragana 1", "Vowels", review = review, exercises = listOf(choiceEx))
    private val katakanaSet = PracticeSet(1, 3, "practice-katakana-1", "katakana-foundations", "Katakana 1", "Vowels", review = review, exercises = listOf(choiceEx))
    private val vocabSet = PracticeSet(1, 3, "practice-vocab-1", "foundational-vocabulary", "Vocab 1", "Words", review = review, exercises = listOf(readingEx))

    private val lifeLesson = Lesson(
        1, 3, "lesson-transport", "life-transport", "Transport", "At station", "Ask platform",
        AdvisoryDifficulty.BEGINNER, 8, emptyList(),
        Dialogue(listOf(Speaker("s1", "Staff", "Staff")), listOf(DialogueTurn("t1", "s1", wordText))),
        emptyList(), emptyList(), RolePlayObjective("Ask", listOf("Polite")), emptyList(), review,
        listOf(choiceEx), ConversationGraph("n1", listOf(TerminalNode("n1", "Done"))),
    )
    private val workLesson = lifeLesson.copy(
        id = "lesson-intro", topicId = "workplace-introductions", title = "Intro",
    )

    private val catalog = ContentCatalog(
        1, 3,
        listOf(
            Topic("kana-foundations", "Hiragana foundations", "Hiragana"),
            Topic("katakana-foundations", "Katakana foundations", "Katakana"),
            Topic("foundational-vocabulary", "Foundational vocabulary", "Vocab"),
            Topic("life-transport", "Transport", "Station"),
            Topic("workplace-introductions", "Workplace introductions", "Intro"),
        ),
        listOf(
            ContentEntry("practice-kana-a-i", DocumentKind.PRACTICE, "kana-foundations", "practice/seed.json"),
            ContentEntry("practice-hiragana-1", DocumentKind.PRACTICE, "kana-foundations", "practice/h1.json"),
            ContentEntry("practice-katakana-1", DocumentKind.PRACTICE, "katakana-foundations", "practice/k1.json"),
            ContentEntry("practice-vocab-1", DocumentKind.PRACTICE, "foundational-vocabulary", "practice/v1.json"),
            ContentEntry("lesson-transport", DocumentKind.LESSON, "life-transport", "lessons/transport.json"),
            ContentEntry("lesson-intro", DocumentKind.LESSON, "workplace-introductions", "lessons/intro.json"),
        ),
    )

    private val practiceSets = mapOf(
        seedSet.id to seedSet,
        hiraganaSet.id to hiraganaSet,
        katakanaSet.id to katakanaSet,
        vocabSet.id to vocabSet,
    )
    private val lessons = mapOf(lifeLesson.id to lifeLesson, workLesson.id to workLesson)

    @Test fun zeroProgressRecommendsFirstCoreHiraganaSetAndPopulatesFourPhases() {
        val snapshot = SaveEnvelope(savedAtEpochMs = 1, snapshotId = "s1", revision = 0)
        val roadmap = projectZeroToJapanRoadmap(catalog, practiceSets, lessons, snapshot)

        assertEquals(4, roadmap.phases.size)
        assertEquals(RoadmapPhaseId.SCRIPT_FOUNDATIONS, roadmap.phases[0].id)
        assertEquals(0, roadmap.phases[0].completedCount)
        assertEquals(2, roadmap.phases[0].totalCount)
        assertEquals("practice-hiragana-1", roadmap.phases[0].nextItemId)

        assertEquals(RoadmapPhaseId.VOCABULARY_GRAMMAR_BRIDGE, roadmap.phases[1].id)
        assertEquals(1, roadmap.phases[1].totalCount)

        assertEquals(RoadmapPhaseId.LIVING_IN_JAPAN, roadmap.phases[2].id)
        assertEquals(1, roadmap.phases[2].totalCount)
        assertEquals("lesson-transport", roadmap.phases[2].nextItemId)

        assertEquals(RoadmapPhaseId.WORKING_IN_JAPAN, roadmap.phases[3].id)
        assertEquals(1, roadmap.phases[3].totalCount)
        assertEquals("lesson-intro", roadmap.phases[3].nextItemId)

        val rec = assertNotNull(roadmap.recommendedPractice)
        assertEquals("practice-hiragana-1", rec.setId)
        assertFalse(rec.resumable)
    }

    @Test fun resumablePracticeTakesPriorityAndCompletedPracticeAdvancesToNextStage() {
        val completedHira = PracticeCheckpoint(
            setId = "practice-hiragana-1",
            contentVersion = 3,
            updatedAtEpochMs = 10,
            runToken = "r1",
            lastTransitionToken = "t1",
            exerciseIds = listOf("ex-1"),
            exerciseTypes = listOf(CheckpointExerciseType.CHOICE),
            outcomes = listOf(CompactOutcome.CORRECT),
            frontier = 1,
            view = CheckpointView.COMPLETE,
            lastCompletedAtEpochMs = 10,
        )
        val inProgressKata = PracticeCheckpoint(
            setId = "practice-katakana-1",
            contentVersion = 3,
            updatedAtEpochMs = 20,
            runToken = "r2",
            lastTransitionToken = "t2",
            exerciseIds = listOf("ex-1"),
            exerciseTypes = listOf(CheckpointExerciseType.CHOICE),
            outcomes = emptyList(),
            frontier = 0,
            view = CheckpointView.PROMPT,
        )
        val completedLife = LessonProgress(
            lessonId = "lesson-transport",
            contentVersion = 3,
            updatedAtEpochMs = 30,
            stage = LessonStage.SUMMARY,
            checkpointId = null,
            completedAtEpochMs = 30,
        )
        val snapshot = SaveEnvelope(
            savedAtEpochMs = 30,
            snapshotId = "s2",
            revision = 1,
            practiceProgress = listOf(completedHira, inProgressKata),
            lessonProgress = listOf(completedLife),
        )
        val roadmap = projectZeroToJapanRoadmap(catalog, practiceSets, lessons, snapshot)

        assertEquals(1, roadmap.phases[0].completedCount)
        assertEquals(1, roadmap.phases[0].inProgressCount)
        assertEquals("practice-katakana-1", roadmap.phases[0].nextItemId)
        assertTrue(roadmap.phases[0].nextItemResumable)

        assertEquals(1, roadmap.phases[2].completedCount)
        assertEquals(null, roadmap.phases[2].nextItemId)

        val rec = assertNotNull(roadmap.recommendedPractice)
        assertEquals("practice-katakana-1", rec.setId)
        assertTrue(rec.resumable)
    }

    @Test fun referenceDeskContainsCompleteGojuonRowsGrammarPillarsAndSurvivalPhrases() {
        assertTrue(ZeroBeginnerReference.kanaRows.size >= 12)
        val allHiragana = ZeroBeginnerReference.kanaRows.flatMap { it.signs }.map { it.hiragana }.toSet()
        for (sign in listOf("あ", "か", "さ", "た", "な", "は", "ま", "や", "ら", "わ", "を", "ん", "が", "ぱ")) {
            assertTrue(sign in allHiragana, "Missing kana reference sign $sign")
        }
        assertEquals(6, ZeroBeginnerReference.pronunciationRules.size)
        assertEquals(6, ZeroBeginnerReference.grammarFoundations.size)
        assertEquals(6, ZeroBeginnerReference.survivalPhrases.size)
    }
}
