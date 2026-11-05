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
 * What the student may practice, grouped like the learning path, in two views:
 *
 * * **Free practice:** the topics they have **finished** (every lesson and test of the topic is done) and their
 *   tasks. The tasks of a topic are the active tasks of its tests (`PRACTICE` units), each task once even when it
 *   sits in several tests. The final test of a parent topic adds nothing of its own: its tasks belong to the topics
 *   below.
 * * **Mistakes:** every topic with a **mistake**, a task whose latest answer is wrong (DECISIONS 2026-11-05),
 *   finished or not. A mistake belongs to the topic of its task (always a topic with no topics below it); a
 *   switched-off task or a task of an unpublished topic is not counted.
 */
class PracticeCatalog(
    private val content: ContentRepository,
    private val progress: ProgressRepository,
) {

    /** Every subject of the installed content with its parts and topics, for the free practice. */
    suspend fun subjects(): OperationResult<List<PracticeSubject>, LearningError> = guarded {
        val done = completedUnits()
        tree { topic ->
            val units = content.units(topic.id).orThrow()
            val finished = isFinished(units, done)
            finished to if (finished) poolOf(units) else emptySet()
        }
    }

    /**
     * Every subject with its parts and topics, for the mistake practice: a topic counts its mistakes ([PracticeNode.taskCount])
     * and can be chosen when it has one. [PracticeNode.finished] is true for every node here (it does not matter).
     */
    suspend fun mistakes(): OperationResult<List<PracticeSubject>, LearningError> = guarded {
        val byTopic = mistakesByTopic(publishedTopics())
        tree { topic -> true to byTopic[topic.id].orEmpty() }
    }

    /** The mistakes of the topics in [topicIds] (a parent stands for the topics below it), each once. */
    suspend fun mistakePool(topicIds: Collection<Long>): OperationResult<List<Long>, LearningError> = guarded {
        val published = publishedTopics()
        val byTopic = mistakesByTopic(published)
        val tasks = LinkedHashSet<Long>()
        topicIds.flatMap { leavesOf(it, published) }.distinct().forEach { tasks += byTopic[it].orEmpty() }
        tasks.toList()
    }

    /** Every mistake of the published content, each once (for "Összes hiba" and the counts). */
    suspend fun allMistakes(): OperationResult<List<Long>, LearningError> = guarded {
        mistakesByTopic(publishedTopics()).values.flatten().distinct()
    }

    /** The subjects with the nodes built by [leaf], which says of a topic with no topics below it whether it counts and its tasks. */
    private suspend fun tree(leaf: suspend (Topic) -> Pair<Boolean, Set<Long>>): List<PracticeSubject> =
        content.subjects().orThrow().map { subject ->
            PracticeSubject(subject, content.rootTopics(subject.id).orThrow().map { build(it, leaf).node })
        }

    /** The mistakes, by the topic of their task; switched-off tasks and tasks of topics not in [published] are left out. */
    private suspend fun mistakesByTopic(published: Set<Long>): Map<Long, Set<Long>> {
        val byTopic = mutableMapOf<Long, MutableSet<Long>>()
        for (id in progress.mistakeTaskIds().orThrow()) {
            val task = when (val result = content.task(id)) {
                is OperationResult.Success -> result.data
                is OperationResult.Error -> if (result.error == LearningError.NOT_FOUND) continue else throw ReadFailure(result.error)
            }
            if (task.isActive && task.topicId in published) byTopic.getOrPut(task.topicId) { LinkedHashSet() } += id
        }
        return byTopic
    }

    /**
     * Every published topic, found by walking the tree from the subjects. (`ContentRepository.topic(id)` also
     * returns a topic that is not published, so it cannot tell.)
     */
    private suspend fun publishedTopics(): Set<Long> {
        val found = HashSet<Long>()
        suspend fun walk(topic: Topic) {
            found += topic.id
            content.childTopics(topic.id).orThrow().forEach { walk(it) }
        }
        for (subject in content.subjects().orThrow()) content.rootTopics(subject.id).orThrow().forEach { walk(it) }
        return found
    }

    /**
     * The different ids of the active tasks of the finished topics in [topicIds]. A parent id stands for the
     * topics below it. A topic that is not finished, has no task or is unknown adds nothing.
     */
    suspend fun pool(topicIds: Collection<Long>): OperationResult<List<Long>, LearningError> = guarded {
        val done = completedUnits()
        val published = publishedTopics()
        val tasks = LinkedHashSet<Long>()
        val visited = HashSet<Long>()
        for (id in topicIds) {
            for (leaf in leavesOf(id, published)) {
                if (!visited.add(leaf)) continue
                val units = content.units(leaf).orThrow()
                if (isFinished(units, done)) tasks += poolOf(units)
            }
        }
        tasks.toList()
    }

    /** The topic itself when it has no topics below it, otherwise the leaves below it. A topic not in [published] has none. */
    private suspend fun leavesOf(topicId: Long, published: Set<Long>): List<Long> {
        if (topicId !in published) return emptyList()
        val children = content.childTopics(topicId).orThrow()
        return if (children.isEmpty()) listOf(topicId) else children.flatMap { leavesOf(it.id, published) }
    }

    private class Built(val node: PracticeNode, val pool: Set<Long>)

    private suspend fun build(topic: Topic, leaf: suspend (Topic) -> Pair<Boolean, Set<Long>>): Built {
        val children = content.childTopics(topic.id).orThrow()
        if (children.isEmpty()) {
            val (finished, pool) = leaf(topic)
            val leafIds = if (pool.isNotEmpty()) listOf(topic.id) else emptyList()
            return Built(PracticeNode(topic, emptyList(), finished, pool.size, leafIds), pool)
        }
        val built = children.map { build(it, leaf) }
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
