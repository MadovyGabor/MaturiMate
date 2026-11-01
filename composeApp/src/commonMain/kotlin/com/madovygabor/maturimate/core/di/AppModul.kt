package com.madovygabor.maturimate.core.di

import com.madovygabor.maturimate.core.database.ContentDatabaseProvider
import com.madovygabor.maturimate.core.database.ContentDatabaseSource
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.createUserDatabase
import com.madovygabor.maturimate.core.network.createAppSupabaseClient
import com.madovygabor.maturimate.core.presentation.AppViewModel
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.appOnboarding.domain.MaturitaValidator
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingNavigationViewModel
import com.madovygabor.maturimate.features.appOnboarding.presentation.date.DateViewModel
import com.madovygabor.maturimate.features.appOnboarding.presentation.goal.GoalViewModel
import com.madovygabor.maturimate.features.appOnboarding.presentation.plan.PlanViewModel
import com.madovygabor.maturimate.features.appOnboarding.presentation.reminder.ReminderViewModel
import com.madovygabor.maturimate.features.appOnboarding.presentation.language.LanguageViewModel
import com.madovygabor.maturimate.features.appOnboarding.presentation.role.RoleViewModel
import com.madovygabor.maturimate.features.appOnboarding.presentation.subjects.SubjectsViewModel
import com.madovygabor.maturimate.features.auth.data.AccountSetupImpl
import com.madovygabor.maturimate.features.auth.data.AuthRepositoryImpl
import com.madovygabor.maturimate.features.auth.data.SupabaseAccountRepository
import com.madovygabor.maturimate.features.auth.domain.AccountRepository
import com.madovygabor.maturimate.features.auth.presentation.AccountViewModel
import com.madovygabor.maturimate.features.auth.presentation.SignInViewModel
import com.madovygabor.maturimate.features.auth.domain.AccountSetup
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import com.madovygabor.maturimate.features.learning.data.ContentRepositoryImpl
import com.madovygabor.maturimate.features.learning.data.ProgressRepositoryImpl
import com.madovygabor.maturimate.features.learning.domain.ContentRepository
import com.madovygabor.maturimate.features.learning.domain.ProgressRepository
import com.madovygabor.maturimate.features.profileDetails.presentation.ProfileViewModel
import com.madovygabor.maturimate.features.userProfile.data.UserProfileRepositoryImpl
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
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

    // Local user database (profile, settings, progress); opened on first use
    single { createUserDatabase(get()) }

    // Read-only content database; installs the bundled content file on first use
    singleOf(::ContentDatabaseProvider) { bind<ContentDatabaseSource>() }

    single<ContentRepository> { ContentRepositoryImpl(get()) }
    // Whose rows the user database holds: a guest id until sign-up
    single { LocalUserProvider(get()) }
    single<ProgressRepository> { ProgressRepositoryImpl(get(), get()) }

    // Profile and settings of the local user
    single<UserProfileRepository> { UserProfileRepositoryImpl(get(), get()) }

    // Supabase: accounts (sign-up, sign-in, password reset)
    single { createAppSupabaseClient() }
    single<AuthRepository> { AuthRepositoryImpl(get<SupabaseClient>().auth) }
    single<AccountRepository> { SupabaseAccountRepository(get<SupabaseClient>().auth, get<SupabaseClient>().postgrest) }
    single<AccountSetup> { AccountSetupImpl(get(), get(), get(), get()) }

    // Domain Logic / Validators
    singleOf(::MaturitaValidator)

    // UI Controllers

    // Singleton for managing global snackbar events
    singleOf(::SnackbarController)

    // ViewModels
    viewModelOf(::AppViewModel)
    viewModelOf(::OnboardingNavigationViewModel)
    viewModelOf(::LanguageViewModel)
    viewModelOf(::RoleViewModel)
    viewModelOf(::SubjectsViewModel)
    viewModel { DateViewModel(get(), get()) }
    viewModelOf(::GoalViewModel)
    viewModelOf(::ReminderViewModel)
    viewModel { PlanViewModel(get()) }
    viewModelOf(::AccountViewModel)
    viewModelOf(::SignInViewModel)
    viewModel { ProfileViewModel(get()) }
}

