package com.github.nanaki_93.practice

import com.github.nanaki_93.content.AnswerRepresentation
import com.github.nanaki_93.content.ChoiceExercise
import com.github.nanaki_93.content.ChoiceOption
import com.github.nanaki_93.content.CompletionExercise
import com.github.nanaki_93.content.JapaneseText
import com.github.nanaki_93.content.KanaReadingAnswer
import com.github.nanaki_93.content.ProductionExercise
import com.github.nanaki_93.content.ReadingExercise
import com.github.nanaki_93.content.RomajiReadingAnswer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PracticeEvaluationTest {
    private val sign = JapaneseText("い", "い", gloss = "The /i/ sign")
    private val phrase = JapaneseText("確認します。", "かくにんします。", translation = "I will confirm.")
    private val fill = JapaneseText("確認", "かくにん", translation = "confirm")
    private val alternative = JapaneseText("連絡", "れんらく", translation = "contact")
    private val choice = ChoiceExercise(
        "choose", "Choose the sign", listOf(
            ChoiceOption("correct", label = "The /i/ sound"), ChoiceOption("wrong", text = sign),
        ), "correct", "This option represents /i/.",
    )
    private val kana = ReadingExercise(
        "read_kana", "Write the reading", phrase, AnswerRepresentation.KANA,
        listOf(
            KanaReadingAnswer(JapaneseText("かくにんします。", "かくにんします。", translation = "I will confirm.")),
            KanaReadingAnswer(JapaneseText("かくにんする。", "かくにんする。", translation = "To confirm.")),
        ), "The first two kanji read かくにん.",
    )
    private val romaji = ReadingExercise(
        "read_romaji", "Write romaji", sign, AnswerRepresentation.ROMAJI,
        listOf(RomajiReadingAnswer("i"), RomajiReadingAnswer("ii")), "This sign reads i.",
    )
    private val completion = CompletionExercise(
        "fill", "Fill the blank", "{blank}します。", listOf(fill, alternative), phrase,
        "Use the fill, not the full sentence.",
    )
    private val production = ProductionExercise(
        "produce", "Write a polite reply", listOf(phrase), listOf("Use a polite ending", "Confirm the time"),
    )

    private fun graded(exercise: com.github.nanaki_93.content.Exercise, answer: PracticeAnswer, correct: Boolean): AuthoredFeedback {
        val result = assertIs<EvaluationResult.Objective>(evaluate(exercise, answer))
        assertEquals(correct, result.correct)
        return result.feedback
    }

    @Test fun choiceComparesIdsAndPreservesAuthoredFeedbackEvenWhenIncorrect() {
        val expected = AuthoredFeedback.Choice(choice.options[0], choice.explanation)
        assertEquals(expected, graded(choice, PracticeAnswer.Choice("correct"), true))
        assertEquals(expected, graded(choice, PracticeAnswer.Choice("wrong"), false))
        // A label is not a choice ID, and a label-only option has no invented Japanese reading.
        assertEquals(EvaluationResult.Invalid(InvalidReason.UNKNOWN_CHOICE), evaluate(choice, PracticeAnswer.Choice("The /i/ sound")))
        assertEquals(null, (authoredFeedback(choice) as AuthoredFeedback.Choice).correctOption.text)
        val japaneseCorrect = choice.copy(correctOptionId = "wrong")
        assertEquals(sign, (graded(japaneseCorrect, PracticeAnswer.Choice("correct"), false) as AuthoredFeedback.Choice).correctOption.text)
    }

    @Test fun readingUsesDeclaredRepresentationAndRetainsAuthoredMeanings() {
        assertTrue(graded(kana, PracticeAnswer.Text(" かくにんする。 \n"), true) is AuthoredFeedback.Reading)
        assertEquals(false, (evaluate(kana, PracticeAnswer.Text("確認します。")) as EvaluationResult.Objective).correct)
        for (input in listOf("かくにんします", "カクニンします。", "かくにんしまーす。")) {
            graded(kana, PracticeAnswer.Text(input), false)
        }
        val feedback = graded(kana, PracticeAnswer.Text("かくにんします。"), true) as AuthoredFeedback.Reading
        assertEquals(AnswerRepresentation.KANA, feedback.representation)
        assertEquals(phrase, feedback.stimulus)
        assertEquals("I will confirm.", (feedback.acceptedAnswers[0] as ReadingExpectedAnswer.Kana).text.translation)
        assertEquals(2, feedback.acceptedAnswers.size)
        assertEquals(kana.explanation, feedback.explanation)
        val romajiFeedback = graded(romaji, PracticeAnswer.Text(" i "), true) as AuthoredFeedback.Reading
        assertEquals(listOf(ReadingExpectedAnswer.Romaji("i"), ReadingExpectedAnswer.Romaji("ii")), romajiFeedback.acceptedAnswers)
        for (input in listOf("I", "い", "i!", "ī")) {
            graded(romaji, PracticeAnswer.Text(input), false)
        }
    }

    private fun singleKanaReading(surface: String): ReadingExercise = kana.copy(
        acceptedAnswers = listOf(KanaReadingAnswer(JapaneseText(surface, surface, translation = "Reading example"))),
    )

    @Test fun kanaReadingComposesCanonicalMarksOnBothSidesWithoutChangingAuthoredFeedback() {
        for ((authored, submitted) in listOf(
            "が" to " か\u3099 \n",
            "は\u309A" to "ぱ",
            "ガ" to "カ\u3099",
        )) {
            val exercise = singleKanaReading(authored)
            val feedback = graded(exercise, PracticeAnswer.Text(submitted), true) as AuthoredFeedback.Reading
            assertEquals(authored, (feedback.acceptedAnswers.single() as ReadingExpectedAnswer.Kana).text.surface)
        }
        // Neither grading nor feedback rewrites the submitted draft or authored variants.
        val draft = " か\u3099 \n"
        graded(singleKanaReading("が"), PracticeAnswer.Text(draft), true)
        assertEquals(" か\u3099 \n", draft)
        graded(kana, PracticeAnswer.Text(" かくにんする。 "), true) // Authored alternative.
    }

    @Test fun kanaReadingDoesNotFoldWidthScriptSpellingSpacesOrPunctuation() {
        val distinctions = listOf(
            "が" to "か", "が" to "ｶﾞ", "が" to "ガ",
            "じ" to "ぢ", "ず" to "づ", "お" to "を", "は" to "わ", "へ" to "え",
            "つ" to "っ", "や" to "ゃ", "きゃ" to "きや",
            "かった" to "かた", "おばあさん" to "おばさん",
            "コート" to "コト", "コート" to "こーと", "コート" to "ｺｰﾄ",
            "かくにん" to "かく にん", "はい。" to "はい",
        )
        for ((authored, submitted) in distinctions) {
            graded(singleKanaReading(authored), PracticeAnswer.Text(submitted), false)
        }
        val explicitlyAuthored = kana.copy(acceptedAnswers = listOf(
            KanaReadingAnswer(JapaneseText("コート", "コート", translation = "Coat")),
            KanaReadingAnswer(JapaneseText("こーと", "こーと", translation = "Coat variant")),
        ))
        graded(explicitlyAuthored, PracticeAnswer.Text("こーと"), true)
    }

    @Test fun romajiReadingUsesOnlyTrimAndCanonicalCompositionWithAuthoredAlternatives() {
        graded(romaji, PracticeAnswer.Text(" ii "), true)
        val macron = romaji.copy(acceptedAnswers = listOf(RomajiReadingAnswer("ā")))
        graded(macron, PracticeAnswer.Text(" a\u0304 "), true)
        for (unlisted in listOf("a", "A", "aa", "ａ", "a-", "a \u0304", "a\u0304!")) {
            graded(macron, PracticeAnswer.Text(unlisted), false)
        }
        graded(romaji, PracticeAnswer.Text("I"), false)
    }

    @Test fun completionTrimsOnlyBoundariesAndPreservesCodeWidthPunctuationAndComposition() {
        fun codeFill(surface: String) = CompletionExercise(
            "code-fill", "Fill exactly", "{blank}",
            listOf(JapaneseText(surface, surface, translation = "Exact fill")),
            JapaneseText(surface, surface, translation = "Exact result"), "Match the authored fill.",
        )
        for ((authored, wrong) in listOf(
            "foo_bar" to "foo bar", "A" to "Ａ", "A!" to "A",
            "が" to "か\u3099", "ぱ" to "は\u309A",
        )) {
            val exercise = codeFill(authored)
            graded(exercise, PracticeAnswer.Text(" $authored "), true)
            graded(exercise, PracticeAnswer.Text(wrong), false)
        }
    }

    @Test fun completionMatchesOnlyAuthoredFillSurfacesNotExampleOrReading() {
        val feedback = graded(completion, PracticeAnswer.Text(" 連絡 "), true) as AuthoredFeedback.Completion
        assertEquals(listOf(fill, alternative), feedback.acceptedFills)
        assertEquals(phrase, feedback.completedExample)
        assertEquals(completion.explanation, feedback.explanation)
        for (input in listOf("確認します。", "かくにん", "確認。", "連絡します。")) {
            assertEquals(feedback, graded(completion, PracticeAnswer.Text(input), false))
        }
    }

    @Test fun productionRequiresExplicitAssessmentAndNeverInventsCorrectness() {
        val feedback = authoredFeedback(production) as AuthoredFeedback.Production
        assertEquals(listOf(LabeledExample("Example response", phrase)), feedback.examples)
        assertEquals(listOf(
            LabeledCriterion("Self-assessment criterion", "Use a polite ending"),
            LabeledCriterion("Self-assessment criterion", "Confirm the time"),
        ), feedback.criteria)
        for (assessment in Assessment.entries) {
            val result = assertIs<EvaluationResult.SelfAssessed>(evaluate(
                production, PracticeAnswer.SelfAssessment(" 私の返事 ", assessment),
            ))
            assertEquals("私の返事", result.response)
            assertEquals(assessment, result.assessment)
            assertEquals(feedback, result.feedback)
        }
        assertIs<EvaluationResult.Invalid>(evaluate(production, PracticeAnswer.Text(phrase.surface)))
    }

    @Test fun invalidSubmissionsNeverReceiveAnIncorrectGradeOrFeedback() {
        val invalid = listOf(
            choice to PracticeAnswer.Choice(" "),
            choice to PracticeAnswer.Choice("missing"),
            choice to PracticeAnswer.Text("correct"),
            kana to PracticeAnswer.Text(" \n "),
            romaji to PracticeAnswer.Choice("i"),
            completion to PracticeAnswer.Text("\t"),
            completion to PracticeAnswer.Choice("correct"),
            production to PracticeAnswer.SelfAssessment(" ", Assessment.MET_CRITERIA),
            production to PracticeAnswer.Choice("correct"),
        )
        for ((exercise, answer) in invalid) {
            assertIs<EvaluationResult.Invalid>(evaluate(exercise, answer))
        }
        assertEquals(EvaluationResult.Invalid(InvalidReason.BLANK_INPUT), evaluate(choice, PracticeAnswer.Choice(" ")))
        assertEquals(EvaluationResult.Invalid(InvalidReason.UNKNOWN_CHOICE), evaluate(choice, PracticeAnswer.Choice("missing")))
        assertEquals(EvaluationResult.Invalid(InvalidReason.WRONG_ANSWER_TYPE), evaluate(completion, PracticeAnswer.Choice("correct")))
    }
}
