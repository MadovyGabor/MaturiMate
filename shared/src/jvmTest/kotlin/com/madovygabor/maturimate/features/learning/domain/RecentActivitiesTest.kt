package com.madovygabor.maturimate.features.learning.domain

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

    @Test
    fun abandonedRoundsAreLeftOutAndTheListIsLimited() = runBlocking {
        round(1_000, 2_000, earned = 0, status = AttemptStatus.ABANDONED)
        round(3_000, 4_000, earned = 5)
        round(5_000, 6_000, earned = 5)

        assertEquals(1, (recent.latest(limit = 1) as OperationResult.Success).data.size)
        assertEquals(2, (recent.latest() as OperationResult.Success).data.size)
    }
}
