package com.madovygabor.maturimate.features.appOnboarding.presentation

import com.madovygabor.maturimate.core.presentation.navigation.Route
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import kotlin.test.Test
import kotlin.test.assertEquals
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
    }

    @Test
    fun stepsFollowTheRole() {
        assertEquals(8, onboardingSteps(null).size)
        assertEquals(8, onboardingSteps(UserRole.STUDENT).size)
        assertEquals(OnboardingStep.entries - OnboardingStep.DATE, onboardingSteps(UserRole.LEARNER))
        assertEquals(OnboardingStep.entries - OnboardingStep.DATE - OnboardingStep.GOAL, onboardingSteps(UserRole.TEACHER))
    }
}
