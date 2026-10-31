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
        dailyGoalMinutes = 10,
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
                Route.AppOnboarding.ProfileSetup,
            ),
            onboardingBackStack(student),
        )
    }

    @Test
    fun learnerWithSubjectsStopsAtTheLastBuiltStep() {
        val learner = profile(TeachingLanguage.HU, UserRole.LEARNER).copy(subjects = listOf(UserSubject(4, compulsory = false)))
        assertEquals(Route.AppOnboarding.Subjects, onboardingBackStack(learner).last())
    }

    @Test
    fun nextStepFollowsTheRole() {
        assertEquals(Route.AppOnboarding.Subjects, nextRoute(OnboardingStep.ROLE, UserRole.TEACHER))
        assertEquals(Route.AppOnboarding.ProfileSetup, nextRoute(OnboardingStep.SUBJECTS, UserRole.STUDENT))
        // learners and teachers have no exam date; the next step is not built yet, so the onboarding ends
        assertEquals(null, nextRoute(OnboardingStep.SUBJECTS, UserRole.LEARNER))
        assertEquals(null, nextRoute(OnboardingStep.SUBJECTS, UserRole.TEACHER))
    }

    @Test
    fun stepsFollowTheRole() {
        assertEquals(8, onboardingSteps(null).size)
        assertEquals(8, onboardingSteps(UserRole.STUDENT).size)
        assertEquals(OnboardingStep.entries - OnboardingStep.DATE, onboardingSteps(UserRole.LEARNER))
        assertEquals(OnboardingStep.entries - OnboardingStep.DATE - OnboardingStep.GOAL, onboardingSteps(UserRole.TEACHER))
    }
}
