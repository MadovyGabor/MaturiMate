package com.madovygabor.maturimate.core.network

import io.github.jan.supabase.auth.AuthConfig

/**
 * Port of the local server that receives the browser's redirect after an OAuth sign-in
 * (Google) on desktop. Fixed, so Supabase can allow exactly `http://localhost:<port>`
 * as a redirect URL (docs/supabase-setup.md).
 */
const val DESKTOP_AUTH_CALLBACK_PORT = 47613

actual fun AuthConfig.platformAuthConfig() {
    httpCallbackConfig {
        httpPort = DESKTOP_AUTH_CALLBACK_PORT
        htmlTitle = "MaturiMate"
    }
}
