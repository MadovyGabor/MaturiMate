package com.madovygabor.maturimate.core.network

import com.madovygabor.maturimate.core.config.SupabaseConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.AuthConfig
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.compose.auth.ComposeAuth
import io.github.jan.supabase.compose.auth.googleNativeLogin
import io.github.jan.supabase.createSupabaseClient

/**
 * The app's Supabase client: Auth only for now (sign-up and sign-in).
 *
 * The session is kept by the library's default storage (SharedPreferences on Android,
 * Java Preferences on desktop), so a signed-in user stays signed in after a restart.
 */
fun createAppSupabaseClient(): SupabaseClient =
    createSupabaseClient(SupabaseConfig.URL, SupabaseConfig.PUBLISHABLE_KEY) {
        install(Auth) {
            flowType = FlowType.PKCE
            platformAuthConfig()
        }
        // native Google sign-in on Android; the button comes with the account screen (step 5.12)
        install(ComposeAuth) {
            googleNativeLogin(serverClientId = SupabaseConfig.GOOGLE_WEB_CLIENT_ID)
        }
    }

/** Platform parts of the Auth setup, such as where the browser returns after an OAuth sign-in. */
expect fun AuthConfig.platformAuthConfig()
