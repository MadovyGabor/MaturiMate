package com.madovygabor.maturimate.features.userProfile.domain

import kotlinx.datetime.LocalDate
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Teaching language of the student's school. It also sets the language of the app. */
enum class TeachingLanguage { HU, SK }

/**
 * Profile and settings of the local user. The onboarding fills the fields one by one,
 * so each may still be empty.
 */
@OptIn(ExperimentalTime::class)
data class UserProfile(
    val teachingLanguage: TeachingLanguage?,
    val graduationDate: LocalDate?,
    val uiLanguage: TeachingLanguage,
    val dailyGoalMinutes: Int?,
    val timezone: String,
    /** When the onboarding was finished; empty while it is not. */
    val onboardingCompletedAt: Instant?,
) {
    val isOnboardingCompleted: Boolean get() = onboardingCompletedAt != null
}
