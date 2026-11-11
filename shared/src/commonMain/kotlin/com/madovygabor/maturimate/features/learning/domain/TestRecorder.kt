package com.madovygabor.maturimate.features.learning.domain

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.learning.domain.model.AttemptAnswer
import com.madovygabor.maturimate.features.learning.domain.model.AttemptStatus
import com.madovygabor.maturimate.features.learning.domain.model.AttemptType
import com.madovygabor.maturimate.features.learning.domain.model.Task
import com.madovygabor.maturimate.features.learning.domain.model.TaskProgress
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgress
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgressStatus
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** The answer to one task of a round, with the points it earned. */
data class RoundEntry(
    val task: Task,
    val answer: Answer,
    val result: AnswerResult,
    val earnedPoints: Int,
    val maxPoints: Int,
) {
    val correct: Boolean get() = result.correct
}

/**
 * Keeps what a test round leaves behind in the user database: the attempt, the answer to each task, the progress
 * of each task and, when the round is finished, the unit as done. It holds the rules; the ViewModel only tells it
 * what happened.
 *
 * * A round is an attempt (`UNIT_ROUND`): `IN_PROGRESS` while it runs, `FINISHED` with the points when the last
 *   task is answered, `ABANDONED` when the student leaves.
 * * Every answer is recorded (`attempt_answer`) and counted in the progress of the task: right or wrong, the right
 *   answers in a row.
 * * A unit is done when a round of it is passed: every task of the round answered right. A round that is not
 *   passed is still finished and kept, but the unit stays open, so the next step of the path does not open.
 *   A unit that is done stays done.
 * * A practice round (`startPractice`, `FREE_PRACTICE` or `MISTAKE_PRACTICE`) is recorded the same way, but belongs
 *   to no unit and never marks one as done.
 */
@OptIn(ExperimentalTime::class)
class TestRecorder(
    private val progress: ProgressRepository,
    private val tracker: StudyTracker? = null,
    private val now: () -> Instant = { Clock.System.now() },
) {

    private var clock = StudyClock(now = now)

    /** Starts a round of the test unit; returns the id of the attempt. */
    suspend fun start(unitId: Long): OperationResult<String, LearningError> {
        clock = StudyClock(now = now)
        val started = progress.startAttempt(AttemptType.UNIT_ROUND, topicId = null, learningUnitId = unitId, startedAt = now())
        if (started is OperationResult.Success) markOpened(unitId)
        return started
    }

    /** A unit opened for the first time is `IN_PROGRESS` from now on; one that already has a row is left as it is. */
    suspend fun markOpened(unitId: Long) {
        val earlier = (progress.unitProgressOf(unitId) as? OperationResult.Success)?.data
        if (earlier == null) progress.saveUnitProgress(UnitProgress(unitId, UnitProgressStatus.IN_PROGRESS, now(), null))
    }

    /**
     * Starts a practice round of the given [type] (`FREE_PRACTICE` or `MISTAKE_PRACTICE`), which belongs to no unit
     * and no topic; returns the id of the attempt. Answers are recorded with [answer], like in a test round.
     */
    suspend fun startPractice(type: AttemptType): OperationResult<String, LearningError> {
        require(type == AttemptType.FREE_PRACTICE || type == AttemptType.MISTAKE_PRACTICE) { "$type is not a practice round" }
        clock = StudyClock(now = now)
        return progress.startAttempt(type, topicId = null, learningUnitId = null, startedAt = now())
    }

    /**
     * Starts a review round of the finished topic [topicId] (`REVIEW`, the attempt has the topic); returns the id of
     * the attempt. It is closed like practice ([finishPractice], [abandon]) and never marks a unit as done.
     */
    suspend fun startReview(topicId: Long): OperationResult<String, LearningError> {
        clock = StudyClock(now = now)
        return progress.startAttempt(AttemptType.REVIEW, topicId = topicId, learningUnitId = null, startedAt = now())
    }

    /** Records the answer to [task] and counts it in the progress of the task. */
    suspend fun answer(attemptId: String, task: Task, answer: Answer, result: AnswerResult): RoundEntry {
        val entry = RoundEntry(task, answer, result, earnedPoints(task, result), task.maxPoints)
        val time = now()
        tracker?.record(clock.lap(), tasks = 1)
        progress.recordAnswer(AttemptAnswer(attemptId, task.id, entry.correct, entry.earnedPoints, entry.maxPoints, time, answer.describe()))
        progress.recordTaskAnswer(task.id, entry.correct, time)
        return entry
    }

    /** Closes the round with its points and, when it is passed, marks the unit as done (a unit that is done stays done). */
    suspend fun finish(attemptId: String, unitId: Long, entries: List<RoundEntry>): OperationResult<Unit, LearningError> {
        val time = now()
        val closed = close(attemptId, AttemptStatus.FINISHED, entries)
        if (closed is OperationResult.Error) return closed
        if (!passed(entries)) return OperationResult.Success(Unit)
        val earlier = (progress.unitProgressOf(unitId) as? OperationResult.Success)?.data
        if (earlier?.status == UnitProgressStatus.COMPLETED) return OperationResult.Success(Unit)
        return progress.saveUnitProgress(UnitProgress(unitId, UnitProgressStatus.COMPLETED, earlier?.startedAt ?: time, time))
    }

    /**
     * Closes a practice round with its points. Unlike [finish] it never touches the progress of a unit: practice
     * is not a step of the path, so no unit is done, however many answers were right.
     */
    suspend fun finishPractice(attemptId: String, entries: List<RoundEntry>): OperationResult<Unit, LearningError> =
        close(attemptId, AttemptStatus.FINISHED, entries)

    /** The student left the round before the end; what was answered stays recorded. */
    suspend fun abandon(attemptId: String, entries: List<RoundEntry>): OperationResult<Unit, LearningError> =
        close(attemptId, AttemptStatus.ABANDONED, entries)

    private suspend fun close(attemptId: String, status: AttemptStatus, entries: List<RoundEntry>) =
        progress.finishAttempt(attemptId, status, now(), entries.sumOf { it.earnedPoints }, entries.sumOf { it.maxPoints })

    companion object {
        /** A round is passed when every task of it is answered right. */
        fun passed(entries: List<RoundEntry>): Boolean = entries.isNotEmpty() && entries.all { it.correct }

        /** The full points for a right answer; a task with several slots (pairs, gaps) earns by the share of right slots. */
        fun earnedPoints(task: Task, result: AnswerResult): Int {
            if (result.correct) return task.maxPoints
            val total = result.slots.size
            if (total == 0) return 0
            return task.maxPoints * result.slots.values.count { it } / total
        }
    }
}

/** The answer as short text for the record: `1=B;2=A`, or the typed texts of the slots. */
fun Answer.describe(): String = when (this) {
    is Answer.Choice -> labels.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" }
    is Answer.Typed -> texts.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" }
}
