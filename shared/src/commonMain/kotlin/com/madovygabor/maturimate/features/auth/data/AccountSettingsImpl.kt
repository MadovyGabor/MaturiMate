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
import com.madovygabor.maturimate.features.auth.domain.AccountUser
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import com.madovygabor.maturimate.features.auth.domain.DeletionProof
import com.madovygabor.maturimate.features.auth.domain.FreshSignInCheck
import com.madovygabor.maturimate.features.auth.domain.SignInsInFlight
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import com.madovygabor.maturimate.features.sync.domain.SyncEngine
import com.madovygabor.maturimate.features.sync.domain.SyncOutcome
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

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
    /** A monotonic clock (not the wall clock, which the user can set), for how long a fresh sign-in proves a deletion. */
    private val timeSource: TimeSource = TimeSource.Monotonic,
    /** The browser sign-ins still running; a cancelled proof waits for them before it compares the sessions. */
    private val signIns: SignInsInFlight? = null,
) : AccountSettings {

    private val prettyJson = Json { prettyPrint = true }

    override suspend fun saveNames(firstName: String, lastName: String): OperationResult<Unit, AuthError> {
        val first = firstName.trim()
        val last = lastName.trim()
        if (first.isEmpty() || last.isEmpty()) return OperationResult.Error(AuthError.UNKNOWN)
        // saved here; the change of the profile makes the sync take it to the server in a few seconds
        return if (profiles.saveNames(first, last) is OperationResult.Error) OperationResult.Error(AuthError.UNKNOWN) else OperationResult.Success(Unit)
    }

    override suspend fun sendPasswordCode(): OperationResult<Unit, AuthError> {
        // the code goes to the account's e-mail address: without a session there is none to send it to
        val user = auth.currentUser() ?: return OperationResult.Error(AuthError.SESSION_EXPIRED)
        if (user.email.isNullOrBlank()) return OperationResult.Error(AuthError.UNKNOWN)
        return auth.sendReauthenticationCode()
    }

    // NonCancellable: the server's password check opens a session that must be ended or handed to the revocation, also
    // when the page goes away meanwhile
    override suspend fun changePassword(oldPassword: String, code: String?, newPassword: String): OperationResult<Unit, AuthError> =
        withContext(NonCancellable) {
            val user = auth.currentUser() ?: return@withContext OperationResult.Error(AuthError.SESSION_EXPIRED)
            // an account made with Google has no password to change (the settings do not offer it)
            if (!user.hasPassword) return@withContext OperationResult.Error(AuthError.UNKNOWN)
            val digits = code?.trim()
            if (code != null && digits.isNullOrEmpty()) return@withContext OperationResult.Error(AuthError.INVALID_CODE)
            if (oldPassword.isBlank()) return@withContext OperationResult.Error(AuthError.INVALID_CREDENTIALS)
            if (!AccountRules.isStrongPassword(newPassword)) return@withContext OperationResult.Error(AuthError.WEAK_PASSWORD)
            // The old password is checked by the server (and sent as current_password); GoTrue checks the reauthentication
            // code only on sessions older than 24 hours and answers REAUTHENTICATION_REQUIRED when needed.
            checkPassword(oldPassword)?.let { return@withContext OperationResult.Error(it) }
            val result = auth.changePasswordWithCode(digits, newPassword, currentPassword = oldPassword)
            if (digits != null && result is OperationResult.Error && result.error == AuthError.REAUTHENTICATION_REQUIRED) {
                OperationResult.Error(AuthError.INVALID_CODE)
            } else {
                result
            }
        }

    /** The server's check of [password]; null when it is right. A session the check could not end is ended later. */
    private suspend fun checkPassword(password: String): AuthError? =
        when (val checked = auth.verifyPassword(password)) {
            is OperationResult.Error -> checked.error
            is OperationResult.Success -> {
                checked.data?.let { revocation?.save(it) }
                null
            }
        }

    override suspend fun isGuestPhone(): Boolean = withContext(dispatcher) { localUser.isGuest() }

    /** When a fresh Google sign-in of this account proved the deletion (a monotonic mark), and for which account. */
    private var freshSignIn: Pair<String, TimeMark>? = null

    /** Since when a proof runs (between start and finish or cancel); null when none does. */
    @Volatile
    private var proofSince: TimeMark? = null

    private fun proofRunning(): Boolean = proofSince?.let { it.elapsedNow() < SyncEngine.MAX_HOLD } == true

    override suspend fun startFreshSignInProof(): OperationResult<Unit, AuthError> = withContext(NonCancellable) {
        freshSignIn = null
        // No sync while another account's session may be this phone's for a moment (the Google sign-in imports it). The
        // hold runs out by itself if neither finish nor cancel ever comes.
        sync?.hold()
        proofSince = timeSource.markNow()
        val marked = auth.markSessionBeforeProof()
        if (marked is OperationResult.Error) endProof()
        marked
    }

    override suspend fun finishFreshSignInProof(): OperationResult<Unit, AuthError> = withContext(NonCancellable) {
        try {
            val check = auth.checkFreshSignIn()
            saveUnended(check)
            val id = auth.currentUser()?.id
            freshSignIn = if (check.result is OperationResult.Success && id != null) id to timeSource.markNow() else null
            check.result
        } finally {
            endProof()
        }
    }

    override suspend fun cancelFreshSignInProof() = withContext(NonCancellable) {
        try {
            // a desktop exchange still running after the screen went away can save a session yet: the comparison below
            // must see it (bounded: the receiver's own limits end it)
            signIns?.awaitNone(SIGN_IN_SETTLE)
            saveUnended(auth.cancelFreshSignIn())
        } finally {
            endProof()
        }
    }

    /** The sessions a proof could not end are ended later. */
    private suspend fun saveUnended(check: FreshSignInCheck) {
        check.unended.forEach { revocation?.save(it) }
    }

    private suspend fun endProof() {
        proofSince = null
        sync?.release()
    }

    override suspend fun endForeignSession(): Boolean = withContext(dispatcher + NonCancellable) {
        // during a proof another account's session is expected for a moment: the proof itself puts it right
        if (proofRunning()) return@withContext false
        val user = auth.currentUser() ?: return@withContext false
        // a guest phone with a session is an account that is not set up yet: that session is the phone's to use
        if (localUser.isGuest() || localUser.userId() == user.id) return@withContext false
        val token = auth.refreshToken()
        if (auth.signOut() is OperationResult.Error) {
            // no network: the phone forgets it now, the server is told at the next opening
            token?.let { revocation?.save(it) }
            auth.clearLocalSession()
        }
        // only the session goes: the rows are this phone's account's, never the other one's
        sync?.refresh()
        true
    }

    /**
     * The deletion on the server with [proof], for [user]: their password, or a fresh sign-in of an account without one.
     * The call goes out with the proof's own session (its token carries the sign-in the server checks): the password
     * check's session, lent out before it ends, or the fresh Google session that is this phone's after the proof.
     */
    private suspend fun deleteWithProof(proof: DeletionProof, user: AccountUser, repository: AccountRepository): OperationResult<Unit, AuthError> =
        when (proof) {
            is DeletionProof.Password -> when {
                !user.hasPassword -> OperationResult.Error(AuthError.PROOF_NEEDED)
                proof.password.isBlank() -> OperationResult.Error(AuthError.INVALID_CREDENTIALS)
                else -> {
                    // nothing that cannot be undone without the server's check of the password
                    var deleted: OperationResult<Unit, AuthError> = OperationResult.Error(AuthError.UNKNOWN)
                    when (val checked = auth.verifyPassword(proof.password) { token -> deleted = repository.deleteAccount(token) }) {
                        is OperationResult.Error -> OperationResult.Error(checked.error)
                        is OperationResult.Success -> {
                            // the check's session could not be ended: it is ended later (after a deletion the server answers
                            // that it is gone, which counts as ended)
                            checked.data?.let { revocation?.save(it) }
                            deleted
                        }
                    }
                }
            }
            DeletionProof.FreshSignIn -> {
                val signedIn = freshSignIn
                // used once, so a failed deletion needs a new sign-in
                freshSignIn = null
                // one snapshot of the session: the user checked is the one whose token goes out
                val session = auth.currentSession()
                when {
                    user.hasPassword -> OperationResult.Error(AuthError.PROOF_NEEDED)
                    signedIn == null || signedIn.first != user.id || signedIn.second.elapsedNow() > FRESH_SIGN_IN -> OperationResult.Error(AuthError.PROOF_NEEDED)
                    session == null -> OperationResult.Error(AuthError.SESSION_EXPIRED)
                    // the session changed after the proof (another account's sign-in): not the proven account's token
                    session.userId != signedIn.first -> OperationResult.Error(AuthError.PROOF_NEEDED)
                    else -> repository.deleteAccount(session.accessToken)
                }
            }
        }

    override suspend fun deleteAccount(proof: DeletionProof): OperationResult<Unit, AuthError> = withContext(dispatcher + NonCancellable) {
        val repository = account ?: return@withContext OperationResult.Error(AuthError.UNKNOWN)
        val user = auth.currentUser() ?: return@withContext OperationResult.Error(AuthError.SESSION_EXPIRED)
        val guest = localUser.isGuest()
        // nothing that cannot be undone without the proof that the user is the account's owner (checked here, and by the
        // server again in the token)
        val deleted = deleteWithProof(proof, user, repository)
        if (deleted is OperationResult.Error) return@withContext deleted
        // the user no longer exists on the server: a server-side sign-out would fail, so only the local session goes
        auth.clearLocalSession()
        // a guest phone's rows were never the account's: they stay, the guest goes on
        if (!guest) localUser.removeUser()
        // the state of the old account's sync (and its cloud badge) goes with it
        sync?.refresh()
        OperationResult.Success(Unit)
    }

    override val canShareExport: Boolean get() = exportSaver?.canShare == true

    override suspend fun exportData(share: Boolean): OperationResult<String, AuthError> {
        val repository = account ?: return OperationResult.Error(AuthError.UNKNOWN)
        val saver = exportSaver ?: return OperationResult.Error(AuthError.UNKNOWN)
        val text = when (val result = repository.exportMyData()) {
            is OperationResult.Success -> result.data
            is OperationResult.Error -> return OperationResult.Error(result.error)
        }
        val name = "maturimate-adataim-${today()}.json"
        val where = (if (share) saver.share(name, pretty(text)) else saver.save(name, pretty(text)))
            ?: return OperationResult.Error(AuthError.UNKNOWN)
        return OperationResult.Success(where)
    }

    /** One field per line, so a person can read the file; a text that is not JSON is handed over as it came. */
    private fun pretty(text: String): String =
        runCatching { prettyJson.encodeToString(JsonElement.serializer(), prettyJson.parseToJsonElement(text)) }.getOrDefault(text)

    // runs to the end even when the settings go away: removing the rows rebuilds the app
    override suspend fun signOut(force: Boolean): OperationResult<Unit, AuthError> = withContext(dispatcher + NonCancellable) {
        // A guest phone with a session (an account that is not set up, so the phone was never taken over): only the
        // session ends; the guest's rows were never synced anywhere and stay.
        val guest = localUser.isGuest()
        // what is not on the server yet goes first; without it nothing is removed unless the user insisted
        if (!guest && !force && sync != null && sync.syncBeforeSignOut() is SyncOutcome.Failed) {
            return@withContext OperationResult.Error(AuthError.NETWORK)
        }
        val token = auth.refreshToken()
        val signedOut = auth.signOut()
        if (signedOut is OperationResult.Error) {
            // No network, or a server failure (supabase-kt has cleared the session by then): the phone is signed out
            // anyway, and the server is told at the next opening with the old token.
            token?.let { revocation?.save(it) }
            auth.clearLocalSession()
        }
        if (!guest) localUser.removeUser()
        sync?.refresh()
        OperationResult.Success(Unit)
    }

    private companion object {
        /** How long a fresh Google sign-in proves a deletion. */
        val FRESH_SIGN_IN: Duration = 5.minutes

        /** The longest a cancel waits for a sign-in in flight: the desktop exchange (30 s), its page (5 s), the stop. */
        val SIGN_IN_SETTLE: Duration = 40.seconds
    }
}
