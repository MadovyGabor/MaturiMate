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

    /** The school's language only, from the settings; the app language stays. */
    suspend fun saveSchoolLanguage(language: TeachingLanguage): OperationResult<Unit, DataError>

    /** The language of the app's labels only, from the settings. */
    suspend fun saveUiLanguage(language: TeachingLanguage): OperationResult<Unit, DataError>
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

    /**
     * Writes the device's time zone to the settings when it differs (one zone for the whole app: the days of the
     * streak, the goal and the history). Does nothing while the user has no settings yet; they get the device's zone
     * when they are created.
     */
    suspend fun refreshTimeZone(): OperationResult<Unit, DataError> = OperationResult.Success(Unit)
}
