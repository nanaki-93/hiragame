package com.github.nanaki_93.practice

import com.github.nanaki_93.content.AnswerRepresentation
import com.github.nanaki_93.content.ChoiceExercise
import com.github.nanaki_93.content.ChoiceOption
import com.github.nanaki_93.content.CompletionExercise
import com.github.nanaki_93.content.Exercise
import com.github.nanaki_93.content.JapaneseText
import com.github.nanaki_93.content.KanaReadingAnswer
import com.github.nanaki_93.content.ProductionExercise
import com.github.nanaki_93.content.ReadingExercise
import com.github.nanaki_93.content.RomajiReadingAnswer

/** Inputs are session-local; only the authored exercise determines what can be graded. */
sealed interface PracticeAnswer {
    data class Choice(val optionId: String) : PracticeAnswer
    data class Text(val value: String) : PracticeAnswer
    data class SelfAssessment(val response: String, val assessment: Assessment) : PracticeAnswer
}

enum class Assessment { MET_CRITERIA, NEEDS_PRACTICE }

enum class InvalidReason { BLANK_INPUT, UNKNOWN_CHOICE, WRONG_ANSWER_TYPE }

sealed interface EvaluationResult {
    data class Objective(val correct: Boolean, val feedback: AuthoredFeedback) : EvaluationResult
    /** A learner's judgment, never an automatic correct/incorrect grade. */
    data class SelfAssessed(val response: String, val assessment: Assessment, val feedback: AuthoredFeedback.Production) : EvaluationResult
    data class Invalid(val reason: InvalidReason) : EvaluationResult
}

/** Authored content retained as structured data (including Japanese reading and meaning). */
sealed interface AuthoredFeedback {
    data class Choice(val correctOption: ChoiceOption, val explanation: String) : AuthoredFeedback
    data class Reading(
        val stimulus: JapaneseText,
        val representation: AnswerRepresentation,
        val acceptedAnswers: List<ReadingExpectedAnswer>,
        val explanation: String,
    ) : AuthoredFeedback
    data class Completion(
        val acceptedFills: List<JapaneseText>,
        val completedExample: JapaneseText,
        val explanation: String,
    ) : AuthoredFeedback
    data class Production(
        val examples: List<LabeledExample>,
        val criteria: List<LabeledCriterion>,
    ) : AuthoredFeedback
}

sealed interface ReadingExpectedAnswer {
    data class Kana(val text: JapaneseText) : ReadingExpectedAnswer
    data class Romaji(val text: String) : ReadingExpectedAnswer
}

data class LabeledExample(val label: String, val text: JapaneseText)
data class LabeledCriterion(val label: String, val text: String)

/** Also used by Skip/Reveal: exposing expected content does not assign a grade. */
fun authoredFeedback(exercise: Exercise): AuthoredFeedback = when (exercise) {
    is ChoiceExercise -> AuthoredFeedback.Choice(
        exercise.options.first { it.id == exercise.correctOptionId }, exercise.explanation,
    )
    is ReadingExercise -> AuthoredFeedback.Reading(
        exercise.stimulus, exercise.answerRepresentation,
        exercise.acceptedAnswers.map {
            when (it) {
                is KanaReadingAnswer -> ReadingExpectedAnswer.Kana(it.text)
                is RomajiReadingAnswer -> ReadingExpectedAnswer.Romaji(it.text)
            }
        }, exercise.explanation,
    )
    is CompletionExercise -> AuthoredFeedback.Completion(
        exercise.acceptedAnswers.toList(), exercise.expectedCompletedExample, exercise.explanation,
    )
    is ProductionExercise -> AuthoredFeedback.Production(
        exercise.exampleResponses.map { LabeledExample("Example response", it) },
        exercise.criteria.map { LabeledCriterion("Self-assessment criterion", it) },
    )
}

/** Only surrounding whitespace is trimmed. Never infer spelling, kana, case, or punctuation variants. */
fun evaluate(exercise: Exercise, answer: PracticeAnswer): EvaluationResult = when (exercise) {
    is ChoiceExercise -> when (answer) {
        is PracticeAnswer.Choice -> when {
            answer.optionId.isBlank() -> EvaluationResult.Invalid(InvalidReason.BLANK_INPUT)
            exercise.options.none { it.id == answer.optionId } -> EvaluationResult.Invalid(InvalidReason.UNKNOWN_CHOICE)
            else -> EvaluationResult.Objective(answer.optionId == exercise.correctOptionId, authoredFeedback(exercise))
        }
        else -> EvaluationResult.Invalid(InvalidReason.WRONG_ANSWER_TYPE)
    }
    is ReadingExercise -> when (answer) {
        is PracticeAnswer.Text -> when {
            answer.value.isBlank() -> EvaluationResult.Invalid(InvalidReason.BLANK_INPUT)
            else -> EvaluationResult.Objective(exercise.acceptedAnswers.any {
                when (it) {
                    is KanaReadingAnswer -> it.text.surface == answer.value.trim()
                    is RomajiReadingAnswer -> it.text == answer.value.trim()
                }
            }, authoredFeedback(exercise))
        }
        else -> EvaluationResult.Invalid(InvalidReason.WRONG_ANSWER_TYPE)
    }
    is CompletionExercise -> when (answer) {
        is PracticeAnswer.Text -> when {
            answer.value.isBlank() -> EvaluationResult.Invalid(InvalidReason.BLANK_INPUT)
            else -> EvaluationResult.Objective(
                exercise.acceptedAnswers.any { it.surface == answer.value.trim() }, authoredFeedback(exercise),
            )
        }
        else -> EvaluationResult.Invalid(InvalidReason.WRONG_ANSWER_TYPE)
    }
    is ProductionExercise -> when (answer) {
        is PracticeAnswer.SelfAssessment -> when {
            answer.response.isBlank() -> EvaluationResult.Invalid(InvalidReason.BLANK_INPUT)
            else -> EvaluationResult.SelfAssessed(
                answer.response.trim(), answer.assessment, authoredFeedback(exercise) as AuthoredFeedback.Production,
            )
        }
        else -> EvaluationResult.Invalid(InvalidReason.WRONG_ANSWER_TYPE)
    }
}
