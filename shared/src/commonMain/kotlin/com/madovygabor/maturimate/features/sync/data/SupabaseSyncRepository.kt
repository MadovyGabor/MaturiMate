package com.madovygabor.maturimate.features.sync.data

import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.sync.domain.SyncChanges
import com.madovygabor.maturimate.features.sync.domain.SyncError
import com.madovygabor.maturimate.features.sync.domain.SyncRepository
import com.madovygabor.maturimate.features.sync.domain.SyncResponse
import com.madovygabor.maturimate.features.sync.domain.SyncSession
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * [SyncRepository] over the `sync_user_data` function (supabase/migrations). The function runs with the rights of the
 * signed-in user (row level security); the user id comes from the session on the server, never from the phone.
 *
 * Every call also names the account this phone belongs to ([localUser], `p_expected_user`, migration 20261128120000):
 * the server refuses the call (`user_mismatch`) when its session is another account's, so a session that changed
 * between the phone's own check and the call can never write this phone's rows into another account. A guest phone names
 * no account and never calls (it never syncs: [SyncSessionImpl]).
 */
class SupabaseSyncRepository(
    private val auth: Auth,
    private val postgrest: Postgrest,
    private val localUser: LocalUserProvider,
    private val dispatcher: CoroutineDispatcher,
) : SyncRepository {

    override suspend fun sync(
        cursor: String?,
        deviceId: String,
        guestMerge: Boolean,
        changes: SyncChanges,
    ): OperationResult<SyncResponse, SyncError> =
        try {
            if (auth.currentUserOrNull() == null) {
                OperationResult.Error(SyncError.NOT_SIGNED_IN)
            } else {
                // an empty cursor means "no cursor": the function takes text, not null, so the call always has all its arguments
                // a guest's id ("guest-" and a uuid) is no account's: such a phone has nothing to sync (the engine never
                // gets here for a guest either), and the server would refuse the id
                val (guest, expectedUser) = withContext(dispatcher) { localUser.isGuest() to localUser.userId() }
                if (guest) {
                    OperationResult.Error(SyncError.NOT_SIGNED_IN)
                } else {
                    val result = postgrest.rpc("sync_user_data", SyncParams(cursor.orEmpty(), deviceId, guestMerge, changes, expectedUser))
                    OperationResult.Success(result.decodeAs<SyncResponse>())
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestException) {
            OperationResult.Error(SyncError.NETWORK)
        } catch (e: RestException) {
            OperationResult.Error(mapRest(e))
        } catch (e: Exception) {
            // an answer this version cannot read
            OperationResult.Error(SyncError.UNKNOWN)
        }

    internal fun mapRest(e: RestException): SyncError {
        val text = "${e.error} ${e.description}".lowercase()
        return when {
            "sync_too_large" in text -> SyncError.TOO_LARGE
            "cursor_too_old" in text -> SyncError.CURSOR_TOO_OLD
            // the server's session is not the account the phone named
            "user_mismatch" in text -> SyncError.WRONG_ACCOUNT
            e.statusCode == 401 || "not signed in" in text || "jwt" in text -> SyncError.NOT_SIGNED_IN
            else -> SyncError.SERVER
        }
    }
}

/** The arguments of `sync_user_data`. */
@Serializable
internal data class SyncParams(
    @SerialName("p_cursor") val cursor: String,
    @SerialName("p_device_id") val deviceId: String,
    @SerialName("p_guest_merge") val guestMerge: Boolean,
    @SerialName("p_changes") val changes: SyncChanges,
    /** The account this phone belongs to: the server refuses the call when its session is another account's. */
    @SerialName("p_expected_user") val expectedUser: String,
)

/** [SyncSession] from the Supabase session and the local user: only the phone's own account syncs (a guest has none). */
class SyncSessionImpl(
    private val auth: Auth,
    private val localUser: LocalUserProvider,
    private val dispatcher: CoroutineDispatcher,
) : SyncSession {
    override suspend fun isSignedIn(): Boolean {
        // at a cold start the saved session loads in the background: without the wait the first run would see nobody
        auth.awaitInitialization()
        val sessionUserId = auth.currentUserOrNull()?.id ?: return false
        // the session must be this phone's own account: a guest phone, or a phone of account X while Y is signed in
        // (Y not set up yet, so not adopted), has no account to sync; X's rows would go to Y and Y's come to X
        return withContext(dispatcher) { !localUser.isGuest() && localUser.userId() == sessionUserId }
    }

    override suspend fun deviceId(): String = withContext(dispatcher) { localUser.deviceId() }
}
