package com.madovygabor.maturimate.features.practice.domain

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.learning.domain.ContentRepository
import com.madovygabor.maturimate.features.learning.domain.LearningError
import com.madovygabor.maturimate.features.learning.domain.ProgressRepository
import com.madovygabor.maturimate.features.learning.domain.ReadFailure
import com.madovygabor.maturimate.features.learning.domain.guarded
import com.madovygabor.maturimate.features.learning.domain.model.Subject
import com.madovygabor.maturimate.features.learning.domain.model.Topic
import com.madovygabor.maturimate.features.learning.domain.orThrow
import kotlin.math.roundToInt

/** How a topic goes now: of the tasks the student has seen ([seen]), how many are answered right last ([right]). */
data class TopicScore(val topic: Topic, val subject: Subject, val seen: Int, val right: Int) {
    /** The share right, in whole percent. */
    val percent: Int get() = if (seen == 0) 0 else (right * 100.0 / seen).roundToInt()
}

/** The weakest and the strongest topics, each list at most [TopicScores.SHOWN] long. */
data class ScoreSummary(val weak: List<TopicScore>, val strong: List<TopicScore>) {
    val isEmpty: Boolean get() = weak.isEmpty() && strong.isEmpty()

    companion object {
        val EMPTY = ScoreSummary(emptyList(), emptyList())
    }
}

/**
 * Which topics go best and which worst (DECISIONS 2026-11-05). Of each task only the latest answer counts: a topic's
 * score is the tasks seen whose latest answer is right, divided by the tasks seen. A topic gets a score from
 * [MIN_SEEN] tasks seen on; below [WEAK_BELOW] percent it is weak. Only published topics and active tasks count.
 */
class TopicScores(
    private val content: ContentRepository,
    private val progress: ProgressRepository,
) {

    suspend fun load(): OperationResult<ScoreSummary, LearningError> = guarded {
        val tree = content.publishedTree()
        val seen = mutableMapOf<Long, Int>()
        val right = mutableMapOf<Long, Int>()
        for (answered in progress.answeredTasks().orThrow()) {
            val task = when (val result = content.task(answered.taskId)) {
                is OperationResult.Success -> result.data
                is OperationResult.Error -> if (result.error == LearningError.NOT_FOUND) continue else throw ReadFailure(result.error)
            }
            if (!task.isActive || task.topicId !in tree.leaves) continue
            seen[task.topicId] = (seen[task.topicId] ?: 0) + 1
            if (answered.rightNow) right[task.topicId] = (right[task.topicId] ?: 0) + 1
        }
        val scores = seen.filterValues { it >= MIN_SEEN }.map { (topicId, count) ->
            TopicScore(tree.topics.getValue(topicId), tree.subjectOf.getValue(topicId), count, right[topicId] ?: 0)
        }
        ScoreSummary(
            weak = scores.filter { it.percent < WEAK_BELOW }
                .sortedWith(compareBy<TopicScore> { it.percent }.thenByDescending { it.seen }.thenBy { it.topic.title })
                .take(SHOWN),
            strong = scores.filter { it.percent >= WEAK_BELOW }
                .sortedWith(compareByDescending<TopicScore> { it.percent }.thenByDescending { it.seen }.thenBy { it.topic.title })
                .take(SHOWN),
        )
    }

    companion object {
        /**
         * Tasks seen before a topic gets a score. Low for now, so the beta can be tried with the small topics of the
         * content; it is raised before the public release (ROADMAP 19.1).
         */
        const val MIN_SEEN = 5

        /** Below this percent a topic is weak. */
        const val WEAK_BELOW = 80

        /** The most weak, and the most strong, topics shown. */
        const val SHOWN = 3
    }
}

/** The published part of the content: every published topic, the subject of each, and the topics with nothing below them. */
internal class PublishedTree(val topics: Map<Long, Topic>, val subjectOf: Map<Long, Subject>, val leaves: Set<Long>)

/**
 * Walks the tree from the subjects. (`ContentRepository.topic(id)` also returns a topic that is not published, so
 * it cannot tell; the walk only meets published ones.) Call it inside [guarded].
 */
internal suspend fun ContentRepository.publishedTree(): PublishedTree {
    val topics = mutableMapOf<Long, Topic>()
    val subjectOf = mutableMapOf<Long, Subject>()
    val leaves = mutableSetOf<Long>()
    suspend fun walk(topic: Topic, subject: Subject) {
        topics[topic.id] = topic
        subjectOf[topic.id] = subject
        val children = childTopics(topic.id).orThrow()
        if (children.isEmpty()) leaves += topic.id else children.forEach { walk(it, subject) }
    }
    for (subject in subjects().orThrow()) rootTopics(subject.id).orThrow().forEach { walk(it, subject) }
    return PublishedTree(topics, subjectOf, leaves)
}
