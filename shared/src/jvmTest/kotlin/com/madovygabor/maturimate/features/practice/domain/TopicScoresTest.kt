package com.madovygabor.maturimate.features.practice.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.content.ContentDatabase
import com.madovygabor.maturimate.database.content.Learning_unit
import com.madovygabor.maturimate.database.content.Subject
import com.madovygabor.maturimate.database.content.Task
import com.madovygabor.maturimate.database.content.Task_format
import com.madovygabor.maturimate.database.content.Topic
import com.madovygabor.maturimate.database.content.Unit_task
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.learning.data.ContentRepositoryImpl
import com.madovygabor.maturimate.features.learning.data.ProgressRepositoryImpl
import com.madovygabor.maturimate.features.learning.domain.LearningError
import com.madovygabor.maturimate.features.learning.domain.model.TaskProgress
import com.madovygabor.maturimate.features.learning.domain.model.TaskProgressStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The scores of the topics. The part "Nyelvtan" (10) has the topics 11 to 18, each with five tasks (topic * 10 + 0
 * to 4; task 114 is switched off, so topic 11 has only four active ones plus the extra 115), the hidden topic 19
 * (five tasks) and topic 20 with only three tasks.
 */
@OptIn(ExperimentalTime::class)
class TopicScoresTest {

    private val database = ContentDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { ContentDatabase.Schema.create(it) }
    ).apply {
        subjectQueries.insert(Subject(1, "HU_MJL", "Magyar", "Maďarčina", "HU", null))
        topicQueries.insert(Topic(10, 1, null, "Nyelvtan", null, 1, true))
        taskFormatQueries.insert(Task_format(1, "MC_SINGLE", "MC", "CHOICE", null))
        (11L..20L).forEach { topic ->
            topicQueries.insert(Topic(topic, null, 10, "Téma $topic", null, topic, topic != 19L))
            learningUnitQueries.insert(Learning_unit(topic * 10, topic, "PRACTICE", "Teszt", "MINI", 2, 3, 10, 1, true, 0))
            val tasks = if (topic == 20L) 0L..2L else if (topic == 11L) 0L..5L else 0L..4L
            tasks.forEach { n ->
                val id = topic * 10 + n
                taskQueries.insert(Task(id, topic, 1, "Kérdés", null, 1, null, id != 114L, 0))
                learningUnitQueries.insertUnitTask(Unit_task(topic * 10, id, n + 1, true))
            }
        }
    }

    private val content = ContentRepositoryImpl({ database }, Dispatchers.Unconfined)
    private val userDatabase = UserDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
    )
    private val progress = ProgressRepositoryImpl(userDatabase, LocalUserProvider(userDatabase), Dispatchers.Unconfined)
    private val scores = TopicScores(content, progress)

    private fun <T> OperationResult<T, LearningError>.data(): T = when (this) {
        is OperationResult.Success -> data
        is OperationResult.Error -> fail("expected success, got $error")
    }

    /** The latest answer of each task: right (true) or wrong (false). */
    private fun answered(vararg tasks: Pair<Long, Boolean>) = runBlocking {
        for ((id, right) in tasks) progress.saveTaskProgress(
            TaskProgress(id, if (right) 1 else 0, if (right) 0 else 1, if (right) 1 else 0, Instant.fromEpochMilliseconds(1), null, TaskProgressStatus.LEARNING)
        ).data()
    }

    /** All five tasks of [topic] answered, the first [right] of them right. */
    private fun topic(topic: Long, right: Int) = answered(*(0L..4L).map { n -> topic * 10 + n to (n < right) }.toTypedArray())

    @Test
    fun aTopicGetsAScoreFromFiveTasksSeen() = runBlocking {
        answered(120L to false, 121L to false, 122L to true, 123L to true)
        assertTrue(scores.load().data().isEmpty)

        answered(124L to true)
        val weak = scores.load().data().weak.single()
        assertEquals(12L, weak.topic.id)
        assertEquals(5, weak.seen)
        assertEquals(3, weak.right)
        assertEquals(60, weak.percent)
        assertEquals("Magyar", weak.subject.nameHu)
    }

    @Test
    fun weakIsBelow80AndStrongFrom80() = runBlocking {
        topic(12, right = 3) // 60%
        topic(13, right = 4) // 80%
        topic(14, right = 5) // 100%

        val summary = scores.load().data()
        assertEquals(listOf(12L), summary.weak.map { it.topic.id })
        assertEquals(listOf(14L, 13L), summary.strong.map { it.topic.id })
    }

    @Test
    fun theLatestAnswerCountsSoAMistakeFixedRaisesTheScore() = runBlocking {
        topic(12, right = 2)
        assertEquals(40, scores.load().data().weak.single().percent)

        answered(122L to true, 123L to true)
        assertEquals(80, scores.load().data().strong.single().percent)
    }

    @Test
    fun atMostThreeOfEachTheWeakestAndTheStrongestFirst() = runBlocking {
        topic(12, right = 0)
        topic(13, right = 2)
        topic(15, right = 1)
        topic(16, right = 3)
        topic(14, right = 5)
        topic(17, right = 5)
        topic(18, right = 4)
        answered(110L to true, 111L to true, 112L to true, 113L to true, 115L to false) // topic 11: 4 of 5, 80%

        val summary = scores.load().data()
        assertEquals(listOf(12L, 15L, 13L), summary.weak.map { it.topic.id })
        // 100% twice: the same number seen, so by name
        assertEquals(listOf(14L, 17L, 11L), summary.strong.map { it.topic.id })
    }

    @Test
    fun hiddenTopicsSwitchedOffTasksAndSmallTopicsAreLeftOut() = runBlocking {
        topic(19, right = 0) // hidden
        answered(200L to false, 201L to false, 202L to false) // only three tasks: never five seen
        // topic 11: the switched-off 114 does not count, so four seen
        answered(110L to false, 111L to false, 112L to false, 113L to false, 114L to false)

        assertTrue(scores.load().data().isEmpty)
    }

    @Test
    fun startingOverClearsTheScores() = runBlocking {
        topic(12, right = 1)
        assertTrue(!scores.load().data().isEmpty)

        progress.resetProgress().data()
        assertTrue(scores.load().data().isEmpty)
    }
}
