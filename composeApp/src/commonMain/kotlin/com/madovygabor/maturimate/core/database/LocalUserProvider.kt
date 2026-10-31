package com.madovygabor.maturimate.core.database

import com.madovygabor.maturimate.database.user.UserDatabase
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The id of the user whose rows the local user database holds.
 *
 * On the first call a guest id ("guest-" and a random UUID) is stored; it stays until sign-up
 * replaces it with the account's id (roadmap step 6.5). Call it off the main thread.
 */
@OptIn(ExperimentalUuidApi::class)
class LocalUserProvider(
    private val database: UserDatabase,
    private val newGuestId: () -> String = { GUEST_PREFIX + Uuid.random() },
) {
    private val queries get() = database.profileQueries
    private var cached: String? = null

    fun userId(): String = cached ?: database.transactionWithResult {
        queries.insertLocalUser(newGuestId(), true)
        queries.selectLocalUser().executeAsOne().user_id
    }.also { cached = it }

    companion object {
        const val GUEST_PREFIX = "guest-"
    }
}
