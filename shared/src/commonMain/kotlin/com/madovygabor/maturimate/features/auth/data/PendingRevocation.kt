package com.madovygabor.maturimate.features.auth.data

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import com.madovygabor.maturimate.features.auth.domain.SessionRevoker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * A sign-out without a network cannot end the session on the server. The phone forgets the session at once (and its data),
 * keeps the old refresh token here, and [revoke] ends the session on the server at the next opening with a network.
 * Signing in again meanwhile does not matter: a session of another sign-in is left alone, and the token is dropped.
 */
class PendingRevocation(
    private val database: UserDatabase,
    private val auth: AuthRepository,
    private val dispatcher: CoroutineDispatcher,
) : SessionRevoker {
    suspend fun save(refreshToken: String) = withContext(dispatcher) {
        database.syncQueries.savePendingRevocation(refreshToken)
    }

    suspend fun hasPending(): Boolean = withContext(dispatcher) {
        database.syncQueries.selectPendingRevocation().executeAsOneOrNull() != null
    }

    override suspend fun revokePending() = revoke()

    /** Tells the server; the token is forgotten when it went through or when the server no longer knows it. */
    suspend fun revoke() {
        val token = withContext(dispatcher) { database.syncQueries.selectPendingRevocation().executeAsOneOrNull() } ?: return
        val result = auth.revokeSession(token)
        // no network: tried again at the next opening; any other answer means the token is dead or the session ended
        if (result is OperationResult.Error && result.error == AuthError.NETWORK) return
        withContext(dispatcher) { database.syncQueries.clearPendingRevocation() }
    }
}
