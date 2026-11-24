package com.madovygabor.maturimate.features.appOnboarding.presentation.plan

import com.madovygabor.maturimate.testutil.awaitFirst
import com.madovygabor.maturimate.testutil.testDbDispatcher
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.appOnboarding.presentation.nextRoute
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingStep
import com.madovygabor.maturimate.core.presentation.navigation.Route
import com.madovygabor.maturimate.features.userProfile.data.UserProfileRepositoryImpl
import com.madovygabor.maturimate.features.userProfile.domain.CatalogSubject
import com.madovygabor.maturimate.features.userProfile.domain.ExamDateRepository
import com.madovygabor.maturimate.features.userProfile.domain.ExamDates
import com.madovygabor.maturimate.features.userProfile.domain.OfficialExamDate
import com.madovygabor.maturimate.features.userProfile.domain.StudyEstimate
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.plan_reminder
import maturimate.shared.generated.resources.plan_student_week
import maturimate.shared.generated.resources.plan_until_exam
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class PlanScreenTest {

    // the app's setup: one thread runs the database calls (AppModul); with Unconfined a ViewModel on the main thread and the
    // test could use the one connection at the same time
    private val dbDispatcher = testDbDispatcher()

    private val defaultLocale: Locale = Locale.getDefault()
    private val today = LocalDate(2026, 10, 31)

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun repository(): UserProfileRepositoryImpl {
        val database = UserDatabase(
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
        )
        return UserProfileRepositoryImpl(database, LocalUserProvider(database), dbDispatcher)
    }

    @Test
    fun studentPlanCountsTheDaysAndHours() = runBlocking {
        val repository = repository()
        repository.saveRole(UserRole.STUDENT)
        repository.saveGraduationDate(LocalDate(2027, 3, 15))
        repository.saveDailyGoal(10)
        repository.saveSubjects(listOf(UserSubject(1, true), UserSubject(6, true)))
        repository.saveReminder(true, LocalTime(18, 0))

        val state = PlanViewModel(repository, today = today).state.awaitFirst { it?.role != null }!!

        assertEquals(StudyEstimate(LocalDate(2027, 3, 15), 135, 23, 1), state.estimate)
        assertEquals(listOf(CatalogSubject.HUNGARIAN, CatalogSubject.ENGLISH_B2), state.subjects)
        assertEquals(LocalTime(18, 0), state.reminder)
    }

    @Test
    fun theOfficialDateIsUsedLikeOnHomeAndInTheReminder() = runBlocking {
        val repository = repository()
        repository.saveRole(UserRole.STUDENT)
        // the rule's date of 2027: the official date replaces it
        repository.saveGraduationDate(ExamDates.secondWeekWeekday(2027))
        repository.saveDailyGoal(10)
        val official = OfficialExamDate(1, 2027, null, LocalDate(2027, 3, 17), publishedAt = 1)
        val dates = object : ExamDateRepository {
            override val dates = flowOf(listOf(official))
            override suspend fun isAnswered(date: OfficialExamDate) = false
            override suspend fun markAnswered(date: OfficialExamDate) = Unit
        }

        val state = PlanViewModel(repository, dates, today).state.awaitFirst { it?.role != null }!!

        assertEquals(official.date, state.estimate?.examDate)
        assertEquals(137, state.estimate?.daysLeft)
        assertEquals(23, state.estimate?.totalHours)
    }

    @Test
    fun learnerHasNoExamAndAReminderThatIsOffIsNotShown() = runBlocking {
        val repository = repository()
        repository.saveRole(UserRole.LEARNER)
        repository.saveDailyGoal(15)
        repository.saveReminder(false, LocalTime(18, 0))

        val state = PlanViewModel(repository, today = today).state.awaitFirst { it?.role != null }!!

        assertNull(state.estimate)
        assertEquals(15, state.dailyGoalMinutes)
        assertNull(state.reminder)
    }

    @Test
    fun planComesAfterTheReminderAndTheAccountAfterThePlan() {
        assertEquals(Route.AppOnboarding.Plan, nextRoute(OnboardingStep.REMINDER, UserRole.TEACHER))
        assertEquals(Route.AppOnboarding.Account, nextRoute(OnboardingStep.PLAN, UserRole.TEACHER))
        assertEquals(null, nextRoute(OnboardingStep.ACCOUNT, UserRole.STUDENT))
    }

    @Test
    fun studentPlanShowsTheCountdownAndTheReminder() = runComposeUiTest {
        setContent { AppTheme(darkTheme = false) { PlanContent(studentPlan, {}, {}) } }

        onNodeWithText(runBlocking { getString(Res.string.plan_until_exam) }.uppercase()).assertExists()
        onNodeWithText(runBlocking { getString(Res.string.plan_reminder, "18:00") }).assertExists()
        // the hours a week come from the estimate
        onNodeWithText(runBlocking { getPluralString(Res.plurals.plan_student_week, 1, 1) }).assertExists()
    }

    /** Pictures at phone, tablet and desktop size, both themes and languages, into build/screenshots/plan. */
    @Test
    fun rendersAtEverySizeAndTheme() {
        val sizes = mapOf("phone" to (412 to 915), "tablet" to (800 to 1100), "desktop" to (1280 to 800))
        val dir = File("build/screenshots/plan").apply { mkdirs() }
        for (language in listOf("hu", "sk")) {
            Locale.setDefault(Locale.forLanguageTag(language))
            for ((name, size) in sizes) for (dark in listOf(false, true)) {
                runDesktopComposeUiTest(size.first, size.second) {
                    setContent { AppTheme(darkTheme = dark) { PlanContent(studentPlan, {}, {}) } }
                    onNodeWithText(runBlocking { getString(Res.string.plan_until_exam) }.uppercase()).assertExists()
                    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "plan-$language-$name-${if (dark) "dark" else "light"}.png"))
                }
            }
        }
    }

    private val studentPlan = PlanUiState(
        role = UserRole.STUDENT,
        estimate = StudyEstimate(LocalDate(2027, 3, 15), 135, 23, 1),
        dailyGoalMinutes = 10,
        subjects = listOf(
            CatalogSubject.HUNGARIAN,
            CatalogSubject.SLOVAK_HU,
            CatalogSubject.ENGLISH_B1,
        ),
        reminder = LocalTime(18, 0),
    )
}
