package com.madovygabor.maturimate.core.di

import com.madovygabor.maturimate.core.database.DatabaseDriverFactory
import com.madovygabor.maturimate.features.reminder.domain.NoReminderScheduler
import com.madovygabor.maturimate.features.reminder.domain.ReminderScheduler
import com.madovygabor.maturimate.features.content.domain.NetworkState
import com.madovygabor.maturimate.features.auth.domain.ExportSaver
import org.koin.dsl.module

import java.io.File

actual val platformModule = module {

    single { DatabaseDriverFactory(File(System.getProperty("user.home"), ".maturimate")) }
    // no daily reminder on the desktop: there is no background service to send it
    single<ReminderScheduler> { NoReminderScheduler }
    // a computer's network is never treated as metered
    single<NetworkState> { NetworkState { false } }
    // the copy of one's data goes into the home folder
    single<ExportSaver> {
        ExportSaver { name, text ->
            runCatching { File(System.getProperty("user.home"), name).apply { writeText(text) }.absolutePath }.getOrNull()
        }
    }
}
