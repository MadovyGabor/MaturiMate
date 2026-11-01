package com.madovygabor.maturimate.core.di

import com.madovygabor.maturimate.core.database.DatabaseDriverFactory
import org.koin.dsl.module

import java.io.File

actual val platformModule = module {

    single { DatabaseDriverFactory(File(System.getProperty("user.home"), ".maturimate")) }
}
