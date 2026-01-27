package com.madovygabor.maturimate.core.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.madovygabor.maturimate.features.createDataStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module


actual val platformModule = module {
    singleOf(::DbClient)

    single<DataStore<Preferences>> {
        // Androidon a kontextus fájlrendszerét használjuk
        createDataStore(androidContext())
    }
}

