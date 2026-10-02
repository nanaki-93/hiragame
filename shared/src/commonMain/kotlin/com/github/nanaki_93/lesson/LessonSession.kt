package com.github.nanaki_93.lesson

import com.github.nanaki_93.content.Lesson
import com.github.nanaki_93.progress.LessonProgress
import com.github.nanaki_93.progress.LessonStage
import com.github.nanaki_93.practice.Assessment
import com.github.nanaki_93.practice.AuthoredFeedback
import com.github.nanaki_93.practice.EvaluationResult
import com.github.nanaki_93.practice.InvalidReason
import com.github.nanaki_93.practice.LabeledCriterion
import com.github.nanaki_93.practice.LabeledExample
import com.github.nanaki_93.practice.PracticeAnswer
import com.github.nanaki_93.practice.authoredFeedback
import com.github.nanaki_93.practice.evaluate

/** Only one outcome per executable item; no attempt history or responses are saved here. */
enum class LessonOutcome { SKIPPED, REVEALED, CORRECT, INCORRECT, SELF_MET_CRITERIA, SELF_NEEDS_PRACTICE }

enum class LessonItemView { PROMPT, FEEDBACK }

/** Actions carry the identity and revision visible when the control was rendered. */
sealed interface LessonCommand {
    val sessionId: Long
    val revision: Long

    data class Next(override val sessionId: Long, override val revision: Long) : LessonCommand
    data class Previous(override val sessionId: Long, override val revision: Long) : LessonCommand
    data class Continue(override val sessionId: Long, override val revision: Long) : LessonCommand
    data class Submit(override val sessionId: Long, override val revision: Long, val answer: PracticeAnswer) : LessonCommand
    data class Reveal(override val sessionId: Long, override val revision: Long) : LessonCommand
    data class Skip(override val sessionId: Long, override val revision: Long) : LessonCommand
    /** Deliberately bypass unresolved prompts in this stage, never crediting them as attempted. */
    data class SkipRemaining(override val sessionId: Long, override val revision: Long) : LessonCommand
    data class Retry(override val sessionId: Long, override val revision: Long) : LessonCommand
    /** A fresh identity prevents a callback from a previous run matching the restarted session. */
    data class Restart(override val sessionId: Long, override val revision: Long, val newSessionId: Long) : LessonCommand
    data class Leave(override val sessionId: Long, override val revision: Long) : LessonCommand
}

const val LESSON_RESUME_NOTICE = "Your place was saved; previous responses and feedback are not restored."

sealed interface LessonResumeResult {
    data class Available(val session: LessonSession) : LessonResumeResult
    data class Incompatible(val reason: LessonCheckpointMismatch) : LessonResumeResult
}

