package com.madovygabor.maturimate.features.userProfile.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Teaching language of the student's school. It also sets the language of the app. */
enum class TeachingLanguage { HU, SK }

/**
 * What the user wants the app for; it decides which onboarding questions follow.
 * [id] is the role's id in the role catalogue of supabase/migrations.
 */
enum class UserRole(val id: Long) {
    /** Prepares for the maturita. */
    STUDENT(1),

    /** Learns or practises, without an exam date. */
    LEARNER(2),

    /** Looks at or tries the content. */
    TEACHER(3);

    companion object {
        fun fromId(id: Long): UserRole? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Profile and settings of the local user. The onboarding fills the fields one by one,
 * so each may still be empty.
 */
@OptIn(ExperimentalTime::class)
data class UserProfile(
    val teachingLanguage: TeachingLanguage?,
    val role: UserRole?,
    val graduationDate: LocalDate?,
    val uiLanguage: TeachingLanguage,
    val dailyGoalMinutes: Int?,
    val timezone: String,
    /** When the onboarding was finished; empty while it is not. */
    val onboardingCompletedAt: Instant?,
    val subjects: List<UserSubject> = emptyList(),
    /** The daily reminder; [reminderTime] is kept when it is off, to offer it again. */
    val reminderEnabled: Boolean = false,
    val reminderTime: LocalTime? = null,
    /** Empty for a guest, who has no name until sign-up. */
    val firstName: String? = null,
    val lastName: String? = null,
) {
    val isOnboardingCompleted: Boolean get() = onboardingCompletedAt != null
}
