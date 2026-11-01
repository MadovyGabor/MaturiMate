package com.madovygabor.maturimate.features.appOnboarding.presentation

import com.madovygabor.maturimate.core.presentation.navigation.Route
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class OnboardingResumeTest {

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

    @Test
    fun nextStepFollowsTheRole() {
        assertEquals(Route.AppOnboarding.Subjects, nextRoute(OnboardingStep.ROLE, UserRole.TEACHER))
        assertEquals(Route.AppOnboarding.Date, nextRoute(OnboardingStep.SUBJECTS, UserRole.STUDENT))
        // learners have no exam date, teachers neither a daily goal
        assertEquals(Route.AppOnboarding.Goal, nextRoute(OnboardingStep.SUBJECTS, UserRole.LEARNER))
        assertEquals(Route.AppOnboarding.Goal, nextRoute(OnboardingStep.DATE, UserRole.STUDENT))
        // teachers have neither: the reminder comes after the subjects
        assertEquals(Route.AppOnboarding.Reminder, nextRoute(OnboardingStep.SUBJECTS, UserRole.TEACHER))
        assertEquals(Route.AppOnboarding.Reminder, nextRoute(OnboardingStep.GOAL, UserRole.LEARNER))
    }

    @Test
    fun stepsFollowTheRole() {
        assertEquals(8, onboardingSteps(null).size)
        assertEquals(8, onboardingSteps(UserRole.STUDENT).size)
        assertEquals(OnboardingStep.entries - OnboardingStep.DATE, onboardingSteps(UserRole.LEARNER))
        assertEquals(OnboardingStep.entries - OnboardingStep.DATE - OnboardingStep.GOAL, onboardingSteps(UserRole.TEACHER))
    }

    @Test
    fun answeredGoalContinuesWithTheReminderEveryTime() {
        val learner = profile(TeachingLanguage.HU, UserRole.LEARNER)
            .copy(subjects = listOf(UserSubject(4, compulsory = false)), dailyGoalMinutes = 15, reminderEnabled = true)
        // the reminder has no answer of its own, so a resumed onboarding asks it again
        assertEquals(Route.AppOnboarding.Reminder, onboardingBackStack(learner).last())
    }

    @Test
    fun everyRoleResumesAtItsFirstUnansweredStep() {
        val base = profile(TeachingLanguage.HU, UserRole.STUDENT).copy(subjects = listOf(UserSubject(1, true)))
        val withDate = base.copy(graduationDate = LocalDate(2027, 3, 15))
        val withGoal = withDate.copy(dailyGoalMinutes = 10)
        assertEquals(Route.AppOnboarding.Date, onboardingBackStack(base).last())
        assertEquals(Route.AppOnboarding.Goal, onboardingBackStack(withDate).last())
        // the reminder, the plan and the account have no stored answer: a resumed onboarding asks the reminder again
        assertEquals(Route.AppOnboarding.Reminder, onboardingBackStack(withGoal).last())
        // Back walks through every earlier screen
        assertEquals(
            listOf(
                Route.AppOnboarding.Welcome, Route.AppOnboarding.Language, Route.AppOnboarding.Role, Route.AppOnboarding.Subjects,
                Route.AppOnboarding.Date, Route.AppOnboarding.Goal, Route.AppOnboarding.Reminder,
            ),
            onboardingBackStack(withGoal),
        )

        val teacher = profile(TeachingLanguage.SK, UserRole.TEACHER).copy(subjects = listOf(UserSubject(4, false)))
        assertEquals(Route.AppOnboarding.Reminder, onboardingBackStack(teacher).last())
        assertTrue(Route.AppOnboarding.Date !in onboardingBackStack(teacher) && Route.AppOnboarding.Goal !in onboardingBackStack(teacher))
    }
}
