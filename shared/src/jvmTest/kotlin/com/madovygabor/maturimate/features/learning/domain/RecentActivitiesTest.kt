package com.madovygabor.maturimate.features.learning.domain

import com.madovygabor.maturimate.features.learning.domain.model.AttemptAnswer
import com.madovygabor.maturimate.database.content.Task_format
import com.madovygabor.maturimate.database.content.Task
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.content.ContentDatabase
import com.madovygabor.maturimate.database.content.Learning_unit
import com.madovygabor.maturimate.database.content.Page_block
import com.madovygabor.maturimate.database.content.Subject
import com.madovygabor.maturimate.database.content.Topic
import com.madovygabor.maturimate.database.content.Unit_page
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.learning.data.ContentRepositoryImpl
import com.madovygabor.maturimate.features.learning.data.ProgressRepositoryImpl
import com.madovygabor.maturimate.features.learning.domain.model.AttemptStatus
import com.madovygabor.maturimate.features.learning.domain.model.AttemptType
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgress
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgressStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class RecentActivitiesTest {

    private val contentDatabase = ContentDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { ContentDatabase.Schema.create(it) }
    ).apply {
        subjectQueries.insert(Subject(1, "HU_MJL", "Magyar", "Maďarčina", "HU", null))
        topicQueries.insert(Topic(10, 1, null, "Nyelvtan", null, 1, true))
        learningUnitQueries.insert(Learning_unit(50, 10, "SUMMARY", "Szóképek", null, 3, 5, 10, 1, true, 0))
        learningUnitQueries.insertPage(Unit_page(50, 1, null))
        learningUnitQueries.insertBlock(Page_block(50, 1, 1, "PARAGRAPH", "Szöveg."))
        learningUnitQueries.insert(Learning_unit(51, 10, "PRACTICE", "Teszt", "MINI", 2, 3, 10, 2, true, 0))
        // tasks for practice rounds: 100 under "Szóképek" (Magyar), 200 under "Algebra" (Matematika)
        subjectQueries.insert(Subject(4, "MAT", "Matematika", "Matematika", "BOTH", null))
        topicQueries.insert(Topic(11, null, 10, "Szóképek", null, 1, true))
        topicQueries.insert(Topic(20, 4, null, "Algebra", null, 1, true))
        taskFormatQueries.insert(Task_format(1, "MC_SINGLE", "MC", "CHOICE", null))
        taskQueries.insert(Task(100, 11, 1, "Kérdés", null, 1, null, true, 0))
        taskQueries.insert(Task(200, 20, 1, "Kérdés", null, 1, null, true, 0))
    }
    private val userDatabase = UserDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
    )
    private val progress = ProgressRepositoryImpl(userDatabase, LocalUserProvider(userDatabase), Dispatchers.Unconfined)
    private val recent = RecentActivities(progress, ContentRepositoryImpl({ contentDatabase }, Dispatchers.Unconfined))

    private fun at(millis: Long) = Instant.fromEpochMilliseconds(millis)

    private suspend fun round(startedAt: Long, finishedAt: Long, earned: Int, status: AttemptStatus = AttemptStatus.FINISHED) {
        val id = (progress.startAttempt(AttemptType.UNIT_ROUND, null, 51, at(startedAt)) as OperationResult.Success).data
        progress.finishAttempt(id, status, at(finishedAt), earned, 5)
    }

    @Test
    fun lessonsAndFinishedRoundsComeNewestFirst() = runBlocking {
        progress.saveUnitProgress(UnitProgress(50, UnitProgressStatus.COMPLETED, at(1_000), at(2_000)))
        round(3_000, 4_000, earned = 3)
        round(5_000, 6_000, earned = 5)

        val list = (recent.latest() as OperationResult.Success).data
        assertEquals(listOf(6_000L, 4_000L, 2_000L), list.map { it.at.toEpochMilliseconds() })
        assertEquals(listOf(true, false, null), list.map { it.passed })
        assertEquals(listOf(true, true, false), list.map { it.isTest })
        assertEquals("Szóképek", list.last().title)
    }

    private suspend fun practice(type: AttemptType, finishedAt: Long, tasks: List<Long>, status: AttemptStatus = AttemptStatus.FINISHED): Long {
        val id = (progress.startAttempt(type, null, null, at(finishedAt - 500)) as OperationResult.Success).data
        tasks.forEachIndexed { index, task ->
            progress.recordAnswer(AttemptAnswer(id, task, index == 0, if (index == 0) 1 else 0, 1, at(finishedAt - 100), "1=A"))
        }
        progress.finishAttempt(id, status, at(finishedAt), 1, tasks.size)
        return id
    }

    @Test
    fun aFinishedPracticeRoundIsListedWithItsKindPointsAndSubject() = runBlocking {
        progress.saveUnitProgress(UnitProgress(50, UnitProgressStatus.COMPLETED, at(1_000), at(2_000)))
        practice(AttemptType.FREE_PRACTICE, finishedAt = 5_000, tasks = listOf(100))

        val list = (recent.latest() as OperationResult.Success).data
        assertEquals(listOf(ActivityKind.PRACTICE, ActivityKind.LESSON), list.map { it.kind })
        val round = list.first()
        assertEquals(null, round.unitId)
        assertEquals(null, round.passed)
        assertEquals(1, round.earnedPoints)
        assertEquals(1, round.maxPoints)
        assertEquals(listOf(1L), round.subjectIds)
        assertEquals(false, round.isTest)
    }

    @Test
    fun aPracticeRoundOverTwoSubjectsNamesBoth() = runBlocking {
        practice(AttemptType.FREE_PRACTICE, finishedAt = 5_000, tasks = listOf(100, 200))

        assertEquals(listOf(1L, 4L), (recent.latest() as OperationResult.Success).data.single().subjectIds)
    }

    @Test
    fun aMistakePracticeRoundHasItsOwnKindAndAnAbandonedOneIsLeftOut() = runBlocking {
        practice(AttemptType.MISTAKE_PRACTICE, finishedAt = 5_000, tasks = listOf(100))
        practice(AttemptType.FREE_PRACTICE, finishedAt = 6_000, tasks = listOf(100), status = AttemptStatus.ABANDONED)

        assertEquals(listOf(ActivityKind.MISTAKE_PRACTICE), (recent.latest() as OperationResult.Success).data.map { it.kind })
    }

    @Test
    fun abandonedRoundsAreLeftOutAndTheListIsLimited() = runBlocking {
        round(1_000, 2_000, earned = 0, status = AttemptStatus.ABANDONED)
        round(3_000, 4_000, earned = 5)
        round(5_000, 6_000, earned = 5)

        assertEquals(1, (recent.latest(limit = 1) as OperationResult.Success).data.size)
        assertEquals(2, (recent.latest() as OperationResult.Success).data.size)
    }
}
