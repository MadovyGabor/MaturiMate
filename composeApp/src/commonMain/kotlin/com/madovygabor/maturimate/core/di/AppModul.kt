package com.madovygabor.maturimate.core.di

import com.madovygabor.maturimate.core.database.createDatabase
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.appOnboarding.domain.UserPreferencesRepository
import com.madovygabor.maturimate.features.appOnboarding.data.UserPreferencesRepositoryImpl
import com.madovygabor.maturimate.features.appOnboarding.domain.MaturitaValidator
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingViewModel
import com.madovygabor.maturimate.features.profileDetails.presentation.ProfileViewModel
import com.madovygabor.maturimate.features.subjectsAndExams.data.DatabaseSeeder
import com.madovygabor.maturimate.features.subjectsAndExams.data.repository.ExamRepositoryImpl
import com.madovygabor.maturimate.features.subjectsAndExams.domain.repository.ExamRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import java.rmi.Naming.bind

/**
 * Platform-specific module declaration.
 * Implemented in androidMain and desktopMain.
 */
expect val platformModule : org.koin.core.module.Module

/**
 * Global dependency injection module for shared business logic and data handling.
 * This module configures how various components like repositories, validators,
 * and view models are instantiated across different platforms.
 */
val sharedModule = module {

    // --- Data Sources & Persistence ---

    /** * Provides a singleton instance of the SQLDelight Database.
     * It uses platform-specific drivers provided by the context.
     */
    single { createDatabase(get()) }

    /** * Handles initial data population from JSON resources into the database.
     */
    singleOf(::DatabaseSeeder)

    // --- Repositories (Data Layer Implementation) ---

    /** * Binds the UserPreferences implementation to its interface.
     * Manages DataStore-based settings like graduation date and level.
     */
    singleOf(::UserPreferencesRepositoryImpl) { bind<UserPreferencesRepository>() }

    /** * Binds the Exam repository implementation to its domain interface.
     * Orchestrates data flow between SQLDelight, JSON seeding, and the UI.
     */
    singleOf(::ExamRepositoryImpl) { bind<ExamRepository>() }

    // --- Domain Logic & Business Rules ---

    /** * Pure Kotlin validator for maturita-specific business rules (e.g., date validation).
     */
    singleOf(::MaturitaValidator)

    // --- UI State Management & Controllers ---

    /** * Global controller for managing transient UI messages (Snackbars).
     * Accessible from both ViewModels and Composables.
     */
    singleOf(::SnackbarController)

    // --- ViewModels (Presentation Layer) ---

    /** * Manages the user onboarding flow and initial data synchronization.
     */
    viewModelOf(::OnboardingViewModel)

    /** * Handles profile-related state and user preference updates.
     */
    viewModelOf(::ProfileViewModel)
}

