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
    @Serializable
    data class Test(val unitId: Long) : Route // Fullscreen test, one task at a time
    @Serializable
    data object PracticePicker : Route // Fullscreen picker of the finished topics and the length of a practice round
    @Serializable
    data class PracticeRound(val topicIds: List<Long>, val count: Int) : Route // Fullscreen free practice round of the chosen topics

    // --- App Main UI with navbar---
    @Serializable
    data object Main : Route {

        // Home Feature
        @Serializable
        data object Home : Route // What to do today: goal, streak, the next step

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

        // Practice Feature: the free practice (9a.2); mistake practice, weak topics and review follow (9a.3 to 9a.5)
        @Serializable
        data object Practice : Route

        // Profile Feature
        @Serializable
        data object Profile : Route {
            @Serializable
            data object Overview : Route // Where the student stands: numbers, progress, the last activities
            @Serializable
            data object History : Route // Every finished lesson and test, under day headings
            @Serializable
            data object Answers : Route // The onboarding answers, until the settings screen (9.4)
            @Serializable
            data object SignUp : Route // A guest creates an account, from the profile
            @Serializable
            data object SignIn : Route // A guest signs in to an existing account, from the profile
        }
    }
}