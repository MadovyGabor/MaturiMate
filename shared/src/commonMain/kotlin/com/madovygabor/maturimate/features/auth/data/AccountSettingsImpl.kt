package com.madovygabor.maturimate.features.auth.data

import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.auth.domain.AccountRepository
import com.madovygabor.maturimate.features.auth.domain.AccountRules
import com.madovygabor.maturimate.features.auth.domain.AccountSettings
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** [AccountSettings] over Supabase Auth, the account's rows on the server and the local user database. */
class AccountSettingsImpl(
    private val auth: AuthRepository,
    private val account: AccountRepository,
    private val profiles: UserProfileRepository,
    private val localUser: LocalUserProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AccountSettings {

    override suspend fun saveNames(firstName: String, lastName: String): OperationResult<Unit, AuthError> {
        val first = firstName.trim()
        val last = lastName.trim()
        if (first.isEmpty() || last.isEmpty()) return OperationResult.Error(AuthError.UNKNOWN)
        // the server first: a name only on this phone would be lost on another one
        val saved = account.saveNames(first, last)
        if (saved is OperationResult.Error) return saved
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

    // runs to the end even when the settings go away: removing the rows rebuilds the app
    override suspend fun signOut(): OperationResult<Unit, AuthError> = withContext(dispatcher + NonCancellable) {
        val signedOut = auth.signOut()
        if (signedOut is OperationResult.Error) return@withContext signedOut
        localUser.removeUser()
        OperationResult.Success(Unit)
    }
}
