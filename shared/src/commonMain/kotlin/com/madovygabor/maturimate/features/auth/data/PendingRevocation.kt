package com.madovygabor.maturimate.features.auth.data

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import com.madovygabor.maturimate.features.auth.domain.SessionRevoker
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile

/**
 * A sign-out without a network cannot end the session on the server. The phone forgets the session at once (and its data),
 * keeps the old refresh token in [store], and [revoke] ends the session on the server at the next opening with a network.
 * Signing in again meanwhile does not matter: the old session is ended with its own token, the new one is left alone.
 * A second offline sign-out before that adds its token; none is overwritten.
 *
 * The tokens live in the same store as the session, which Android leaves out of the backup and the device transfer
 * (`data_extraction_rules.xml`), not in the user database, which is backed up. Older versions kept one in the database
 * (`pending_revocation` in `Sync.sq`); such a token is moved once, the first time the tokens are needed.
 */
class PendingRevocation(
    private val database: UserDatabase,
    private val auth: AuthRepository,
    private val dispatcher: CoroutineDispatcher,
    private val store: PendingTokenStore,
) : SessionRevoker {
    @Volatile
    private var moved = false

    suspend fun save(refreshToken: String) = withContext(dispatcher) {
        moveFromDatabase()
        add(refreshToken)
    }

    suspend fun hasPending(): Boolean = tokens().isNotEmpty()

    override suspend fun revokePending() = revoke()

    /**
     * Tells the server, oldest token first; a token is forgotten only when its session ended or the server no longer knows
     * it. Without a network or on a server failure it is kept and tried again at the next opening.
     */
    suspend fun revoke() {
        for (token in tokens()) {
            if (auth.revokeSession(token) is OperationResult.Success) {
                withContext(dispatcher) { store.write(store.read() - token) }
            }
        }
    }

    private suspend fun tokens(): List<String> = withContext(dispatcher) {
        moveFromDatabase()
        store.read()
    }

    private fun add(token: String) {
        // a handful at most; the oldest would go first if a phone kept signing out offline without ever coming online
        store.write((store.read() - token + token).takeLast(MAX_TOKENS))
    }

    /** A token an older version left in the user database goes to [store], and the database forgets it. */
    private fun moveFromDatabase() {
        if (moved) return
        database.syncQueries.selectPendingRevocation().executeAsOneOrNull()?.let { old ->
            add(old)
            database.syncQueries.clearPendingRevocation()
        }
        moved = true
    }

    private companion object {
        const val MAX_TOKENS = 10
    }
}

/** Where the refresh tokens of offline sign-outs wait, oldest first. */
interface PendingTokenStore {
    fun read(): List<String>
    fun write(tokens: List<String>)
}

/**
 * [PendingTokenStore] in the settings store the session lives in (the default `Settings()` of multiplatform-settings:
 * the shared preferences on Android, which the backup rules leave out; Java Preferences on the desktop). One value, the
 * tokens one per line (a refresh token has no line break).
 */
class SettingsPendingTokenStore(private val settings: Settings = Settings()) : PendingTokenStore {
    override fun read(): List<String> =
        settings.getStringOrNull(KEY)?.lines()?.filter { it.isNotBlank() }.orEmpty()

    override fun write(tokens: List<String>) {
        if (tokens.isEmpty()) settings.remove(KEY) else settings.putString(KEY, tokens.joinToString("\n"))
    }

    private companion object {
        const val KEY = "maturimate-pending-revocation"
    }
}
