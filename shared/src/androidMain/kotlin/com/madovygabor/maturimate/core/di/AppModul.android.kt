package com.madovygabor.maturimate.core.di

import com.madovygabor.maturimate.core.database.DatabaseDriverFactory
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module


actual val platformModule = module {
    single { DatabaseDriverFactory(androidContext()) }
}
