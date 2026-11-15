package com.madovygabor.maturimate.core.network

import io.github.jan.supabase.auth.AuthConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Port of the local server that receives the browser's redirect after an OAuth sign-in
 * (Google) on desktop. Fixed, so Supabase can allow `http://127.0.0.1:<port>` and every path below it
 * as a redirect URL (llm/supabase-setup.md); the path is a new random secret for every sign-in.
 */
const val DESKTOP_AUTH_CALLBACK_PORT = 47613

/** How long the desktop sign-in waits for the browser: choosing the Google account or a second factor can take a while. */
val DESKTOP_AUTH_TIMEOUT: Duration = 3.minutes

/** The longest the exchange of a code that came in may take; it runs to its end even when [DESKTOP_AUTH_TIMEOUT] is up. */
val DESKTOP_AUTH_EXCHANGE_LIMIT: Duration = 30.seconds

/** The pages the browser shows at the end of the sign-in: the app's own, in Slovak and Hungarian (jvmMain resources). */
private const val SIGN_IN_DONE_PAGE = "/auth/sign-in-done.html"
private const val SIGN_IN_FAILED_PAGE = "/auth/sign-in-failed.html"

/**
 * Nothing to set on desktop: the Google sign-in does not use the library's callback server (`signInWith`), which listened
 * on every network interface and accepted tokens in the query. The app's own receiver does it instead
 * (features/auth/presentation/GoogleSignIn.jvm.kt): loopback only, the PKCE code only.
 */
actual fun AuthConfig.platformAuthConfig() = Unit

/** The page after a successful sign-in; a bare page with the app's name if it is missing. */
internal fun signInDonePage(): String = page(SIGN_IN_DONE_PAGE)

/** The page after a refused or failed sign-in: back to the app, try again. */
internal fun signInFailedPage(): String = page(SIGN_IN_FAILED_PAGE)

private fun page(path: String): String =
    DesktopAuthPage::class.java.getResource(path)?.readText(Charsets.UTF_8)
        ?: "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>MaturiMate</title></head><body><h1>MaturiMate</h1></body></html>"

/** Only an anchor for the resource lookup. */
private object DesktopAuthPage
