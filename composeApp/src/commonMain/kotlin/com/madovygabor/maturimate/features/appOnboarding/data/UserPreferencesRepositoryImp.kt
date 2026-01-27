package com.madovygabor.maturimate.features.appOnboarding.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.madovygabor.maturimate.features.appOnboarding.domain.DataError
import com.madovygabor.maturimate.core.error.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Data model representing the persisted onboarding choices and application state.
 */
data class UserPreferences(
    val language: String,
    val level: String,
    val isVpuEnabled: Boolean,
    val examDate: Long,
    val isSetupCompleted: Boolean
)

/**
* Jetpack DataStore implementation of [UserPreferencesRepository].
* Handles low-level data persistence and retrieval.
*/
class UserPreferencesRepositoryImpl(
    private val dataStore: DataStore<Preferences>
): UserPreferencesRepository {

    /**
    * Private keys used to store and retrieve data from [DataStore].
    */
    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val LEVEL = stringPreferencesKey("eng_level")
        val IS_VPU_ENABLED = booleanPreferencesKey("is_vpu_enabled")
        val EXAM_DATE = longPreferencesKey("exam_date")
        val SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
    }

    /**
     * Maps the raw [Preferences] into a structured [UserPreferences] object.
     * Provides default values if the keys are not yet present in the storage.
     */
    override val preferences: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            language = prefs[Keys.LANGUAGE] ?: "HU",
            level = prefs[Keys.LEVEL] ?: "B2",
            isVpuEnabled = prefs[Keys.IS_VPU_ENABLED] ?: false,
            examDate = prefs[Keys.EXAM_DATE] ?: 0L,
            isSetupCompleted = prefs[Keys.SETUP_COMPLETED] ?: false
        )
    }

    /**
     * Updates multiple preference keys at once within a single [edit] transaction.
     * Includes error handling for I/O and unexpected exceptions.
     */
    override suspend fun saveOnboarding(
        language: String,
        level: String,
        isVpuEnabled: Boolean,
        examDate: Long
    ): OperationResult<Unit, DataError> {
        return try {
            dataStore.edit { prefs ->
                prefs[Keys.LANGUAGE] = language
                prefs[Keys.LEVEL] = level
                prefs[Keys.IS_VPU_ENABLED] = isVpuEnabled
                prefs[Keys.EXAM_DATE] = examDate
                prefs[Keys.SETUP_COMPLETED] = true
            }
            // Emit success if the transaction completes without errors
            OperationResult.Success(Unit)
        } catch (e: IOException) {
            // Specifically handle file system related issues
            OperationResult.Error(DataError.IO_ERROR)
        } catch (e: Exception) {
            // General fallback for any other error during writing
            OperationResult.Error(DataError.UNKNOWN)
        }
    }
}
