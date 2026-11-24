package com.madovygabor.maturimate.core.presentation.theme

import androidx.compose.runtime.Composable

/**
 * Desktop: the JVM has no system setting for it, so motion is on, unless the app is started with
 * `-Dmaturimate.reducedMotion=true`.
 */
@Composable
actual fun rememberReducedMotion(): Boolean = System.getProperty("maturimate.reducedMotion") == "true"
