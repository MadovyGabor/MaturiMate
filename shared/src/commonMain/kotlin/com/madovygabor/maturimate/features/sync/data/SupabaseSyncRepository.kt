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
 */
class SupabaseSyncRepository(
    private val auth: Auth,
    private val postgrest: Postgrest,
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
                val result = postgrest.rpc("sync_user_data", SyncParams(cursor.orEmpty(), deviceId, guestMerge, changes))
                OperationResult.Success(result.decodeAs<SyncResponse>())
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
)

/** [SyncSession] from the Supabase session and the local user (a guest has no account to sync). */
class SyncSessionImpl(
    private val auth: Auth,
    private val localUser: LocalUserProvider,
    private val dispatcher: CoroutineDispatcher,
) : SyncSession {
    override suspend fun isSignedIn(): Boolean {
        // at a cold start the saved session loads in the background: without the wait the first run would see nobody
        auth.awaitInitialization()
        return auth.currentUserOrNull() != null && withContext(dispatcher) { !localUser.isGuest() }
    }

    override suspend fun deviceId(): String = withContext(dispatcher) { localUser.deviceId() }
}
