package com.madovygabor.maturimate.features.appOnboarding.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.madovygabor.maturimate.features.appOnboarding.domain.DataError
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.appOnboarding.domain.MaturitaLanguage
import com.madovygabor.maturimate.features.appOnboarding.domain.MaturitaLevel
import com.madovygabor.maturimate.features.appOnboarding.domain.UserPreferences
import com.madovygabor.maturimate.features.appOnboarding.domain.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
* Jetpack DataStore implementation of [com.madovygabor.maturimate.features.appOnboarding.domain.UserPreferencesRepository].
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
    @OptIn(ExperimentalTime::class)
    override val preferences: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            maturitaLanguage = prefs[Keys.LANGUAGE]?.let {
                try { MaturitaLanguage.valueOf(it) } catch (e: Exception) { MaturitaLanguage.HU }
            } ?: MaturitaLanguage.HU,

            maturitaLevel = prefs[Keys.LEVEL]?.let {
                try { MaturitaLevel.valueOf(it) } catch (e: Exception) { MaturitaLevel.B2 }
            } ?: MaturitaLevel.B2,

            isVpuEnabled = prefs[Keys.IS_VPU_ENABLED] ?: false,

            // Long (epoch days) -> LocalDate
            maturitaDate = prefs[Keys.EXAM_DATE]?.let {
                LocalDate.fromEpochDays(it.toInt())
            } ?: Clock.System.todayIn(TimeZone.currentSystemDefault()),

            isSetupCompleted = prefs[Keys.SETUP_COMPLETED] ?: false
        )
    }

    /**
     * Updates multiple preference keys at once within a single [edit] transaction.
     * Includes error handling for I/O and unexpected exceptions.
     */
    override suspend fun saveUserPreferences(
        maturitaLanguage: MaturitaLanguage,
        maturitaLevel: MaturitaLevel,
        maturitaDate: LocalDate,
        isVpuEnabled: Boolean
    ): OperationResult<Unit, DataError> {
        return try {
            dataStore.edit { prefs ->
                prefs[Keys.LANGUAGE] = maturitaLanguage.name
                prefs[Keys.LEVEL] = maturitaLevel.name
                prefs[Keys.IS_VPU_ENABLED] = isVpuEnabled
                prefs[Keys.EXAM_DATE] = maturitaDate.toEpochDays()
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
