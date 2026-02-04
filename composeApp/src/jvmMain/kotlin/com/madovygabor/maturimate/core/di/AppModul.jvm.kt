package com.madovygabor.maturimate.core.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.madovygabor.maturimate.core.database.DatabaseDriverFactory
import com.madovygabor.maturimate.features.appOnboarding.data.DATA_STORE_FILE_NAME
import com.madovygabor.maturimate.features.appOnboarding.data.createDataStore
import org.koin.dsl.module
import java.io.File

/**
 * Desktop-specific dependency injection module.
 * Configures storage paths and database drivers for the JVM environment.
 */
actual val platformModule = module {

    /**
     * Initializes DataStore for Desktop using a shared application directory.
     * Everything is stored in the user's home under the ".maturimate" hidden folder.
     */
    single<DataStore<Preferences>> {
        createDataStore {
            // Resolve the shared app directory: C:/Users/{User}/.maturimate/
            val appDir = File(System.getProperty("user.home"), ".maturimate")

            // Ensure the directory exists before DataStore tries to write to it
            if (!appDir.exists()) {
                appDir.mkdirs()
            }

            // Return the full path to the preferences file
            File(appDir, DATA_STORE_FILE_NAME).absolutePath
        }
    }

    /**
     * Registers the DatabaseDriverFactory for Desktop.
     * Unlike Android, no external context is needed to initialize the SQLite driver on JVM.
     */
    single { DatabaseDriverFactory() }
}