/** Immutable runtime cursor. A null item is the explicit stage entry, even for an empty stage. */
class LessonSession private constructor(
    val id: Long,
    val revision: Long,
    val plan: LessonPlan,
    private val stageIndex: Int,
    val itemIndex: Int?,
    private val slots: List<List<LessonOutcome?>>,
    /** One optional reflection when the role-play has no production exercise. */
    private val objectiveOutcome: LessonOutcome?,
    val validation: InvalidReason?,
    val resumed: Boolean,
    val notice: String?,
    val left: Boolean,
) {
    val stage: LessonStage get() = plan.stages[stageIndex].stage
    val item: LessonPlanItem? get() = itemIndex?.let { plan.stages[stageIndex].items[it] }
    val checkpointId: String? get() = item?.checkpointId
    private val objectiveOnly: Boolean get() = stage == LessonStage.ROLE_PLAY &&
        itemIndex == null && plan.stages[stageIndex].empty == EmptyLessonStage.ROLE_PLAY_OBJECTIVE_ONLY
    val itemView: LessonItemView? get() = if (item is LessonPlanItem.Prompt || objectiveOnly) {
        if (outcome != null) LessonItemView.FEEDBACK else LessonItemView.PROMPT
    } else null
    val outcome: LessonOutcome? get() = if (objectiveOnly) objectiveOutcome else itemIndex?.let { slots[stageIndex][it] }
    /** Authored explanation remains available on revisits, never on an unresolved prompt. */
    val feedback: AuthoredFeedback? get() = if (outcome == null) null else when {
        objectiveOnly -> AuthoredFeedback.Production(
            plan.lesson.rolePlay.examples.map { LabeledExample("Possible response", it) },
            plan.lesson.rolePlay.criteria.map { LabeledCriterion("Self-assessment criterion", it) },
        )
        else -> (item as? LessonPlanItem.Prompt)?.exercise?.let(::authoredFeedback)
    }
    /** Counts cover only this run; a resumed run cannot reconstruct earlier answers. */
    val outcomes: List<LessonOutcome> get() = slots.flatMap { it.filterNotNull() } + listOfNotNull(objectiveOutcome)

    private fun move(stage: Int, index: Int?, slots: List<List<LessonOutcome?>> = this.slots,
                     objectiveOutcome: LessonOutcome? = this.objectiveOutcome): LessonSession =
        LessonSession(id, revision + 1, plan, stage, index, slots, objectiveOutcome, null, resumed, notice, false)

    private fun invalid(reason: InvalidReason): LessonSession =
        LessonSession(id, revision + 1, plan, stageIndex, itemIndex, slots, objectiveOutcome,
            reason, resumed, notice, false)

    private fun resolve(outcome: LessonOutcome): LessonSession = if (objectiveOnly) {
        move(stageIndex, itemIndex, objectiveOutcome = outcome)
    } else move(stageIndex, itemIndex, setOutcome(itemIndex!!, outcome))

    private fun setOutcome(index: Int, outcome: LessonOutcome?): List<List<LessonOutcome?>> =
        slots.toMutableList().also { stages ->
            stages[stageIndex] = stages[stageIndex].toMutableList().also { it[index] = outcome }
        }

    internal fun transition(command: LessonCommand): LessonSession {
        if (left || command.sessionId != id || command.revision != revision) return this
        if (command is LessonCommand.Leave) return LessonSession(
            id, revision + 1, plan, stageIndex, itemIndex, slots, objectiveOutcome, null, resumed, notice, true,
        )
        if (command is LessonCommand.Restart) return if (command.newSessionId > id) {
            create(command.newSessionId, plan)
        } else this
        val stageItems = plan.stages[stageIndex].items
        val currentItem = item
        return when (command) {
            is LessonCommand.Previous -> when {
                itemIndex != null -> move(stageIndex, if (itemIndex == 0) null else itemIndex - 1)
                stageIndex > 0 -> {
                    val previous = plan.stages[stageIndex - 1].items
                    move(stageIndex - 1, previous.lastIndex.takeIf { it >= 0 })
                }
                else -> this
            }
            is LessonCommand.Next -> when {
                itemIndex == null && stageItems.isNotEmpty() -> move(stageIndex, 0)
                itemIndex == null && objectiveOnly -> this // Resolve, then explicitly Continue.
                itemIndex == null && stageIndex < plan.stages.lastIndex -> move(stageIndex + 1, null)
                itemIndex != null && item is LessonPlanItem.Turn -> advance()
                else -> this // An exercise requires resolution and an explicit Continue.
            }
            is LessonCommand.Submit -> if (currentItem is LessonPlanItem.Prompt && outcome == null) {
                when (val result = evaluate(currentItem.exercise, command.answer)) {
                    is EvaluationResult.Invalid -> invalid(result.reason)
                    is EvaluationResult.Objective -> resolve(if (result.correct) LessonOutcome.CORRECT else LessonOutcome.INCORRECT)
                    is EvaluationResult.SelfAssessed -> resolve(when (result.assessment) {
                        Assessment.MET_CRITERIA -> LessonOutcome.SELF_MET_CRITERIA
                        Assessment.NEEDS_PRACTICE -> LessonOutcome.SELF_NEEDS_PRACTICE
                    })
                }
            } else if (objectiveOnly && outcome == null) {
                // No exercise to grade: reflect on the authored objective without comparing to examples.
                when (val answer = command.answer) {
                    is PracticeAnswer.SelfAssessment -> if (answer.response.isBlank()) invalid(InvalidReason.BLANK_INPUT)
                        else resolve(when (answer.assessment) {
                            Assessment.MET_CRITERIA -> LessonOutcome.SELF_MET_CRITERIA
                            Assessment.NEEDS_PRACTICE -> LessonOutcome.SELF_NEEDS_PRACTICE
                        })
                    else -> invalid(InvalidReason.WRONG_ANSWER_TYPE)
                }
            } else this
            is LessonCommand.Continue -> if (item is LessonPlanItem.Prompt && outcome != null) advance()
                else if (objectiveOnly && outcome != null) move(stageIndex + 1, null) else this
            is LessonCommand.Skip -> if ((item is LessonPlanItem.Prompt || objectiveOnly) && outcome == null)
                resolve(LessonOutcome.SKIPPED) else this
            is LessonCommand.Reveal -> if ((item is LessonPlanItem.Prompt || objectiveOnly) && outcome == null)
                resolve(LessonOutcome.REVEALED) else this
            is LessonCommand.SkipRemaining -> {
                if (objectiveOnly && outcome == null) return move(stageIndex + 1, null,
                    objectiveOutcome = LessonOutcome.SKIPPED)
                if (stageIndex == plan.stages.lastIndex || stageItems.isEmpty() ||
                    stageItems.indices.none { index -> index >= (itemIndex ?: 0) &&
                        stageItems[index] is LessonPlanItem.Prompt && slots[stageIndex][index] == null }) return this
                val remaining = slots[stageIndex].toMutableList()
                for (index in (itemIndex ?: 0)..stageItems.lastIndex) {
                    if (stageItems[index] is LessonPlanItem.Prompt && remaining[index] == null) {
                        remaining[index] = LessonOutcome.SKIPPED
                    }
                }
                val updated = slots.toMutableList().also { it[stageIndex] = remaining }
                move(stageIndex + 1, null, updated)
            }
            is LessonCommand.Retry -> if (objectiveOnly && outcome != null) {
                move(stageIndex, itemIndex, objectiveOutcome = null)
            } else if (item is LessonPlanItem.Prompt && outcome != null) {
                move(stageIndex, itemIndex, setOutcome(itemIndex!!, null))
            } else this
            else -> this
        }
    }

    private fun advance(): LessonSession {
        val next = itemIndex!! + 1
        return if (next < plan.stages[stageIndex].items.size) move(stageIndex, next)
        else if (stageIndex < plan.stages.lastIndex) move(stageIndex + 1, null)
        else this
    }

    companion object {
        internal fun create(id: Long, plan: LessonPlan, stage: LessonStage = LessonStage.SITUATION,
                            itemId: String? = null, resumed: Boolean = false): LessonSession {
            val stageIndex = plan.stages.indexOfFirst { it.stage == stage }
            require(stageIndex >= 0) { "Unknown lesson stage" }
            val items = plan.stages[stageIndex].items
            val itemIndex = itemId?.let { id -> items.indexOfFirst { it.checkpointId == id }.also {
                require(it >= 0) { "Invalid lesson checkpoint" }
            } }
            return LessonSession(id, 0, plan, stageIndex, itemIndex,
                plan.stages.map { List(it.items.size) { null } }, null, null, resumed,
                if (resumed && (items.getOrNull(itemIndex ?: -1) is LessonPlanItem.Prompt ||
                    (stage == LessonStage.ROLE_PLAY && items.isEmpty()))) LESSON_RESUME_NOTICE else null,
                false)
        }
    }
}

fun startLessonSession(sessionId: Long, lesson: Lesson): LessonSession =
    LessonSession.create(sessionId, buildLessonPlan(lesson))

/** Does not mutate the record or advance the saved checkpoint. */
fun resumeLessonSession(sessionId: Long, lesson: Lesson, record: LessonProgress): LessonResumeResult =
    when (val resolved = resolveLessonCheckpoint(lesson, record)) {
        is LessonCheckpointResolution.Available -> LessonResumeResult.Available(
            LessonSession.create(sessionId, buildLessonPlan(lesson), resolved.stage.stage,
                resolved.item?.checkpointId, resumed = true),
        )
        is LessonCheckpointResolution.Incompatible -> LessonResumeResult.Incompatible(resolved.reason)
        LessonCheckpointResolution.NoRecord -> error("A record was supplied")
    }

fun reduceLesson(state: LessonSession, command: LessonCommand): LessonSession = state.transition(command)
