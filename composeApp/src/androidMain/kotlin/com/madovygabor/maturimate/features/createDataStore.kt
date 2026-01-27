package com.madovygabor.maturimate.features

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.madovygabor.maturimate.features.appOnboarding.data.DATA_STORE_FILE_NAME
import com.madovygabor.maturimate.features.appOnboarding.data.createDataStore

fun createDataStore(context: Context): DataStore<Preferences> {
    return createDataStore {
        context.filesDir.resolve(DATA_STORE_FILE_NAME).absolutePath
    }
}