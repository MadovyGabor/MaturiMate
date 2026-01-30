package com.madovygabor.maturimate.features.appOnboarding.domain

import kotlinx.datetime.LocalDate

/**
 * Domain model representing the user's settings and progress state.
 */
data class UserPreferences(
    val maturitaLanguage: MaturitaLanguage,
    val maturitaLevel: MaturitaLevel,
    val maturitaDate: LocalDate,
    val isVpuEnabled: Boolean,
    val isSetupCompleted: Boolean

)

enum class MaturitaLanguage { HU, SK }
enum class MaturitaLevel { B1, B2 }

