package com.madovygabor.maturimate.features.appOnboarding.presentation

import com.madovygabor.maturimate.core.presentation.navigation.Route
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.userProfile.domain.DataError
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import com.madovygabor.maturimate.testutil.awaitFirst
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.assertNotNull
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class OnboardingNavigationTest {

    private fun profile(language: TeachingLanguage?, role: UserRole? = null) = UserProfile(
        teachingLanguage = language,
        role = role,
        graduationDate = null,
        uiLanguage = language ?: TeachingLanguage.SK,
        dailyGoalMinutes = null,
        timezone = "Europe/Bratislava",
        onboardingCompletedAt = null,
    )

    @Test
    fun newUserStartsOnTheWelcomeScreen() {
        assertEquals(listOf(Route.AppOnboarding.Welcome), onboardingBackStack(null))
    }

    @Test
    fun profileWithoutALanguageStartsOnTheWelcomeScreen() {
        assertEquals(listOf(Route.AppOnboarding.Welcome), onboardingBackStack(profile(language = null)))
    }

    @Test
    fun savedLanguageContinuesWithTheRole() {
        assertEquals(
            listOf(Route.AppOnboarding.Welcome, Route.AppOnboarding.Language, Route.AppOnboarding.Role),
            onboardingBackStack(profile(TeachingLanguage.SK)),
        )
    }

    @Test
    fun savedRoleContinuesWithTheSubjects() {
        assertEquals(
            listOf(Route.AppOnboarding.Welcome, Route.AppOnboarding.Language, Route.AppOnboarding.Role, Route.AppOnboarding.Subjects),
            onboardingBackStack(profile(TeachingLanguage.HU, UserRole.LEARNER)),
        )
    }

    @Test
    fun studentWithSubjectsContinuesWithTheExamDate() {
        val student = profile(TeachingLanguage.HU, UserRole.STUDENT).copy(subjects = listOf(UserSubject(1, compulsory = true)))
        assertEquals(
            listOf(
                Route.AppOnboarding.Welcome,
                Route.AppOnboarding.Language,
                Route.AppOnboarding.Role,
                Route.AppOnboarding.Subjects,
                Route.AppOnboarding.Date,
            ),
            onboardingBackStack(student),
        )
    }

    @Test
    fun learnerWithSubjectsContinuesWithTheGoal() {
        val learner = profile(TeachingLanguage.HU, UserRole.LEARNER).copy(subjects = listOf(UserSubject(4, compulsory = false)))
        assertEquals(Route.AppOnboarding.Goal, onboardingBackStack(learner).last())
    }

    // A school-language change removes the subjects, so the resume asks them again.
    @Test
    fun aProfileWhoseLanguageChangedHasNoSubjectsAndResumesAtTheSubjects() {
        val changed = profile(TeachingLanguage.HU, UserRole.STUDENT).copy(subjects = emptyList(), graduationDate = LocalDate(2027, 3, 15), dailyGoalMinutes = 10)
        assertEquals(Route.AppOnboarding.Subjects, onboardingBackStack(changed).last())
    }

    @Test
    fun nextStepFollowsTheRole() {
        assertEquals(Route.AppOnboarding.Subjects, nextRoute(OnboardingStep.ROLE, UserRole.TEACHER))
        assertEquals(Route.AppOnboarding.Date, nextRoute(OnboardingStep.SUBJECTS, UserRole.STUDENT))
        // learners have no exam date, teachers neither a daily goal
        assertEquals(Route.AppOnboarding.Goal, nextRoute(OnboardingStep.SUBJECTS, UserRole.LEARNER))
        assertEquals(Route.AppOnboarding.Goal, nextRoute(OnboardingStep.DATE, UserRole.STUDENT))
        // teachers have neither: the reminder comes after the subjects
        assertEquals(Route.AppOnboarding.Reminder, nextRoute(OnboardingStep.SUBJECTS, UserRole.TEACHER, reminder = true))
        assertEquals(Route.AppOnboarding.Reminder, nextRoute(OnboardingStep.GOAL, UserRole.LEARNER, reminder = true))
    }

    @Test
    fun stepsFollowTheRole() {
        assertEquals(8, onboardingSteps(null, reminder = true).size)
        assertEquals(8, onboardingSteps(UserRole.STUDENT, reminder = true).size)
        assertEquals(OnboardingStep.entries - OnboardingStep.DATE, onboardingSteps(UserRole.LEARNER, reminder = true))
        assertEquals(OnboardingStep.entries - OnboardingStep.DATE - OnboardingStep.GOAL, onboardingSteps(UserRole.TEACHER, reminder = true))
    }

    // The desktop app has no reminder: the step is left out, so the header counts one step less and nothing saves a
    // reminder that never comes.
    @Test
    fun theDesktopSkipsTheReminderStep() {
        assertEquals(7, onboardingSteps(null, reminder = false).size)
        assertEquals(OnboardingStep.entries - OnboardingStep.REMINDER, onboardingSteps(UserRole.STUDENT, reminder = false))
        assertEquals(OnboardingStep.entries - OnboardingStep.DATE - OnboardingStep.REMINDER, onboardingSteps(UserRole.LEARNER, reminder = false))
        assertEquals(Route.AppOnboarding.Plan, nextRoute(OnboardingStep.GOAL, UserRole.STUDENT, reminder = false))
        assertEquals(Route.AppOnboarding.Plan, nextRoute(OnboardingStep.SUBJECTS, UserRole.TEACHER, reminder = false))
        // a step the platform hides still continues with the next shown one
        assertEquals(Route.AppOnboarding.Plan, nextRoute(OnboardingStep.REMINDER, UserRole.TEACHER, reminder = false))
        // this test runs on the JVM, whose default is the desktop's
        assertEquals(onboardingSteps(null, reminder = false), onboardingSteps(null))
    }

    @Test
    fun theDesktopResumesAtThePlanAfterTheGoal() {
        val withGoal = profile(TeachingLanguage.HU, UserRole.STUDENT)
            .copy(subjects = listOf(UserSubject(1, true)), graduationDate = LocalDate(2027, 3, 15), dailyGoalMinutes = 10)
        assertEquals(Route.AppOnboarding.Plan, onboardingBackStack(withGoal).last())
        assertTrue(Route.AppOnboarding.Reminder !in onboardingBackStack(withGoal))
    }

    @Test
    fun answeredGoalContinuesWithTheReminderEveryTime() {
        val learner = profile(TeachingLanguage.HU, UserRole.LEARNER)
            .copy(subjects = listOf(UserSubject(4, compulsory = false)), dailyGoalMinutes = 15, reminderEnabled = true)
        // the reminder has no answer of its own, so a resumed onboarding asks it again
        assertEquals(Route.AppOnboarding.Plan, onboardingBackStack(learner).last())
    }

    @Test
    fun everyRoleResumesAtItsFirstUnansweredStep() {
        val base = profile(TeachingLanguage.HU, UserRole.STUDENT).copy(subjects = listOf(UserSubject(1, true)))
        val withDate = base.copy(graduationDate = LocalDate(2027, 3, 15))
        val withGoal = withDate.copy(dailyGoalMinutes = 10)
        assertEquals(Route.AppOnboarding.Date, onboardingBackStack(base).last())
        assertEquals(Route.AppOnboarding.Goal, onboardingBackStack(withDate).last())
        // the plan and the account (and the reminder, on a phone) have no stored answer: a resumed onboarding asks the plan again
        assertEquals(Route.AppOnboarding.Plan, onboardingBackStack(withGoal).last())
        // Back walks through every earlier screen
        assertEquals(
            listOf(
                Route.AppOnboarding.Welcome, Route.AppOnboarding.Language, Route.AppOnboarding.Role, Route.AppOnboarding.Subjects,
                Route.AppOnboarding.Date, Route.AppOnboarding.Goal, Route.AppOnboarding.Plan,
            ),
            onboardingBackStack(withGoal),
        )

        val teacher = profile(TeachingLanguage.SK, UserRole.TEACHER).copy(subjects = listOf(UserSubject(4, false)))
        assertEquals(Route.AppOnboarding.Plan, onboardingBackStack(teacher).last())
        assertTrue(Route.AppOnboarding.Date !in onboardingBackStack(teacher) && Route.AppOnboarding.Goal !in onboardingBackStack(teacher))
    }

    // After a new language the app is rebuilt at once, but the stack may still come from the
    // profile before the change; the onboarding then started on the welcome screen again.
    @Test
    fun aStackFromBeforeTheLanguageChangeIsNotUsed() {
        val beforeLanguage = OnboardingResumeState(listOf(Route.AppOnboarding.Welcome), language = null)
        val afterLanguage = OnboardingResumeState(onboardingBackStack(profile(TeachingLanguage.HU)), language = TeachingLanguage.HU)
        val unreadable = OnboardingResumeState(listOf(Route.AppOnboarding.Welcome), language = null, fallback = true)

        assertTrue(!beforeLanguage.matches(TeachingLanguage.HU))
        assertTrue(afterLanguage.matches(TeachingLanguage.HU))
        assertTrue(unreadable.matches(TeachingLanguage.HU))
    }

    @Test
    fun aFailedFinishShowsASnackbar() = runBlocking<Unit> {
        val snackbar = SnackbarController()
        val navigation = OnboardingNavigationViewModel(CannotFinish(), snackbar)
        navigation.finish()

        assertNotNull(snackbar.events.awaitFirst())
    }

    /** Saving the end of the onboarding fails, the disk is full. */
    private class CannotFinish : UserProfileRepository {
        override val profile: Flow<UserProfile?> = flowOf(null)
        private val ok: OperationResult<Unit, DataError> = OperationResult.Success(Unit)
        override suspend fun saveTeachingLanguage(language: TeachingLanguage) = ok
        override suspend fun saveSchoolLanguage(language: TeachingLanguage) = ok
        override suspend fun saveUiLanguage(language: TeachingLanguage) = ok
        override suspend fun saveRole(role: UserRole) = ok
        override suspend fun saveSubjects(subjects: List<UserSubject>) = ok
        override suspend fun saveGraduationDate(date: LocalDate) = ok
        override suspend fun saveReminder(enabled: Boolean, time: LocalTime?) = ok
        override suspend fun saveDailyGoal(minutes: Int) = ok
        override suspend fun saveNames(firstName: String, lastName: String) = ok
        override suspend fun completeOnboarding(at: Instant): OperationResult<Unit, DataError> = OperationResult.Error(DataError.STORAGE_FULL)
    }
}
