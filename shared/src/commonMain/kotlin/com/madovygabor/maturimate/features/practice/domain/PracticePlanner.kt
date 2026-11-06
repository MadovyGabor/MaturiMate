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

    /**
     * Up to [count] different ids: every one of [required] first (at random when there are more than [count]), then
     * random ones of [pool] to fill the round; the whole round in random order. The practice of a weak topic asks all
     * its mistakes and fills up with its other tasks.
     */
    fun plan(required: List<Long>, pool: List<Long>, count: Int, random: Random = Random.Default): List<Long> {
        if (count <= 0) return emptyList()
        val must = required.distinct().shuffled(random).take(count)
        val rest = pool.distinct().filterNot { it in must }.shuffled(random).take(count - must.size)
        return (must + rest).shuffled(random)
    }
}
