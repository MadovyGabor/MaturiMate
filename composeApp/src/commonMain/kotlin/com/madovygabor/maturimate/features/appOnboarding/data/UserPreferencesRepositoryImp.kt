package com.madovygabor.maturimate.features.appOnboarding.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Data class representing user preferences
data class UserPreferences(
    val language: String,
    val level: String,
    val isVpuEnabled: Boolean,
    val examDate: Long,
    val isSetupCompleted: Boolean
)

// Implementation of UserPreferencesRepository using DataStore
class UserPreferencesRepositoryImpl(
    private val dataStore: DataStore<Preferences>
): UserPreferencesRepository {

    // Define preference keys
    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val LEVEL = stringPreferencesKey("eng_level")
        val IS_VPU_ENABLED = booleanPreferencesKey("is_vpu_enabled")
        val EXAM_DATE = longPreferencesKey("exam_date")
        val SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
    }


    // Retrieve user preferences as a Flow
    override val preferences: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            language = prefs[Keys.LANGUAGE] ?: "HU",
            level = prefs[Keys.LEVEL] ?: "B2",
            isVpuEnabled = prefs[Keys.IS_VPU_ENABLED] ?: false,
            examDate = prefs[Keys.EXAM_DATE] ?: 0L,
            isSetupCompleted = prefs[Keys.SETUP_COMPLETED] ?: false
        )
    }

    // Save onboarding preferences
    override suspend fun saveOnboarding(language: String, level: String, isVpuEnabled: Boolean, examDate: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.LANGUAGE] = language
            prefs[Keys.LEVEL] = level
            prefs[Keys.IS_VPU_ENABLED] = isVpuEnabled
            prefs[Keys.EXAM_DATE] = examDate
            prefs[Keys.SETUP_COMPLETED] = true
        }
    }
}
