package com.madovygabor.maturimate.features.auth.domain

/**
 * Ends on the server a session that a sign-out without a network could not end. Called when the app comes to the front;
 * it does nothing when no such session waits, and keeps it for the next time when the network is still missing.
 */
fun interface SessionRevoker {
    suspend fun revokePending()
}
