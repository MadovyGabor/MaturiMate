package com.madovygabor.maturimate.features.userProfile.domain

import com.madovygabor.maturimate.core.domain.error.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Profile and settings of the local user (a guest until sign-up).
 *
 * Each save writes one field, so the onboarding can save after every screen.
 */
@OptIn(ExperimentalTime::class)
interface UserProfileRepository {
    /** The profile, again after every change; null until the first answer is saved. */
    val profile: Flow<UserProfile?>

    /** Also sets the language of the app. */
    suspend fun saveTeachingLanguage(language: TeachingLanguage): OperationResult<Unit, DataError>
    /** Replaces the role chosen before. */
    suspend fun saveRole(role: UserRole): OperationResult<Unit, DataError>
    /** Replaces the subjects chosen before. */
    suspend fun saveSubjects(subjects: List<UserSubject>): OperationResult<Unit, DataError>
    suspend fun saveGraduationDate(date: LocalDate): OperationResult<Unit, DataError>
    /** Turns the daily reminder on at [time], or off. */
    suspend fun saveReminder(enabled: Boolean, time: LocalTime?): OperationResult<Unit, DataError>
    suspend fun saveDailyGoal(minutes: Int): OperationResult<Unit, DataError>
    /** The account holder's name, saved at sign-up. */
    suspend fun saveNames(firstName: String, lastName: String): OperationResult<Unit, DataError>
    suspend fun completeOnboarding(at: Instant): OperationResult<Unit, DataError>
}
