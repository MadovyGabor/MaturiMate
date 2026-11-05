package com.madovygabor.maturimate.features.practice.domain

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.learning.domain.Answer
import com.madovygabor.maturimate.features.learning.domain.AnswerResult
import com.madovygabor.maturimate.features.learning.domain.ContentRepository
import com.madovygabor.maturimate.features.learning.domain.RoundEntry
import com.madovygabor.maturimate.features.learning.domain.RoundKind
import com.madovygabor.maturimate.features.learning.domain.RoundSource
import com.madovygabor.maturimate.features.learning.domain.RoundStart
import com.madovygabor.maturimate.features.learning.domain.TestRecorder
import com.madovygabor.maturimate.features.learning.domain.model.AttemptType
import com.madovygabor.maturimate.features.learning.domain.model.Task
import kotlin.random.Random

/**
 * A free practice round: [count] random tasks of the finished topics in [topicIds] ([PracticeCatalog.pool],
 * [PracticePlanner]), recorded as a `FREE_PRACTICE` attempt. No unit is ever marked as done by it.
 *
 * [title] is the text the round shows on top (an app string, given by the screen so it is in the app's language).
 */
class PracticeRoundSource(
    private val topicIds: List<Long>,
    private val count: Int,
    private val title: String,
    private val catalog: PracticeCatalog,
    private val content: ContentRepository,
    private val recorder: TestRecorder,
) : RoundSource {

    override val kind = RoundKind.PRACTICE

    override suspend fun start(random: Random): RoundStart {
        val pool = (catalog.pool(topicIds) as? OperationResult.Success)?.data ?: return RoundStart.Failed
        val ids = PracticePlanner.plan(pool, count, random)
        if (ids.isEmpty()) return RoundStart.Empty
        val tasks = mutableListOf<Task>()
        for (id in ids) tasks += (content.task(id) as? OperationResult.Success)?.data ?: return RoundStart.Failed
        val attemptId = (recorder.startPractice(AttemptType.FREE_PRACTICE) as? OperationResult.Success)?.data ?: return RoundStart.Failed
        return RoundStart.Ready(title, tasks, attemptId)
    }

    override suspend fun answer(attemptId: Long, task: Task, answer: Answer, result: AnswerResult) {
        recorder.answer(attemptId, task, answer, result)
    }

    override suspend fun finish(attemptId: Long, entries: List<RoundEntry>) {
        recorder.finishPractice(attemptId, entries)
    }

    override suspend fun abandon(attemptId: Long, entries: List<RoundEntry>) {
        recorder.abandon(attemptId, entries)
    }
}
