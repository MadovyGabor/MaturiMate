package com.madovygabor.maturimate.features.learning.domain

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.learning.domain.model.LearningUnit
import com.madovygabor.maturimate.features.learning.domain.model.Task
import kotlin.random.Random

/** What kind of round runs: a test of the path, or practice. It decides how the round can end and what the result says. */
enum class RoundKind { TEST, PRACTICE }

/** The start of a round: its tasks and its attempt, or why there is none. */
sealed interface RoundStart {
    data class Ready(val title: String, val tasks: List<Task>, val attemptId: Long) : RoundStart

    /** There is no task to ask. */
    data object Empty : RoundStart

    /** The content or the progress could not be read or written. */
    data object Failed : RoundStart
}

/**
 * How a round begins and ends, and where its answers go. The task-by-task part of a round (choose, type, check,
 * go on) is the same for every kind and lives in the view-model; only this part differs between a test and practice.
 */
interface RoundSource {
    val kind: RoundKind

    /** Chooses the tasks and starts the attempt. */
    suspend fun start(random: Random): RoundStart

    /** Records the answer to one task. */
    suspend fun answer(attemptId: Long, task: Task, answer: Answer, result: AnswerResult)

    /** The last task is answered (or, in practice, the student ended the round after some answers). */
    suspend fun finish(attemptId: Long, entries: List<RoundEntry>)

    /** The student left before the end; what was answered stays recorded. */
    suspend fun abandon(attemptId: Long, entries: List<RoundEntry>)
}

/**
 * A round of a test of the path: the required tasks of the unit and random ones to fill the round ([RoundPlanner]),
 * and the unit marked as done when the round is passed ([TestRecorder.finish]).
 */
class UnitRoundSource(
    private val unitId: Long,
    private val content: ContentRepository,
    private val recorder: TestRecorder,
) : RoundSource {

    override val kind = RoundKind.TEST

    override suspend fun start(random: Random): RoundStart {
        val unit = (content.unit(unitId) as? OperationResult.Success)?.data as? LearningUnit.Practice ?: return RoundStart.Failed
        val unitTasks = (content.unitTasks(unitId) as? OperationResult.Success)?.data ?: return RoundStart.Failed
        val tasks = mutableListOf<Task>()
        for (id in RoundPlanner.plan(unitTasks, unit.roundSize, random)) {
            tasks += (content.task(id) as? OperationResult.Success)?.data ?: return RoundStart.Failed
        }
        if (tasks.isEmpty()) return RoundStart.Empty
        val attemptId = (recorder.start(unitId) as? OperationResult.Success)?.data ?: return RoundStart.Failed
        return RoundStart.Ready(unit.title, tasks, attemptId)
    }

    override suspend fun answer(attemptId: Long, task: Task, answer: Answer, result: AnswerResult) {
        recorder.answer(attemptId, task, answer, result)
    }

    override suspend fun finish(attemptId: Long, entries: List<RoundEntry>) {
        recorder.finish(attemptId, unitId, entries)
    }

    override suspend fun abandon(attemptId: Long, entries: List<RoundEntry>) {
        recorder.abandon(attemptId, entries)
    }
}
