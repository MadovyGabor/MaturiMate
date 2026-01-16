package com.madovygabor.maturimate.core.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable


// Defines the navigation routes used in the application.
@Serializable
sealed interface Route : NavKey {

    // Route for the Subject Tiles Screen
    @Serializable
    data object SubjectListTabScreen : NavKey

    // Route for the Subject Detail List Screen with a subjectId parameter
    @Serializable
    data class SubjectDetailList(val subjectId: String) : NavKey

}