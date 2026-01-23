package com.madovygabor.maturimate.core.di

import com.madovygabor.maturimate.features.appOnboarding.data.UserPreferencesRepository
import com.madovygabor.maturimate.features.appOnboarding.data.UserPreferencesRepositoryImpl
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingViewModel
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module


expect val platformModule : org.koin.core.module.Module

val sharedModule = module {

    // Repository kötése az interfészhez
    singleOf(::UserPreferencesRepositoryImpl) { bind<UserPreferencesRepository>() }

     //Ha a viewModelOf crashel, próbáld meg a hagyományos factory-t:
     factory { OnboardingViewModel(get()) }

}

