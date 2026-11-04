package com.madovygabor.maturimate.core.di

import com.madovygabor.maturimate.core.database.ContentDatabaseProvider
import com.madovygabor.maturimate.core.database.ContentDatabaseSource
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.createUserDatabase
import com.madovygabor.maturimate.core.network.createAppSupabaseClient
import com.madovygabor.maturimate.core.presentation.AppViewModel
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
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
import com.madovygabor.maturimate.features.learning.domain.LearningCourse
import com.madovygabor.maturimate.features.learning.domain.RecentActivities
import com.madovygabor.maturimate.features.learning.domain.StudySettings
import com.madovygabor.maturimate.features.learning.domain.StudyTracker
import com.madovygabor.maturimate.features.learning.domain.TestRecorder
import com.madovygabor.maturimate.features.learning.presentation.LessonViewModel
import com.madovygabor.maturimate.features.learning.presentation.SubjectListViewModel
import com.madovygabor.maturimate.features.learning.presentation.TestSessionViewModel
import com.madovygabor.maturimate.features.learning.presentation.TopicTarget
import com.madovygabor.maturimate.features.learning.presentation.TopicViewModel
import com.madovygabor.maturimate.features.learning.domain.ProgressRepository
import com.madovygabor.maturimate.features.profileDetails.presentation.ProfileViewModel
import com.madovygabor.maturimate.features.userProfile.data.UserProfileRepositoryImpl
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import org.koin.core.module.dsl.bind
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Platform-specific module declaration.
 * Implemented in androidMain and desktopMain.
 */
expect val platformModule : org.koin.core.module.Module

/** The dispatcher of the user database (see [sharedModule]). */
private val USER_DATABASE = named("userDatabase")

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
    // Every call to the user database runs on this dispatcher, one at a time: no two transactions
    // overlap, and a sign-up that moves the guest's rows cannot interleave with a save
    single(USER_DATABASE) { Dispatchers.IO.limitedParallelism(1) }
    single<ProgressRepository> { ProgressRepositoryImpl(get(), get(), get(USER_DATABASE)) }

    // The learning path: subjects, parts, topics, steps and their progress
    single { LearningCourse(get(), get()) }
    // Study time per day, the daily goal and the streak; the goal and the time zone come from the profile
    single {
        val profile = get<UserProfileRepository>()
        StudyTracker(get(), settings = {
            val current = profile.profile.first()
            StudySettings(current?.dailyGoalMinutes, current?.timezone?.let { runCatching { TimeZone.of(it) }.getOrNull() } ?: TimeZone.currentSystemDefault())
        })
    }
    // The last finished lessons and tests, for the home screen
    single { RecentActivities(get(), get()) }
    // What a test round leaves in the user database: the attempt, the answers, the progress
    single { TestRecorder(get(), get()) }

    // Profile and settings of the local user
    single<UserProfileRepository> { UserProfileRepositoryImpl(get(), get(), get(USER_DATABASE)) }

    // Supabase: accounts (sign-up, sign-in, password reset)
    single { createAppSupabaseClient() }
    single<AuthRepository> { AuthRepositoryImpl(get<SupabaseClient>().auth) }
    single<AccountRepository> { SupabaseAccountRepository(get<SupabaseClient>().auth, get<SupabaseClient>().postgrest) }
    single<AccountSetup> { AccountSetupImpl(get(), get(), get(), get(), get(USER_DATABASE)) }

    // UI Controllers

    // Singleton for managing global snackbar events
    singleOf(::SnackbarController)

    // ViewModels
    viewModel { AppViewModel(get()) }
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
    viewModel { SubjectListViewModel(get(), get()) }
    viewModel { (target: TopicTarget) -> TopicViewModel(get(), target, get()) }
    viewModel { (unitId: Long) -> LessonViewModel(unitId, get(), get(), get(), get()) }
    viewModel { (unitId: Long) -> TestSessionViewModel(unitId, get(), get()) }
}

