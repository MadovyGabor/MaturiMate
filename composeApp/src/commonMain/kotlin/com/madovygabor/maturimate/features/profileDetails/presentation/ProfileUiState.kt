package com.madovygabor.maturimate.features.profileDetails.presentation

import com.madovygabor.maturimate.core.presentation.utils.Subject
import com.madovygabor.maturimate.features.appOnboarding.domain.MaturitaLanguage
import com.madovygabor.maturimate.features.appOnboarding.domain.MaturitaLevel
import kotlinx.datetime.LocalDate

/**
 * Represents the complete UI state for the Profile screen.
 * * Uses a 'Draft' pattern to separate the persisted user data from temporary
 * changes made during an editing session in a dialog.
 */
data class ProfileUiState(
    /** Controls which edit dialog is currently visible to the user. */
    val editType: EditType = EditType.NONE,

    // --- Persisted (Real) Data ---
    val maturitaDate: LocalDate? = null,
    val maturitaLevel: MaturitaLevel = MaturitaLevel.B1,
    val maturitaLanguage: MaturitaLanguage = MaturitaLanguage.HU,
    val daysLeft: Int = 0,

    // --- Temporary (Draft) Data ---
    // These hold the values while the user is interacting with a dialog.
    val draftMaturitaDate: LocalDate? = null,
    val draftMaturitaLevel: MaturitaLevel = MaturitaLevel.B1,
    val draftMaturitaLanguage: MaturitaLanguage = MaturitaLanguage.HU,

    /** List of subject-specific statistics to display in the progress section. */
    val subjectStats: List<SubjectStat> = emptyList()
)

/**
 * UI model representing progress statistics for a single subject.
 * * @param subject The domain model containing subject details (name, icon, etc.).
 * @param progress A float value between 0.0 and 1.0 representing completion.
 * @param scoreText The formatted percentage or score string (e.g., "85%").
 */
data class SubjectStat(
    val subject: Subject,
    val progress: Float,
    val scoreText: String
)

/**
 * Defines the possible editing states for the profile.
 */
enum class EditType {
    /** No dialog is shown. */
    NONE,
    /** Editing the graduation exam date. */
    DATE,
    /** Editing the chosen maturita language/tagozat. */
    LANGUAGE,
    /** Editing the English level (B1/B2). */
    LEVEL
}