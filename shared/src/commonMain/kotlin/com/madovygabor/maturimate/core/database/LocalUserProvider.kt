package com.madovygabor.maturimate.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import com.madovygabor.maturimate.database.user.UserDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.concurrent.Volatile
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The id of the user whose rows the local user database holds.
 *
 * On the first call a guest id ("guest-" and a random UUID) is stored; it stays until sign-up
 * or sign-in replaces it with the account's id ([adoptAccount]). Call it off the main thread, on the
 * user database's dispatcher, which runs one call at a time (`AppModul`).
 */
@OptIn(ExperimentalUuidApi::class)
class LocalUserProvider(
    private val database: UserDatabase,
    private val newDeviceId: () -> String = { Uuid.random().toString() },
    private val newGuestId: () -> String = { GUEST_PREFIX + Uuid.random() },
) {
    private val queries get() = database.profileQueries
    @Volatile
    private var cached: String? = null
    @Volatile
    private var cachedDevice: String? = null

    fun userId(): String = cached ?: database.transactionWithResult {
        queries.insertLocalUser(newGuestId(), true)
        queries.selectLocalUser().executeAsOne().user_id
    }.also { cached = it }

    /**
     * The id of this installation: made once, kept when the user signs out. The study time of a day is stored per
     * device, so two devices of one account add up exactly.
     */
    fun deviceId(): String = cachedDevice ?: database.transactionWithResult {
        queries.insertLocalDevice(newDeviceId())
        queries.selectLocalDevice().executeAsOne()
    }.also { cachedDevice = it }

    /** True while no account is signed in on this phone. */
    fun isGuest(): Boolean {
        userId()
        return queries.selectLocalUser().executeAsOne().is_guest
    }

    /** The current id, again after [adoptAccount]; it creates the guest id first if needed. */
    fun userIdFlow(dispatcher: CoroutineDispatcher): Flow<String> = flow {
        userId()
        emitAll(queries.selectLocalUser().asFlow().mapToOne(dispatcher).map { it.user_id })
    }.distinctUntilChanged()

    /**
     * Makes [accountId] the user of this phone after a sign-up or sign-in, in one transaction. A
     * guest's rows move to the account, so nothing answered or practised as a guest is lost
     * (roadmap 7.5). Another account's rows are not handed over: they are removed.
     */
    fun adoptAccount(accountId: String) {
        val current = userId()
        when {
            current == accountId -> Unit
            isGuest() -> database.transaction {
                queries.adoptAccount(accountId = accountId, guestId = current)
                // the first sync of the account merges: the account's profile wins, the guest's learning data is added
                database.syncQueries.clearSyncState()
                database.syncQueries.ensureSyncState(accountId)
                database.syncQueries.setGuestMergePending(true)
            }
            else -> queries.switchAccount(previousId = current, accountId = accountId)
        }
        cached = accountId
    }

    /**
     * Sign-out: every row of the current user goes, and a new guest takes its place.
     * Until the sync (12.3) the rows are nowhere else; the settings warn before.
     */
    fun removeUser() {
        val current = userId()
        // a new guest in the same transaction: the readers of the local user never see it missing
        val guest = newGuestId()
        database.transaction {
            queries.removeUser(current)
            queries.insertLocalUser(guest, true)
        }
        cached = guest
    }

    companion object {
        const val GUEST_PREFIX = "guest-"
    }
}
