package com.madovygabor.maturimate.features.learning.domain

import com.madovygabor.maturimate.features.learning.domain.model.UnitTask
import kotlin.random.Random

/**
 * Chooses the tasks of one round of a test.
 *
 * A round shows [roundSize] tasks: every **required** task (`isCore`) of the unit, and random ones of the other
 * active tasks to fill the round; the order is random too. A unit of 20 tasks with a round size of 10 and no
 * required task therefore shows a random half each time. A task that is switched off is never chosen.
 */
object RoundPlanner {

    /** The ids of the tasks of the round, in the order they are asked. */
    fun plan(tasks: List<UnitTask>, roundSize: Int, random: Random = Random.Default): List<Long> {
        val active = tasks.filter { it.isActive }
        val required = active.filter { it.isCore }
        val others = active.filterNot { it.isCore }.shuffled(random)
        val fill = (roundSize - required.size).coerceAtLeast(0)
        return (required + others.take(fill)).shuffled(random).map { it.taskId }
    }
}
