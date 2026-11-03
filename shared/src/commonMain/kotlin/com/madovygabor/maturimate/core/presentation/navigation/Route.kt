package com.madovygabor.maturimate.core.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Defines the navigation routes used in the application.
@Serializable
sealed interface Route : NavKey {

    // --- Top Level Screens ---

    // Initial setup screen
    @Serializable
    data object AppOnboarding : Route {
        @Serializable
        data object Welcome : Route // Welcome screen for onboarding
        @Serializable
        data object Language : Route // Teaching language (step 1)
        @Serializable
        data object Role : Route // Role: student, learner or teacher (step 2)
        @Serializable
        data object Subjects : Route // Subjects and the English level (step 3)
        @Serializable
        data object Date : Route // Exam date, students only (step 4)
        @Serializable
        data object Goal : Route // Daily goal, students and learners
        @Serializable
        data object Reminder : Route // Daily reminder
        @Serializable
        data object Plan : Route // "A te terved" summary
        @Serializable
        data object Account : Route // Account, or "Körülnézek" (last step)
        @Serializable
        data object SignIn : Route // "Már van fiókom"
    }
    @Serializable
    data class ExamWrite(val examId: String) : Route // Fullscreen exam writing screen
    @Serializable
    data class Lesson(val unitId: Long) : Route // Fullscreen lesson, one page at a time

    // --- App Main UI with navbar---
    @Serializable
    data object Main : Route {

        // Subject Feature
        @Serializable
        data object Subject : Route {
            @Serializable
            data object Selection : Route // Main Subject selection Screen
            @Serializable
            data class Parts(val subjectId: Long) : Route // The big parts of a subject
            @Serializable
            data class Topic(val topicId: Long) : Route // The topics below a topic, or the path of a topic
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