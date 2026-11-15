package com.madovygabor.maturimate.features.auth.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.madovygabor.maturimate.features.auth.domain.AuthError

/**
 * Starts the Google sign-in: the native account picker on Android, the browser on desktop
 * (it returns to the app through the local callback server).
 *
 * This is the one place where a screen reaches the Supabase client: compose-auth's Google flow
 * is a Composable, so it cannot live in a repository.
 *
 * Exactly one callback ends each started flow, so a screen can wait for it: [onSignedIn] once the new session is saved,
 * [onError] when it failed (on desktop also when the browser was left or Google was cancelled), [onClosed] when the
 * user closed the Android account picker.
 *
 * [accountHint] (an e-mail address) asks Google to offer that account and always show the account choice (`login_hint`,
 * `prompt=select_account`); the deletion proof uses it on the desktop. Android's native picker has no such option and
 * ignores it.
 *
 * @return A function that starts the flow.
 */
@Composable
expect fun rememberGoogleSignIn(
    onSignedIn: () -> Unit,
    onError: (AuthError) -> Unit,
    onClosed: () -> Unit,
    accountHint: String? = null,
): () -> Unit

/**
 * Whether a started Google flow still owes its one callback. A flow whose composition goes away never calls back
 * (Android: rotation cancels compose-auth's picker; desktop: leaving the screen cancels the browser wait), so the
 * screen would stay busy: [rememberOpenGoogleFlow] then reports `onClosed`. Every callback marks it [answered], so
 * nothing is reported twice.
 */
internal class OpenGoogleFlow {
    private var open = false

    fun opened() {
        open = true
    }

    /** The flow called back; true when it was open. */
    fun answered(): Boolean = open.also { open = false }
}

/** An [OpenGoogleFlow] that calls [onClosed] when the composition leaves while the flow is still open. */
@Composable
internal fun rememberOpenGoogleFlow(onClosed: () -> Unit): OpenGoogleFlow {
    val flow = remember { OpenGoogleFlow() }
    val closed by rememberUpdatedState(onClosed)
    DisposableEffect(flow) {
        onDispose { if (flow.answered()) closed() }
    }
    return flow
}
