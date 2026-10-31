package com.madovygabor.maturimate.features.appOnboarding.presentation.plan

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
import com.madovygabor.maturimate.features.userProfile.domain.SubjectLevel
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import maturimate.composeapp.generated.resources.Res
import maturimate.composeapp.generated.resources.plan_reminder
import maturimate.composeapp.generated.resources.plan_until_exam
import org.jetbrains.compose.resources.getString
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class PlanTest {

    private val defaultLocale: Locale = Locale.getDefault()
    private val today = LocalDate(2026, 10, 31)

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun repository(): UserProfileRepositoryImpl {
        val database = UserDatabase(
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
        )
        return UserProfileRepositoryImpl(database, LocalUserProvider(database), Dispatchers.Unconfined)
    }

    @Test
    fun studentPlanCountsTheDaysAndHours() = runBlocking {
        val repository = repository()
        repository.saveRole(UserRole.STUDENT)
        repository.saveGraduationDate(LocalDate(2027, 3, 15))
        repository.saveDailyGoal(10)
        repository.saveSubjects(listOf(UserSubject(1, true), UserSubject(3, true, SubjectLevel.B2)))
        repository.saveReminder(true, LocalTime(18, 0))

        val state = withTimeout(5_000) { PlanViewModel(repository, today).state.first { it?.role != null } }!!

        assertEquals(135, state.daysLeft)
        assertEquals(23, state.hoursUntilExam)
        assertEquals(listOf(PlanSubject(CatalogSubject.HUNGARIAN, null), PlanSubject(CatalogSubject.ENGLISH, SubjectLevel.B2)), state.subjects)
        assertEquals(LocalTime(18, 0), state.reminder)
    }

    @Test
    fun learnerHasNoExamAndAReminderThatIsOffIsNotShown() = runBlocking {
        val repository = repository()
        repository.saveRole(UserRole.LEARNER)
        repository.saveDailyGoal(15)
        repository.saveReminder(false, LocalTime(18, 0))

        val state = withTimeout(5_000) { PlanViewModel(repository, today).state.first { it?.role != null } }!!

        assertNull(state.daysLeft)
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
        daysLeft = 135,
        dailyGoalMinutes = 10,
        subjects = listOf(
            PlanSubject(CatalogSubject.HUNGARIAN, null),
            PlanSubject(CatalogSubject.SLOVAK, null),
            PlanSubject(CatalogSubject.ENGLISH, SubjectLevel.B1),
        ),
        reminder = LocalTime(18, 0),
    )
}
