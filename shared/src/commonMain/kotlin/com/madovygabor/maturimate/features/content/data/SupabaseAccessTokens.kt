package com.madovygabor.maturimate.features.content.data

import io.github.jan.supabase.auth.Auth

/** [AccessTokens] from the Supabase session. */
class SupabaseAccessTokens(private val auth: Auth) : AccessTokens {
    override suspend fun current(): String? = auth.currentSessionOrNull()?.accessToken

    override suspend fun refreshed(): String? =
        runCatching { auth.refreshCurrentSession() }.getOrNull().let { auth.currentSessionOrNull()?.accessToken }
}
