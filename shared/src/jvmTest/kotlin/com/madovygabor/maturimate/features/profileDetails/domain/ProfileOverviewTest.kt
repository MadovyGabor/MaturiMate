package com.madovygabor.maturimate.features.profileDetails.domain

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
import com.madovygabor.maturimate.features.learning.domain.LearningCourse
import com.madovygabor.maturimate.features.learning.domain.LearningError
import com.madovygabor.maturimate.features.learning.domain.RecentActivities
import com.madovygabor.maturimate.features.learning.domain.StudySettings
import com.madovygabor.maturimate.features.learning.domain.StudyTotals
import com.madovygabor.maturimate.features.learning.domain.StudyTracker
import com.madovygabor.maturimate.features.learning.domain.model.AttemptStatus
import com.madovygabor.maturimate.features.learning.domain.model.AttemptType
import com.madovygabor.maturimate.features.learning.domain.model.LessonTestCount
import com.madovygabor.maturimate.features.learning.domain.model.StepCount
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgress
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgressStatus
import com.madovygabor.maturimate.features.userProfile.domain.CatalogSubject
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class ProfileOverviewTest {

    // Hungarian with two big parts: Nyelvtan (a lesson and a mini test) and Irodalom (one lesson)
    private val contentDatabase = ContentDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { ContentDatabase.Schema.create(it) }
    ).apply {
        subjectQueries.insert(Subject(1, "HU_MJL", "Magyar", "Maďarčina", "HU", null))
        topicQueries.insert(Topic(10, 1, null, "Nyelvtan", null, 1, true))
        topicQueries.insert(Topic(20, 1, null, "Irodalom", null, 2, true))
        lesson(50, 10, "Szóképek", 1)
        learningUnitQueries.insert(Learning_unit(51, 10, "PRACTICE", "Mini teszt", "MINI", 2, 3, 10, 2, true, 0))
        lesson(60, 20, "Az ember tragédiája", 1)
    }

    private fun ContentDatabase.lesson(id: Long, topic: Long, title: String, order: Long) {
        learningUnitQueries.insert(Learning_unit(id, topic, "SUMMARY", title, null, 3, 5, 10, order, true, 0))
        learningUnitQueries.insertPage(Unit_page(id, 1, null))
        learningUnitQueries.insertBlock(Page_block(id, 1, 1, "PARAGRAPH", "Szöveg."))
    }

    private val userDatabase = UserDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
    )
    private val progress = ProgressRepositoryImpl(userDatabase, LocalUserProvider(userDatabase), Dispatchers.Unconfined)
    private val content = ContentRepositoryImpl({ contentDatabase }, Dispatchers.Unconfined)
    private var clock = Instant.parse("2026-11-04T10:00:00Z")
    private val tracker = StudyTracker(progress, { StudySettings(10, TimeZone.UTC) }) { clock }

    private fun profileOf(firstName: String?) = object : UserProfileRepository by unsupported() {
        override val profile = flowOf<UserProfile?>(
            UserProfile(
                teachingLanguage = TeachingLanguage.HU, role = null, graduationDate = null, uiLanguage = TeachingLanguage.HU,
                dailyGoalMinutes = 10, timezone = "UTC", onboardingCompletedAt = null, firstName = firstName,
                // Mathematics has no content yet; it comes after Hungarian, as on the subjects tab
                subjects = listOf(UserSubject(CatalogSubject.MATHEMATICS.id, false), UserSubject(CatalogSubject.HUNGARIAN.id, true)),
            )
        )
    }

    private fun overview(firstName: String? = "Gábor") =
        ProfileOverview(tracker, LearningCourse(content, progress), RecentActivities(progress, content), profileOf(firstName))

    private fun <T> OperationResult<T, LearningError>.data(): T = when (this) {
        is OperationResult.Success -> data
        is OperationResult.Error -> fail("expected success, got $error")
    }

    @Test
    fun aNewStudentHasZerosAndEmptyLists() = runBlocking {
        val profile = overview().load().data()
        assertEquals("Gábor", profile.firstName)
        assertEquals(0, profile.streak)
        assertEquals(StudyTotals(0, 0), profile.totals)
        assertEquals(emptyList(), profile.recent)
        assertEquals(LocalDate(2026, 11, 4), profile.today)
        assertEquals(listOf(CatalogSubject.HUNGARIAN, CatalogSubject.MATHEMATICS), profile.subjects.map { it.subject })
        assertEquals(LessonTestCount(StepCount(0, 2), StepCount(0, 1)), profile.subjects[0].progress!!.count)
        // no content for Mathematics: the card says "Hamarosan"
        assertNull(profile.subjects[1].progress)
    }

    @Test
    fun aGuestHasNoName() = runBlocking {
        assertNull(overview(firstName = null).load().data().firstName)
    }

    @Test
    fun lessonsAndTestsAreCountedApartPerSubjectAndPerPart() = runBlocking {
        progress.saveUnitProgress(UnitProgress(50, UnitProgressStatus.COMPLETED, clock, clock))
        progress.saveUnitProgress(UnitProgress(51, UnitProgressStatus.COMPLETED, clock, clock))
        val hungarian = overview().load().data().subjects[0].progress!!
        assertEquals(LessonTestCount(StepCount(1, 2), StepCount(1, 1)), hungarian.count)
        assertEquals(StepCount(2, 3), hungarian.count.steps)
        assertEquals(listOf("Nyelvtan", "Irodalom"), hungarian.parts.map { it.topic.title })
        assertEquals(LessonTestCount(StepCount(1, 1), StepCount(1, 1)), hungarian.parts[0].count)
        assertEquals(LessonTestCount(StepCount(0, 1), StepCount(0, 0)), hungarian.parts[1].count)
    }

    @Test
    fun theTotalsAddUpEveryDay() = runBlocking {
        tracker.record(seconds = 600, tasks = 5)
        clock += 1.days
        tracker.record(seconds = 120, tasks = 3)
        val profile = overview().load().data()
        assertEquals(StudyTotals(720, 8), profile.totals)
        // two days in a row with the goal reached on the first only: the streak is one
        assertEquals(1, profile.streak)
    }

    @Test
    fun theLastThreeActivitiesComeWithThePointsOfATest() = runBlocking {
        progress.saveUnitProgress(UnitProgress(50, UnitProgressStatus.COMPLETED, clock, clock))
        progress.saveUnitProgress(UnitProgress(60, UnitProgressStatus.COMPLETED, clock + 1.hours, clock + 1.hours))
        round(earned = 3, max = 5, at = clock + 2.hours)
        round(earned = 5, max = 5, at = clock + 3.hours)
        val recent = overview().load().data().recent
        assertEquals(3, recent.size)
        assertEquals(listOf(true, true, false), recent.map { it.isTest })
        assertEquals(5 to 5, recent[0].earnedPoints to recent[0].maxPoints)
        assertEquals(true, recent[0].passed)
        assertEquals(3 to 5, recent[1].earnedPoints to recent[1].maxPoints)
        assertEquals(false, recent[1].passed)
    }

    private suspend fun round(earned: Int, max: Int, at: Instant) {
        val id = progress.startAttempt(AttemptType.UNIT_ROUND, null, 51, at).data()
        progress.finishAttempt(id, AttemptStatus.FINISHED, at, earned, max).data()
    }
}

/** A stand-in that rejects every call; a test overrides the one call it needs. */
private inline fun <reified T : Any> unsupported(): T =
    java.lang.reflect.Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        throw UnsupportedOperationException(method.name)
    } as T
