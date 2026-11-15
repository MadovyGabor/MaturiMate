package com.madovygabor.maturimate.core.di

import android.content.Context
import android.net.ConnectivityManager
import com.madovygabor.maturimate.core.database.DatabaseDriverFactory
import com.madovygabor.maturimate.core.export.AndroidExportSaver
import com.madovygabor.maturimate.core.export.ExportPicker
import com.madovygabor.maturimate.features.auth.domain.ExportSaver
import com.madovygabor.maturimate.features.content.domain.NetworkState
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module


actual val platformModule = module {
    single { DatabaseDriverFactory(androidContext()) }
    // mobile data (or a hotspot that costs money) counts as metered; a large download then waits for the user's yes
    single<NetworkState> {
        val connectivity = androidContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        NetworkState { connectivity.isActiveNetworkMetered }
    }
    // the copy of one's data: the user picks the place in the system's "save as" screen (MainActivity registers the picker)
    single { ExportPicker() }
    single<ExportSaver> { AndroidExportSaver(androidContext(), get()) }
}
