package com.madovygabor.maturimate.features.practice.domain

import kotlin.random.Random

/**
 * Chooses the tasks of one practice round from a pool of task ids.
 *
 * It is plain chance: no task is preferred (decided on 2026-11-05), so a round is as likely to repeat what the
 * student knows as what they do not. The mistake practice uses the same planner with its own pool.
 */
object PracticePlanner {

    /**
     * Up to [count] different ids from [pool], in random order. A pool smaller than [count] gives the whole pool
     * (shuffled); a [count] of zero or less, or an empty pool, gives nothing. An id that is in the pool twice is
     * chosen at most once.
     */
    fun plan(pool: List<Long>, count: Int, random: Random = Random.Default): List<Long> {
        if (count <= 0) return emptyList()
        return pool.distinct().shuffled(random).take(count)
    }
}
