package com.madovygabor.maturimate.features.appOnboarding.data

import com.madovygabor.maturimate.features.appOnboarding.domain.DataError
import com.madovygabor.maturimate.core.error.OperationResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing user-specific preferences and onboarding state.
 */
interface UserPreferencesRepository {
    /**
     * A [Flow] that emits [UserPreferences] whenever the underlying data changes.
     */
    val preferences: Flow<UserPreferences>

    /**
     * Persists the user's onboarding selections to local storage.
     * * @return [OperationResult.Success] if data was saved, or [OperationResult.Error] with [DataError].
     */
    suspend fun saveOnboarding(
        language: String,
        level: String,
        isVpuEnabled: Boolean,
        examDate: Long
    ): OperationResult<Unit, DataError>
}