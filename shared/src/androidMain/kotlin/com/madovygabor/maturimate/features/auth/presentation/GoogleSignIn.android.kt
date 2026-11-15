package com.madovygabor.maturimate.features.auth.presentation

import androidx.compose.runtime.Composable
import com.madovygabor.maturimate.features.auth.domain.AuthError
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.compose.auth.composable.NativeSignInResult
import io.github.jan.supabase.compose.auth.composable.rememberSignInWithGoogle
import io.github.jan.supabase.compose.auth.composeAuth
import org.koin.compose.koinInject

/** Android: compose-auth's native Google account picker (Credential Manager); its result comes back in `onResult`. */
@Composable
actual fun rememberGoogleSignIn(onSignedIn: () -> Unit, onError: (AuthError) -> Unit, onClosed: () -> Unit, accountHint: String?): () -> Unit {
    val client = koinInject<SupabaseClient>()
    // a rotation cancels the picker without a result: the open flow then reports onClosed
    val open = rememberOpenGoogleFlow(onClosed)
    val state = client.composeAuth.rememberSignInWithGoogle(
        onResult = { result ->
            open.answered()
            when (result) {
                is NativeSignInResult.Success -> onSignedIn()
                NativeSignInResult.ClosedByUser -> onClosed()
                is NativeSignInResult.NetworkError -> onError(AuthError.NETWORK)
                is NativeSignInResult.Error -> onError(AuthError.UNKNOWN)
            }
        },
    )
    return {
        open.opened()
        state.startFlow()
    }
}
