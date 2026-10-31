package com.madovygabor.maturimate.features.appOnboarding.presentation

import com.madovygabor.maturimate.core.presentation.navigation.Route
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
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
    fun studentContinuesAfterTheRoleWithBackThroughTheEarlierSteps() {
        assertEquals(
            listOf(
                Route.AppOnboarding.Welcome,
                Route.AppOnboarding.Language,
                Route.AppOnboarding.Role,
                Route.AppOnboarding.ProfileSetup,
            ),
            onboardingBackStack(profile(TeachingLanguage.HU, UserRole.STUDENT)),
        )
    }

    @Test
    fun learnerAndTeacherStopAtTheRoleForNow() {
        for (role in listOf(UserRole.LEARNER, UserRole.TEACHER)) {
            assertEquals(Route.AppOnboarding.Role, onboardingBackStack(profile(TeachingLanguage.HU, role)).last(), role.name)
        }
    }

    @Test
    fun stepsFollowTheRole() {
        assertEquals(8, onboardingSteps(null).size)
        assertEquals(8, onboardingSteps(UserRole.STUDENT).size)
        assertEquals(OnboardingStep.entries - OnboardingStep.DATE, onboardingSteps(UserRole.LEARNER))
        assertEquals(OnboardingStep.entries - OnboardingStep.DATE - OnboardingStep.GOAL, onboardingSteps(UserRole.TEACHER))
    }
}
