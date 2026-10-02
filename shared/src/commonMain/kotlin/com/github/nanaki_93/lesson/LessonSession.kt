package com.github.nanaki_93.lesson

import com.github.nanaki_93.content.Lesson
import com.github.nanaki_93.progress.LessonProgress
import com.github.nanaki_93.progress.LessonStage
import com.github.nanaki_93.practice.AuthoredFeedback
import com.github.nanaki_93.practice.authoredFeedback

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
    val resumed: Boolean,
    val notice: String?,
    val left: Boolean,
) {
    val stage: LessonStage get() = plan.stages[stageIndex].stage
    val item: LessonPlanItem? get() = itemIndex?.let { plan.stages[stageIndex].items[it] }
    val checkpointId: String? get() = item?.checkpointId
    val itemView: LessonItemView? get() = if (item is LessonPlanItem.Prompt) {
        if (outcome != null) LessonItemView.FEEDBACK else LessonItemView.PROMPT
    } else null
    val outcome: LessonOutcome? get() = itemIndex?.let { slots[stageIndex][it] }
    /** Authored explanation remains available on revisits, never on an unresolved prompt. */
    val feedback: AuthoredFeedback? get() = (item as? LessonPlanItem.Prompt)?.exercise
        ?.takeIf { outcome != null }?.let(::authoredFeedback)
    /** Counts cover only this run; a resumed run cannot reconstruct earlier answers. */
    val outcomes: List<LessonOutcome> get() = slots.flatMap { it.filterNotNull() }

    private fun move(stage: Int, index: Int?, slots: List<List<LessonOutcome?>> = this.slots): LessonSession =
        LessonSession(id, revision + 1, plan, stage, index, slots, resumed, notice, false)

    private fun setOutcome(index: Int, outcome: LessonOutcome?): List<List<LessonOutcome?>> =
        slots.toMutableList().also { stages ->
            stages[stageIndex] = stages[stageIndex].toMutableList().also { it[index] = outcome }
        }

    internal fun transition(command: LessonCommand): LessonSession {
        if (left || command.sessionId != id || command.revision != revision) return this
        if (command is LessonCommand.Leave) return LessonSession(
            id, revision + 1, plan, stageIndex, itemIndex, slots, resumed, notice, true,
        )
        if (command is LessonCommand.Restart) return if (command.newSessionId > id) {
            create(command.newSessionId, plan)
        } else this
        val stageItems = plan.stages[stageIndex].items
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
                itemIndex == null && stageIndex < plan.stages.lastIndex -> move(stageIndex + 1, null)
                itemIndex != null && item is LessonPlanItem.Turn -> advance()
                else -> this // An exercise requires resolution and an explicit Continue.
            }
            is LessonCommand.Continue -> if (item is LessonPlanItem.Prompt && outcome != null) advance() else this
            is LessonCommand.Skip -> if (item is LessonPlanItem.Prompt && outcome == null) {
                move(stageIndex, itemIndex, setOutcome(itemIndex!!, LessonOutcome.SKIPPED))
            } else this
            is LessonCommand.SkipRemaining -> {
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
            is LessonCommand.Retry -> if (item is LessonPlanItem.Prompt && outcome != null) {
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
                plan.stages.map { List(it.items.size) { null } }, resumed,
                if (resumed && items.getOrNull(itemIndex ?: -1) is LessonPlanItem.Prompt) LESSON_RESUME_NOTICE else null,
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
