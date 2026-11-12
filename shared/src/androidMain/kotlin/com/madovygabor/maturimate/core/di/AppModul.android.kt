package com.madovygabor.maturimate.core.di

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import androidx.core.content.FileProvider
import com.madovygabor.maturimate.features.auth.domain.ExportSaver
import java.io.File
import com.madovygabor.maturimate.core.database.DatabaseDriverFactory
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
    // the copy of one's data: written to the app's cache and offered through the system share sheet (no storage permission)
    single<ExportSaver> {
        val context = androidContext()
        ExportSaver { name, text ->
            runCatching {
                val dir = File(context.cacheDir, "export").apply { mkdirs() }
                val file = File(dir, name).apply { writeText(text) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.export", file)
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(share, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                name
            }.getOrNull()
        }
    }
}
