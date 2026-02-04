package com.madovygabor.maturimate.core.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.madovygabor.maturimate.core.database.DatabaseDriverFactory
import com.madovygabor.maturimate.features.createDataStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module


/**
 * Android-specific dependency injection module.
 * Provides platform-required components like the Android Context for Database and DataStore.
 */
actual val platformModule = module {

    /**
     * Registers the DatabaseDriverFactory as a singleton.
     * On Android, it requires the [androidContext] to access the application's file system.
     */
    single {
        DatabaseDriverFactory(androidContext())
    }

    /**
     * Initializes the DataStore for preferences on Android.
     * Uses the internal file system provided by the Android context for persistent storage.
     */
    single<DataStore<Preferences>> {
        createDataStore(androidContext())
    }
}

