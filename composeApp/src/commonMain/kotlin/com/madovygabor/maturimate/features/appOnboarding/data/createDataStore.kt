package com.madovygabor.maturimate.features.appOnboarding.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

/**
 * Filename for the DataStore preferences file.
 * The .preferences_pb extension is the standard for Protocol Buffer based DataStore.
 */
internal const val DATA_STORE_FILE_NAME = "prefs.preferences_pb"

/**
 * Factory function to create a DataStore instance for storing application Preferences.
 * * @param producePath A lambda that returns the absolute path where the file should be stored.
 * @return A [DataStore] instance configured with the provided file path.
 */
fun createDataStore(producePath: () -> String): DataStore<Preferences> {
    return PreferenceDataStoreFactory.createWithPath(
        produceFile = {
            // Converts the String path to an Okio Path required by DataStore
            producePath().toPath()
        }
    )
}

