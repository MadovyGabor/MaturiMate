package com.madovygabor.maturimate.features.appOnboarding.domain

import com.madovygabor.maturimate.core.domain.error.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * Repository interface for managing user-specific preferences and onboarding state.
 */
interface UserPreferencesRepository {
    /**
     * A [kotlinx.coroutines.flow.Flow] that emits [com.madovygabor.maturimate.features.appOnboarding.data.UserPreferences] whenever the underlying data changes.
     */
    val preferences: Flow<UserPreferences>

    /**
     * Updates and persists the user's core settings to local storage.
     * Used for both the initial setup and subsequent profile modifications.
     *
     * @param maturitaLanguage The chosen language for the exam (e.g., Hungarian, Slovak).
     * @param maturitaLevel The target difficulty level (e.g., B1, B2).
     * @param isVpuEnabled Toggle for additional educational support features.
     * @param maturitaDate The scheduled date of the graduation exam.
     * @return [OperationResult.Success] if the operation completed,
     * or [OperationResult.Error] containing a [DataError] on failure.
     */
    suspend fun saveUserPreferences(
        maturitaLanguage: MaturitaLanguage,
        maturitaLevel: MaturitaLevel,
        maturitaDate: LocalDate,
        isVpuEnabled: Boolean
    ): OperationResult<Unit, DataError>
}