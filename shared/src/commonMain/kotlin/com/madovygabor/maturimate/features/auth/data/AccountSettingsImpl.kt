package com.madovygabor.maturimate.features.auth.data

import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.auth.domain.AccountRepository
import com.madovygabor.maturimate.features.auth.domain.AccountRules
import com.madovygabor.maturimate.features.auth.domain.ExportSaver
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import com.madovygabor.maturimate.features.auth.domain.AccountSettings
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import com.madovygabor.maturimate.features.sync.domain.SyncEngine
import com.madovygabor.maturimate.features.sync.domain.SyncOutcome
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * [AccountSettings] over Supabase Auth, the sync and the local user database.
 *
 * The name is a profile field and goes through the sync like the others. The password and anything else of the account
 * itself never go through the sync: they change only online, directly through Supabase Auth.
 */
class AccountSettingsImpl(
    private val auth: AuthRepository,
    private val profiles: UserProfileRepository,
    private val localUser: LocalUserProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sync: SyncEngine? = null,
    private val revocation: PendingRevocation? = null,
    private val account: AccountRepository? = null,
    private val exportSaver: ExportSaver? = null,
    private val today: () -> String = { Clock.System.todayIn(TimeZone.currentSystemDefault()).toString() },
) : AccountSettings {

    override suspend fun saveNames(firstName: String, lastName: String): OperationResult<Unit, AuthError> {
        val first = firstName.trim()
        val last = lastName.trim()
        if (first.isEmpty() || last.isEmpty()) return OperationResult.Error(AuthError.UNKNOWN)
        // saved here; the change of the profile makes the sync take it to the server in a few seconds
        return if (profiles.saveNames(first, last) is OperationResult.Error) OperationResult.Error(AuthError.UNKNOWN) else OperationResult.Success(Unit)
    }

    override suspend fun changePassword(oldPassword: String, newPassword: String): OperationResult<Unit, AuthError> {
        val email = auth.currentUser()?.email ?: return OperationResult.Error(AuthError.INVALID_CREDENTIALS)
        if (!AccountRules.isStrongPassword(newPassword)) return OperationResult.Error(AuthError.WEAK_PASSWORD)
        if (newPassword == oldPassword) return OperationResult.Error(AuthError.SAME_PASSWORD)
        // a sign-in with the old password proves it is the owner; it keeps the same session
        val checked = auth.signIn(email, oldPassword)
        if (checked is OperationResult.Error) return checked
        return auth.setNewPassword(newPassword)
    }

    override suspend fun deleteAccount(): OperationResult<Unit, AuthError> = withContext(dispatcher + NonCancellable) {
        val repository = account ?: return@withContext OperationResult.Error(AuthError.UNKNOWN)
        val deleted = repository.deleteAccount()
        if (deleted is OperationResult.Error) return@withContext deleted
        // the user no longer exists on the server: a server-side sign-out would fail, so only the local session goes
        auth.clearLocalSession()
        localUser.removeUser()
        // the state of the old account's sync (and its cloud badge) goes with it
        sync?.refresh()
        OperationResult.Success(Unit)
    }

    override suspend fun exportData(): OperationResult<String, AuthError> {
        val repository = account ?: return OperationResult.Error(AuthError.UNKNOWN)
        val saver = exportSaver ?: return OperationResult.Error(AuthError.UNKNOWN)
        val text = when (val result = repository.exportMyData()) {
            is OperationResult.Success -> result.data
            is OperationResult.Error -> return OperationResult.Error(result.error)
        }
        val where = saver.save("maturimate-adataim-${today()}.json", text) ?: return OperationResult.Error(AuthError.UNKNOWN)
        return OperationResult.Success(where)
    }

    // runs to the end even when the settings go away: removing the rows rebuilds the app
    override suspend fun signOut(force: Boolean): OperationResult<Unit, AuthError> = withContext(dispatcher + NonCancellable) {
        // what is not on the server yet goes first; without it nothing is removed unless the user insisted
        if (!force && sync != null && sync.syncBeforeSignOut() is SyncOutcome.Failed) {
            return@withContext OperationResult.Error(AuthError.NETWORK)
        }
        val token = auth.refreshToken()
        val signedOut = auth.signOut()
        if (signedOut is OperationResult.Error) {
            if (signedOut.error != AuthError.NETWORK) return@withContext signedOut
            // no network: the phone is signed out anyway, the server is told at the next opening
            token?.let { revocation?.save(it) }
            auth.clearLocalSession()
        }
        localUser.removeUser()
        sync?.refresh()
        OperationResult.Success(Unit)
    }
}
