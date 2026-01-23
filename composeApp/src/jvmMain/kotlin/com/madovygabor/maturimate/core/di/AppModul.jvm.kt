package com.madovygabor.maturimate.core.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.madovygabor.maturimate.features.appOnboarding.data.DATA_STORE_FILE_NAME
import com.madovygabor.maturimate.features.appOnboarding.data.createDataStore
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

actual val platformModule = module{

    singleOf(::DbClient)

    single<DataStore<Preferences>> {
        createDataStore {
            // Desktopon pl. a felhasználó home könyvtárába mentünk
            DATA_STORE_FILE_NAME
        }
    }
}
