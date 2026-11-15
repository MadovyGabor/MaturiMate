package com.madovygabor.maturimate.core.di


import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * Entry point for Koin initialization.
 * Starts Koin with the shared and the platform modules; [config] adds the platform's own setup (the Android context, for example).
 */
fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(sharedModule, platformModule)
    }
}

