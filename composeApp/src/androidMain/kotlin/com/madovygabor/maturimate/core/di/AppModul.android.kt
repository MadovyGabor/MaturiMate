package com.madovygabor.maturimate.core.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.madovygabor.maturimate.core.database.DatabaseDriverFactory
import com.madovygabor.maturimate.features.createDataStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module


actual val platformModule = module {
    single { DatabaseDriverFactory(androidContext()) }

    single<DataStore<Preferences>> {
        // Androidon a kontextus fájlrendszerét használjuk
        createDataStore(androidContext())
    }
}

