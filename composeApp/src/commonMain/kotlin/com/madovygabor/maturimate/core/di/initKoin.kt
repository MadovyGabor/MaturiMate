package com.madovygabor.maturimate.core.di


import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * Entry point for Koin initialization.
 * Returns [KoinApplication] to allow manual dependency retrieval (useful for Desktop/Tests).
 */
fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(sharedModule, platformModule)
    }
}

