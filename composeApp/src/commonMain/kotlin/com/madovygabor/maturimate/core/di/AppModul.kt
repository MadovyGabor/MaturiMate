package com.madovygabor.maturimate.core.di

import com.madovygabor.maturimate.core.database.ContentDatabaseProvider
import com.madovygabor.maturimate.core.database.ContentDatabaseSource
import com.madovygabor.maturimate.core.database.createUserDatabase
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.appOnboarding.domain.UserPreferencesRepository
import com.madovygabor.maturimate.features.appOnboarding.data.UserPreferencesRepositoryImpl
import com.madovygabor.maturimate.features.appOnboarding.domain.MaturitaValidator
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingViewModel
import com.madovygabor.maturimate.features.learning.data.ContentRepositoryImpl
import com.madovygabor.maturimate.features.learning.data.ProgressRepositoryImpl
import com.madovygabor.maturimate.features.learning.domain.ContentRepository
import com.madovygabor.maturimate.features.learning.domain.ProgressRepository
import com.madovygabor.maturimate.features.profileDetails.presentation.ProfileViewModel
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Platform-specific module declaration.
 * Implemented in androidMain and desktopMain.
 */
expect val platformModule : org.koin.core.module.Module

/**
 * Shared dependencies for all platforms.
 */
val sharedModule = module {

    // Data Sources & Repositories

    // Local user database (progress, settings); opened on first use
    single { createUserDatabase(get()) }

    // Read-only content database; installs the bundled content file on first use
    singleOf(::ContentDatabaseProvider) { bind<ContentDatabaseSource>() }

    single<ContentRepository> { ContentRepositoryImpl(get()) }
    single<ProgressRepository> { ProgressRepositoryImpl(get()) }

    // Binds the implementation to the interface for clean architecture
    singleOf(::UserPreferencesRepositoryImpl) { bind<UserPreferencesRepository>() }

    // Domain Logic / Validators
    singleOf(::MaturitaValidator)

    // UI Controllers

    // Singleton for managing global snackbar events
    singleOf(::SnackbarController)

    // ViewModels
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::ProfileViewModel)
}

