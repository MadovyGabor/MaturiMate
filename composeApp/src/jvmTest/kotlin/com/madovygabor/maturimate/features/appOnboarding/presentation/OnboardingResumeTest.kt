package com.madovygabor.maturimate.features.appOnboarding.presentation

import com.madovygabor.maturimate.core.presentation.navigation.Route
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class OnboardingResumeTest {

    private fun profile(language: TeachingLanguage?) = UserProfile(
        teachingLanguage = language,
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
    fun savedLanguageContinuesAfterTheLanguageStepWithBackThroughIt() {
        assertEquals(
            listOf(Route.AppOnboarding.Welcome, Route.AppOnboarding.Language, Route.AppOnboarding.ProfileSetup),
            onboardingBackStack(profile(TeachingLanguage.SK)),
        )
    }
}
