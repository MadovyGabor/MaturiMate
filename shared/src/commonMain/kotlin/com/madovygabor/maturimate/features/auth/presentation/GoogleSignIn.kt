package com.madovygabor.maturimate.features.auth.presentation

import androidx.compose.runtime.Composable
import com.madovygabor.maturimate.features.auth.domain.AuthError
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.compose.auth.composable.NativeSignInResult
import io.github.jan.supabase.compose.auth.composable.rememberSignInWithGoogle
import io.github.jan.supabase.compose.auth.composeAuth
import org.koin.compose.koinInject

/**
 * Starts the Google sign-in: the native account picker on Android, the browser on desktop
 * (it returns to the app through the local callback server).
 *
 * This is the one place where a screen reaches the Supabase client: compose-auth's Google flow
 * is a Composable, so it cannot live in a repository.
 *
 * @return A function that starts the flow.
 */
@Composable
fun rememberGoogleSignIn(onSignedIn: () -> Unit, onError: (AuthError) -> Unit): () -> Unit {
    val client = koinInject<SupabaseClient>()
    val state = client.composeAuth.rememberSignInWithGoogle(
        onResult = { result ->
            when (result) {
                is NativeSignInResult.Success -> onSignedIn()
                NativeSignInResult.ClosedByUser -> Unit
                is NativeSignInResult.NetworkError -> onError(AuthError.NETWORK)
                is NativeSignInResult.Error -> onError(AuthError.UNKNOWN)
            }
        },
    )
    return { state.startFlow() }
}
