package com.madovygabor.maturimate.core.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.madovygabor.maturimate.features.appOnboarding.data.DATA_STORE_FILE_NAME
import com.madovygabor.maturimate.features.appOnboarding.data.createDataStore
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

import java.io.File

actual val platformModule = module {

    singleOf(::DbClient)

    single<DataStore<Preferences>> {
        createDataStore {
            val appDir = File(System.getProperty("user.home"), ".maturimate")
            if (!appDir.exists()) {
                appDir.mkdirs()
            }
            File(appDir, DATA_STORE_FILE_NAME).absolutePath
        }
    }
}
