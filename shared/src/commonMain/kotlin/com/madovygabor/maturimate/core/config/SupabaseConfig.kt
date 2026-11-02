package com.madovygabor.maturimate.core.config

/**
 * Where the app finds its Supabase project.
 *
 * Both values are public by design: the publishable key only identifies the project, and row level
 * security decides what a request may read or write (supabase/migrations). Secret keys (the
 * secret/service_role key, the Resend and Google secrets) never go into the app or the repository.
 * See docs/supabase-setup.md.
 */
object SupabaseConfig {
    const val URL = "https://usqefjhcnehwgszvecym.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_1t7ZFmPKmNeSayYcJKGXbQ_dT4M16M8"

    /** Web client of Google Cloud: the native Google sign-in on Android asks for a token for it. */
    const val GOOGLE_WEB_CLIENT_ID = "147440499003-cmepur0p2c4441ghta6bqtl91hm7uimd.apps.googleusercontent.com"
}
