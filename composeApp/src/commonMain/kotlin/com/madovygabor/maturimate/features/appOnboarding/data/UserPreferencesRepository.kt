package com.madovygabor.maturimate.features.appOnboarding.data

import kotlinx.coroutines.flow.Flow


interface UserPreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun saveOnboarding(language: String, level: String, isVpuEnabled: Boolean, examDate: Long)
}