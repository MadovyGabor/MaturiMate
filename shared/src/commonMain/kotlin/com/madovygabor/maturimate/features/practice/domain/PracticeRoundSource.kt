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

/** What a practice round draws from: the finished topics, or the mistakes. */
enum class PracticeMode { FREE, MISTAKES }

/**
 * A practice round of [count] random tasks ([PracticePlanner]), recorded as an attempt that belongs to no unit. No
 * unit is ever marked as done by it.
 *
 * * [PracticeMode.FREE]: the tasks of the finished topics in [topicIds] ([PracticeCatalog.pool]), a `FREE_PRACTICE` attempt.
 * * [PracticeMode.MISTAKES]: the mistakes of the topics in [topicIds], or every mistake when [topicIds] is empty
 *   ([PracticeCatalog.mistakePool], [PracticeCatalog.allMistakes]), a `MISTAKE_PRACTICE` attempt. The mistakes are
 *   read at every start, so "Még egy kör" no longer asks the ones answered right; with none left the round says so.
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
    private val mode: PracticeMode = PracticeMode.FREE,
) : RoundSource {

    override val kind = RoundKind.PRACTICE

    override suspend fun start(random: Random): RoundStart {
        val pool = when {
            mode == PracticeMode.FREE -> catalog.pool(topicIds)
            topicIds.isEmpty() -> catalog.allMistakes()
            else -> catalog.mistakePool(topicIds)
        }
        val ids = PracticePlanner.plan((pool as? OperationResult.Success)?.data ?: return RoundStart.Failed, count, random)
        if (ids.isEmpty()) return if (mode == PracticeMode.MISTAKES) RoundStart.NoMistakes else RoundStart.Empty
        val tasks = mutableListOf<Task>()
        for (id in ids) tasks += (content.task(id) as? OperationResult.Success)?.data ?: return RoundStart.Failed
        val type = if (mode == PracticeMode.MISTAKES) AttemptType.MISTAKE_PRACTICE else AttemptType.FREE_PRACTICE
        val attemptId = (recorder.startPractice(type) as? OperationResult.Success)?.data ?: return RoundStart.Failed
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
