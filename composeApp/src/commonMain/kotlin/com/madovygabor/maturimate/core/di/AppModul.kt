package com.madovygabor.maturimate.core.di

import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.appOnboarding.data.UserPreferencesRepository
import com.madovygabor.maturimate.features.appOnboarding.data.UserPreferencesRepositoryImpl
import com.madovygabor.maturimate.features.appOnboarding.domain.OnBoardingDataValidator
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingViewModel
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

    // Binds the implementation to the interface for clean architecture
    singleOf(::UserPreferencesRepositoryImpl) { bind<UserPreferencesRepository>() }

    // Domain Logic / Validators
    singleOf(::OnBoardingDataValidator)

    // UI Controllers

    // Singleton for managing global snackbar events
    singleOf(::SnackbarController)

    // ViewModels
    viewModelOf(::OnboardingViewModel)
}

