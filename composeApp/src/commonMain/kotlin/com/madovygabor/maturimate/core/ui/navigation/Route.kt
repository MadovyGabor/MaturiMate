package com.madovygabor.maturimate.core.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Defines the navigation routes used in the application.
@Serializable
sealed interface Route : NavKey {

    // --- Top Level Screens ---
    @Serializable
    data object InitialSetup : Route // Initial setup screen
    @Serializable
    data class ExamWrite(val examId: String) : Route // Fullscreen exam writing screen

    // --- App Main UI with navbar---
    @Serializable
    data object Main : Route {

        // Subject Feature
        @Serializable
        data object Subject : Route {
            @Serializable
            data object Selection : Route // Main Subject selection Screen
            @Serializable
            data class ExamList(val subjectId: String) : Route // Exam list for a specific subject
        }

        // History Feature
        @Serializable
        data object History : Route {
            @Serializable
            data object List : Route //List of previous taken exams
            @Serializable
            data class Detail(val historyId: String) : Route // Detail of a specific exam from history
        }

        // Profile Feature
        @Serializable
        data object Profile : Route // User profile and settings
    }
}