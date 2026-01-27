package com.madovygabor.maturimate.features.appOnboarding.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

// File name for the DataStore preferences file.
internal const val DATA_STORE_FILE_NAME = "prefs.preferences_pb"

// Function to create a DataStore instance for storing Preferences.
fun createDataStore(producePath: () -> String): DataStore<Preferences>{
    return PreferenceDataStoreFactory.createWithPath(
        produceFile = {
            producePath().toPath()
        }
    )
}

