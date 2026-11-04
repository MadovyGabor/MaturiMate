package com.madovygabor.maturimate.core.di

import com.madovygabor.maturimate.core.database.DatabaseDriverFactory
import com.madovygabor.maturimate.features.reminder.domain.NoReminderScheduler
import com.madovygabor.maturimate.features.reminder.domain.ReminderScheduler
import org.koin.dsl.module

import java.io.File

actual val platformModule = module {

    single { DatabaseDriverFactory(File(System.getProperty("user.home"), ".maturimate")) }
    // no daily reminder on the desktop: there is no background service to send it
    single<ReminderScheduler> { NoReminderScheduler }
}
