package com.madovygabor.maturimate.features.learning.domain

import com.madovygabor.maturimate.features.learning.domain.model.AttemptType
import com.madovygabor.maturimate.features.learning.domain.model.AttemptStatus
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
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgress
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgressStatus
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import com.madovygabor.maturimate.features.userProfile.domain.ExamDateRepository
import com.madovygabor.maturimate.features.userProfile.domain.OfficialExamDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class HomeOverviewTest {

    private val contentDatabase = ContentDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { ContentDatabase.Schema.create(it) }
    ).apply {
        subjectQueries.insert(Subject(1, "HU_MJL", "Magyar", "Maďarčina", "HU", null))
        topicQueries.insert(Topic(10, 1, null, "Nyelvtan", null, 1, true))
        learningUnitQueries.insert(Learning_unit(50, 10, "SUMMARY", "Szóképek", null, 3, 5, 10, 1, true, 0))
        learningUnitQueries.insertPage(Unit_page(50, 1, null))
        learningUnitQueries.insertBlock(Page_block(50, 1, 1, "PARAGRAPH", "Szöveg."))
        learningUnitQueries.insert(Learning_unit(51, 10, "PRACTICE", "Mini teszt", "MINI", 2, 3, 10, 2, true, 0))
    }
    private val userDatabase = UserDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
    )
    private val progress = ProgressRepositoryImpl(userDatabase, LocalUserProvider(userDatabase), Dispatchers.Unconfined)
    private val content = ContentRepositoryImpl({ contentDatabase }, Dispatchers.Unconfined)
    private val clock = Instant.parse("2026-11-04T10:00:00Z")
    private val tracker = StudyTracker(progress, { StudySettings(10, TimeZone.UTC) }) { clock }
    private val profile = object : UserProfileRepository by unsupported() {
        override val profile = flowOf<UserProfile?>(
            UserProfile(
                teachingLanguage = TeachingLanguage.HU, role = null, graduationDate = LocalDate(2027, 3, 8), uiLanguage = TeachingLanguage.HU,
                dailyGoalMinutes = 10, timezone = "UTC", onboardingCompletedAt = null, firstName = "Gábor",
            )
        )
    }
    private val overview = HomeOverview(tracker, LearningCourse(content, progress), RecentActivities(progress, content), profile)

    private fun <T> OperationResult<T, LearningError>.data(): T = when (this) {
        is OperationResult.Success -> data
        is OperationResult.Error -> fail("expected success, got $error")
    }

    @Test
    fun aNewStudentSeesTheFirstStepAndTheDaysToTheExam() = runBlocking {
        val home = overview.load().data()
        assertEquals("Gábor", home.firstName)
        assertEquals(124, home.daysToExam)
        assertEquals(50L, home.next!!.unit.id)
        assertEquals(MottoKind.FRESH, home.motto)
        assertEquals(0, home.streak)
        assertEquals(emptyList(), home.recent)
        assertEquals(7, home.week.size)
    }

    @Test
    fun afterALessonTheNextStepIsTheTestAndTheLessonIsAmongTheRecentOnes() = runBlocking {
        progress.saveUnitProgress(UnitProgress(50, UnitProgressStatus.COMPLETED, clock, clock))
        tracker.record(seconds = 300)
        val home = overview.load().data()
        assertEquals(51L, home.next!!.unit.id)
        assertEquals(listOf("Szóképek"), home.recent.map { it.title })
        assertEquals(300, home.goal.secondsStudied)
        assertEquals(MottoKind.GENERAL, home.motto)
    }

    @Test
    fun aPracticeRoundDoesNotMoveTheStepToGoOnWith() = runBlocking {
        // the student tried the test 51 last (not passed, so it comes up again; the lesson 50 before it is still open),
        // then did a practice round: "Folytatás" stays on the test, it does not go back to the start of the path
        val round = (progress.startAttempt(AttemptType.UNIT_ROUND, null, 51, clock) as OperationResult.Success).data
        progress.finishAttempt(round, AttemptStatus.FINISHED, clock, 0, 1)
        val practice = (progress.startAttempt(AttemptType.FREE_PRACTICE, null, null, clock) as OperationResult.Success).data
        progress.finishAttempt(practice, AttemptStatus.FINISHED, Instant.parse("2026-11-04T10:05:00Z"), 1, 1)

        val home = overview.load().data()
        assertEquals(ActivityKind.PRACTICE, home.recent.first().kind)
        assertEquals(51L, home.next!!.unit.id)
    }

    @Test
    fun practiceRoundsFillingTheLastThreeStillLeaveTheStepOfTheLastUnit() = runBlocking {
        // the test 51 tried (the lesson 50 still open), then three practice rounds: the last three activities have no unit
        val round = (progress.startAttempt(AttemptType.UNIT_ROUND, null, 51, clock) as OperationResult.Success).data
        progress.finishAttempt(round, AttemptStatus.FINISHED, clock, 0, 1)
        repeat(3) { minute ->
            val practice = (progress.startAttempt(AttemptType.FREE_PRACTICE, null, null, clock) as OperationResult.Success).data
            progress.finishAttempt(practice, AttemptStatus.FINISHED, Instant.parse("2026-11-04T10:0${minute + 1}:00Z"), 1, 1)
        }

        val home = overview.load().data()
        assertEquals(listOf(ActivityKind.PRACTICE, ActivityKind.PRACTICE, ActivityKind.PRACTICE), home.recent.map { it.kind })
        assertEquals(51L, home.next!!.unit.id)
    }

    @Test
    fun whenEverythingIsDoneThereIsNoNextStep() = runBlocking {
        progress.saveUnitProgress(UnitProgress(50, UnitProgressStatus.COMPLETED, clock, clock))
        progress.saveUnitProgress(UnitProgress(51, UnitProgressStatus.COMPLETED, clock, clock))
        assertNull(overview.load().data().next)
    }

    @Test
    fun theHomeScreenCountsItsDaysInTheZoneOfTheTracker() = runBlocking {
        // the stored zone is far from the device's: the goal, the week and the activity labels must use the stored one
        val auckland = TimeZone.of("Pacific/Auckland")
        val late = Instant.parse("2026-11-04T23:30:00Z")
        val inAuckland = HomeOverview(
            StudyTracker(progress, { StudySettings(10, auckland) }) { late },
            LearningCourse(content, progress), RecentActivities(progress, content), profile,
        )

        val summary = inAuckland.load().data()
        assertEquals(auckland, summary.zone)
        assertEquals(kotlinx.datetime.LocalDate(2026, 11, 5), summary.goal.date)
    }

    @Test
    fun theOfficialExamDateReplacesTheRulesDateInTheCountdown() = runBlocking {
        // the profile holds the rule's date (8 March 2027); the official one is a week later
        val official = OfficialExamDate(1, 2027, null, LocalDate(2027, 3, 15), publishedAt = 1)
        val dates = object : ExamDateRepository {
            override val dates = MutableStateFlow(listOf(official))
            override suspend fun isAnswered(date: OfficialExamDate) = false
            override suspend fun markAnswered(date: OfficialExamDate) {}
        }
        val withOfficial = HomeOverview(tracker, LearningCourse(content, progress), RecentActivities(progress, content), profile, examDates = dates)

        val summary = withOfficial.load().data()
        assertEquals(131, summary.daysToExam)
        // the rule's date is replaced without asking
        assertNull(summary.examOffer)
    }

    private fun motto(reached: Boolean = false, streak: Int = 0, hadStreak: Boolean = false, studied: Boolean = true) =
        HomeOverview.mottoOf(reached, streak, hadStreak, studied)

    @Test
    fun theMottoFollowsTheSituation() {
        assertEquals(MottoKind.REACHED, motto(reached = true, streak = 6, hadStreak = true))
        assertEquals(MottoKind.STREAK, motto(streak = 3, hadStreak = true))
        assertEquals(MottoKind.COMEBACK, motto(streak = 0, hadStreak = true))
        assertEquals(MottoKind.FRESH, motto(studied = false))
        assertEquals(MottoKind.GENERAL, motto(studied = true))
    }
}

/** A stand-in that rejects every call; a test overrides the one call it needs. */
private inline fun <reified T : Any> unsupported(): T =
    java.lang.reflect.Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        throw UnsupportedOperationException(method.name)
    } as T
