package com.madovygabor.maturimate.features.learning.domain

import com.madovygabor.maturimate.features.learning.domain.model.UnitTask
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoundPlannerTest {

    private fun task(id: Long, core: Boolean = true, active: Boolean = true) = UnitTask(id, sortOrder = id.toInt(), isCore = core, isActive = active)

    @Test
    fun requiredTasksAreAlwaysInTheRound() {
        val tasks = listOf(task(1), task(2), task(3, core = false), task(4, core = false), task(5, core = false))
        repeat(50) { seed ->
            val round = RoundPlanner.plan(tasks, roundSize = 4, random = Random(seed))
            assertEquals(4, round.size)
            assertTrue(1L in round && 2L in round, "required tasks missing in $round")
        }
    }

    @Test
    fun theRestOfTheRoundIsFilledWithRandomTasks() {
        val tasks = listOf(task(1), task(2, core = false), task(3, core = false), task(4, core = false))
        val fills = (0 until 60).map { seed -> RoundPlanner.plan(tasks, roundSize = 2, random = Random(seed)).single { it != 1L } }.toSet()
        // over many rounds every optional task is drawn at least once
        assertEquals(setOf(2L, 3L, 4L), fills)
    }

    @Test
    fun withoutRequiredTasksARoundIsARandomPartOfThePool() {
        val tasks = (1L..20L).map { task(it, core = false) }
        val round = RoundPlanner.plan(tasks, roundSize = 10, random = Random(7))
        assertEquals(10, round.size)
        assertEquals(10, round.toSet().size)
        assertTrue(round.all { it in 1L..20L })
    }

    @Test
    fun theOrderChangesFromRoundToRound() {
        val tasks = (1L..8L).map { task(it) }
        val orders = (0 until 20).map { RoundPlanner.plan(tasks, roundSize = 8, random = Random(it)) }.toSet()
        assertTrue(orders.size > 1)
        assertTrue(orders.all { it.sorted() == (1L..8L).toList() })
    }

    @Test
    fun aSwitchedOffTaskIsNeverChosen() {
        val tasks = listOf(task(1), task(2, active = false), task(3, core = false, active = false), task(4, core = false))
        repeat(30) { seed ->
            val round = RoundPlanner.plan(tasks, roundSize = 3, random = Random(seed))
            assertEquals(setOf(1L, 4L), round.toSet())
        }
    }

    @Test
    fun aSmallPoolGivesASmallerRound() {
        val round = RoundPlanner.plan(listOf(task(1), task(2, core = false)), roundSize = 5, random = Random(1))
        assertEquals(setOf(1L, 2L), round.toSet())
    }

    @Test
    fun theSameSeedGivesTheSameRound() {
        val tasks = (1L..10L).map { task(it, core = it <= 3) }
        assertEquals(
            RoundPlanner.plan(tasks, roundSize = 6, random = Random(42)),
            RoundPlanner.plan(tasks, roundSize = 6, random = Random(42)),
        )
    }
}
