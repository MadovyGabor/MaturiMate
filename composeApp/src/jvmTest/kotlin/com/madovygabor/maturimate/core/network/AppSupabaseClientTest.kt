package com.madovygabor.maturimate.core.network

import com.madovygabor.maturimate.features.auth.data.AuthRepositoryImpl
import com.madovygabor.maturimate.features.auth.domain.AuthState
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.compose.auth.composeAuth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class AppSupabaseClientTest {

    @Test
    fun appClientStartsWithAuthAndComposeAuth() = runBlocking {
        val client = createAppSupabaseClient()
        assertNotNull(client.composeAuth)

        // loading the stored session finishes without a network call
        val state = withTimeout(10_000) { AuthRepositoryImpl(client.auth).state.first { it != AuthState.Loading } }
        assertNotEquals(AuthState.Loading, state)
        client.close()
    }
}
