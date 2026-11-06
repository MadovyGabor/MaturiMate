package com.madovygabor.maturimate.features.practice.domain

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PracticePlannerTest {

    private val pool = (1L..30L).toList()

    @Test
    fun givesTheAskedNumberOfTasksFromThePool() {
        val round = PracticePlanner.plan(pool, 10, Random(1))

        assertEquals(10, round.size)
        assertTrue(round.all { it in pool })
    }

    @Test
    fun aSmallPoolGivesTheWholePool() {
        val round = PracticePlanner.plan(listOf(5L, 6L, 7L), 50, Random(1))

        assertEquals(setOf(5L, 6L, 7L), round.toSet())
        assertEquals(3, round.size)
    }

    @Test
    fun anExactPoolSizeGivesEveryTaskOnce() {
        assertEquals(pool.toSet(), PracticePlanner.plan(pool, pool.size, Random(2)).toSet())
    }

    @Test
    fun noTaskIsAskedTwice() {
        val round = PracticePlanner.plan(pool + pool + listOf(1L, 1L), 30, Random(3))

        assertEquals(30, round.size)
        assertEquals(round.size, round.toSet().size)
    }

    @Test
    fun zeroNegativeAndEmptyGiveNothing() {
        assertEquals(emptyList(), PracticePlanner.plan(pool, 0))
        assertEquals(emptyList(), PracticePlanner.plan(pool, -5))
        assertEquals(emptyList(), PracticePlanner.plan(emptyList(), 10))
    }

    @Test
    fun theSameSeedGivesTheSameRoundAndAnotherSeedAnotherOne() {
        assertEquals(PracticePlanner.plan(pool, 10, Random(7)), PracticePlanner.plan(pool, 10, Random(7)))
        assertNotEquals(PracticePlanner.plan(pool, 10, Random(7)), PracticePlanner.plan(pool, 10, Random(8)))
    }

    @Test
    fun theRequiredTasksAreAlwaysThereAndTheRestFillsTheRound() {
        repeat(20) { seed ->
            val round = PracticePlanner.plan(required = listOf(3L, 7L), pool = pool, count = 5, random = Random(seed))
            assertEquals(5, round.size)
            assertEquals(round.size, round.toSet().size)
            assertTrue(3L in round && 7L in round, "required missing in $round")
        }
    }

    @Test
    fun moreRequiredThanTheRoundTakesSomeOfThem() {
        val round = PracticePlanner.plan(required = listOf(1L, 2L, 3L, 4L), pool = pool, count = 2, random = Random(1))
        assertEquals(2, round.size)
        assertTrue(round.all { it in 1L..4L })
    }

    @Test
    fun aSmallTopicGivesAllItsTasks() {
        assertEquals(setOf(1L, 2L, 3L), PracticePlanner.plan(required = listOf(2L), pool = listOf(1L, 2L, 3L), count = 30, random = Random(1)).toSet())
        assertEquals(emptyList(), PracticePlanner.plan(required = listOf(2L), pool = listOf(1L), count = 0))
    }

    @Test
    fun theOrderIsRandomToo() {
        // the whole pool asked: the order differs from the order of the pool
        assertNotEquals(pool, PracticePlanner.plan(pool, pool.size, Random(9)))
    }
}
