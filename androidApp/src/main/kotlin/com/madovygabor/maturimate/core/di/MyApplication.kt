package com.madovygabor.maturimate.core.di

import android.app.Application
import com.madovygabor.maturimate.core.export.clearExportShareCache
import com.madovygabor.maturimate.features.reminder.domain.ReminderScheduler
import com.madovygabor.maturimate.features.reminder.domain.ReminderSync
import com.madovygabor.maturimate.features.sync.domain.SyncTriggers
import com.madovygabor.maturimate.reminder.AndroidReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.dsl.module

class MyApplication : Application(), KoinComponent {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@MyApplication)
            modules(module { single<ReminderScheduler> { AndroidReminderScheduler(androidContext()) } })
        }
        // a share of the export leaves a copy of the data in the cache (older versions did too): it goes at every start
        applicationScope.launch(Dispatchers.IO) { clearExportShareCache(this@MyApplication) }
        // plans the daily reminder, and again whenever the profile changes it (also after a restart of the phone)
        applicationScope.launch { get<ReminderSync>().run() }
        // uploads a settings change a few seconds after it was made
        applicationScope.launch { get<SyncTriggers>().run() }
    }
}
