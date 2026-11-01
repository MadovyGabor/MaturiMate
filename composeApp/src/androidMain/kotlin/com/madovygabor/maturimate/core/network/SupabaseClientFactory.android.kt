package com.madovygabor.maturimate.core.network

import io.github.jan.supabase.auth.AuthConfig

// Android signs in to Google natively (Credential Manager through ComposeAuth), so no
// deep link is needed for now.
actual fun AuthConfig.platformAuthConfig() = Unit
