package com.madovygabor.maturimate.features.practice.domain

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.learning.domain.ContentRepository
import com.madovygabor.maturimate.features.learning.domain.LearningError
import com.madovygabor.maturimate.features.learning.domain.ProgressRepository
import com.madovygabor.maturimate.features.learning.domain.ReadFailure
import com.madovygabor.maturimate.features.learning.domain.guarded
import com.madovygabor.maturimate.features.learning.domain.model.LearningUnit
import com.madovygabor.maturimate.features.learning.domain.model.Subject
import com.madovygabor.maturimate.features.learning.domain.model.Topic
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgressStatus
import com.madovygabor.maturimate.features.learning.domain.orThrow

/**
 * A node of the practice picker: a big part or a topic of a subject. A leaf (a topic with no topics below it)
 * holds the tasks; a parent only groups its children.
 */
data class PracticeNode(
    val topic: Topic,
    val children: List<PracticeNode>,
    /** A leaf: it has units and every one of them is done. A parent: it has leaves and all of them are finished. */
    val finished: Boolean,
    /** How many different tasks the student can draw from the finished leaves of this node. */
    val taskCount: Int,
    /**
     * The finished leaves of this node that have at least one task (the node itself, for a leaf): what the picker
     * selects when the node is ticked. A topic that is not finished, or has no task, is never in it.
     */
    val leafIds: List<Long>,
)

/** A subject with the nodes of its big parts. */
data class PracticeSubject(val subject: Subject, val parts: List<PracticeNode>)

/**
 * What the student may practice freely: the topics they have **finished** (every lesson and test of the topic is
 * done), grouped like the learning path, and the tasks of those topics.
 *
 * The tasks of a topic are the active tasks of its tests (`PRACTICE` units), each task once even when it sits
 * in several tests. The final test of a parent topic adds nothing of its own: its tasks belong to the topics below.
 */
class PracticeCatalog(
    private val content: ContentRepository,
    private val progress: ProgressRepository,
) {

    /** Every subject of the installed content with its parts and topics. */
    suspend fun subjects(): OperationResult<List<PracticeSubject>, LearningError> = guarded {
        val done = completedUnits()
        content.subjects().orThrow().map { subject ->
            PracticeSubject(subject, content.rootTopics(subject.id).orThrow().map { build(it, done).node })
        }
    }

    /**
     * The different ids of the active tasks of the finished topics in [topicIds]. A parent id stands for the
     * topics below it. A topic that is not finished, has no task or is unknown adds nothing.
     */
    suspend fun pool(topicIds: Collection<Long>): OperationResult<List<Long>, LearningError> = guarded {
        val done = completedUnits()
        val tasks = LinkedHashSet<Long>()
        val visited = HashSet<Long>()
        for (id in topicIds) {
            for (leaf in leavesOf(id)) {
                if (!visited.add(leaf)) continue
                val units = content.units(leaf).orThrow()
                if (isFinished(units, done)) tasks += poolOf(units)
            }
        }
        tasks.toList()
    }

    /** The topic itself when it has no topics below it, otherwise the leaves below it. An unknown or unpublished topic has none. */
    private suspend fun leavesOf(topicId: Long): List<Long> {
        when (val topic = content.topic(topicId)) {
            is OperationResult.Success -> Unit
            is OperationResult.Error -> if (topic.error == LearningError.NOT_FOUND) return emptyList() else throw ReadFailure(topic.error)
        }
        val children = content.childTopics(topicId).orThrow()
        return if (children.isEmpty()) listOf(topicId) else children.flatMap { leavesOf(it.id) }
    }

    private class Built(val node: PracticeNode, val pool: Set<Long>)

    private suspend fun build(topic: Topic, done: Set<Long>): Built {
        val children = content.childTopics(topic.id).orThrow()
        if (children.isEmpty()) {
            val units = content.units(topic.id).orThrow()
            val finished = isFinished(units, done)
            val pool = if (finished) poolOf(units) else emptySet()
            val leafIds = if (pool.isNotEmpty()) listOf(topic.id) else emptyList()
            return Built(PracticeNode(topic, emptyList(), finished, pool.size, leafIds), pool)
        }
        val built = children.map { build(it, done) }
        val pool = built.flatMapTo(LinkedHashSet()) { it.pool }
        val node = PracticeNode(
            topic = topic,
            children = built.map { it.node },
            finished = built.all { it.node.finished },
            taskCount = pool.size,
            leafIds = built.flatMap { it.node.leafIds },
        )
        return Built(node, pool)
    }

    /** A topic is finished when it has units and every one of them is done. */
    private fun isFinished(units: List<LearningUnit>, done: Set<Long>) = units.isNotEmpty() && units.all { it.id in done }

    /** The active tasks of the tests of a topic, each once. */
    private suspend fun poolOf(units: List<LearningUnit>): Set<Long> {
        val tasks = LinkedHashSet<Long>()
        for (unit in units.filterIsInstance<LearningUnit.Practice>()) {
            for (task in content.unitTasks(unit.id).orThrow()) if (task.isActive) tasks += task.taskId
        }
        return tasks
    }

    private suspend fun completedUnits(): Set<Long> =
        progress.unitProgress().orThrow().filter { it.status == UnitProgressStatus.COMPLETED }.map { it.learningUnitId }.toSet()
}
