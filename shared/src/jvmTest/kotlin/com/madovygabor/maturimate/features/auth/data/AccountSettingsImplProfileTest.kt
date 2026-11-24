package com.madovygabor.maturimate.features.auth.data

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.auth.domain.AccountUser
import com.madovygabor.maturimate.features.auth.domain.ExportSaver
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.sync.domain.SyncError
import com.madovygabor.maturimate.features.sync.domain.SyncProfile
import com.madovygabor.maturimate.features.sync.domain.SyncSettings
import com.madovygabor.maturimate.features.learning.domain.model.AttemptStatus
import com.madovygabor.maturimate.features.learning.domain.model.AttemptType
import com.madovygabor.maturimate.features.learning.domain.model.DailyActivity
import com.madovygabor.maturimate.features.learning.domain.model.Streak
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgress
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgressStatus
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class AccountSettingsImplProfileTest : AccountSettingsImplTestBase() {

    @Test
    fun aNewNameIsSavedOnThisPhoneAndLeftToTheSync() = runBlocking {
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        assertEquals(OperationResult.Success(Unit), settings().saveNames(" Anna ", "Kiss"))
        val saved = profiles.profile.first()!!
        assertEquals("Anna", saved.firstName)
        assertEquals("Kiss", saved.lastName)
        // an empty name is not saved
        assertEquals(OperationResult.Error(AuthError.UNKNOWN), settings().saveNames(" ", "Kiss"))
    }

    @Test
    fun thePasswordChangesWithTheEmailedCodeAndAStrongNewOne() = runBlocking {
        val auth = Auth()
        val settings = settings(auth)
        assertEquals(OperationResult.Success(Unit), settings.sendPasswordCode())
        assertEquals(1, auth.codesSent)

        assertEquals(OperationResult.Error(AuthError.INVALID_CODE), settings.changePassword("Old12345", "000000", "New12345"))
        assertEquals(OperationResult.Error(AuthError.INVALID_CODE), settings.changePassword("Old12345", "  ", "New12345"))
        assertEquals(OperationResult.Error(AuthError.WEAK_PASSWORD), settings.changePassword("Old12345", "123456", "short"))
        assertEquals(OperationResult.Error(AuthError.SAME_PASSWORD), settings.changePassword("Old12345", "123456", "Old12345"))
        assertNull(auth.newPassword)
        assertEquals(OperationResult.Success(Unit), settings.changePassword("Old12345", " 123456 ", "New12345"))
        assertEquals("New12345", auth.newPassword)
        // the old password is checked by the server, never as a sign-in of this phone: the session stays as it is
        assertEquals(0, auth.signIns)
    }

    // Card 11: GoTrue checks the code only on sessions older than 24 hours, so on a fresh session any code changed the
    // password. The old password, checked by the server, is the other half of the proof.
    @Test
    fun thePasswordChangeNeedsTheOldPasswordToo() = runBlocking {
        val auth = Auth()
        val settings = settings(auth)

        assertEquals(OperationResult.Error(AuthError.INVALID_CREDENTIALS), settings.changePassword("Wrong123", "123456", "New12345"))
        assertEquals(OperationResult.Error(AuthError.INVALID_CREDENTIALS), settings.changePassword("  ", "123456", "New12345"))
        assertNull(auth.newPassword)
        // both are needed: the right old password with a wrong code changes nothing either
        assertEquals(OperationResult.Error(AuthError.INVALID_CODE), settings.changePassword("Old12345", "000000", "New12345"))
        assertNull(auth.newPassword)

        auth.rateLimited = true
        assertEquals(OperationResult.Error(AuthError.TOO_MANY_REQUESTS), settings.changePassword("Old12345", "123456", "New12345"))
        assertNull(auth.newPassword)
    }

    @Test
    fun aPasswordCheckWhoseSessionLivesOnIsEndedLater() = runBlocking {
        val auth = Auth().apply { leftover = "check-refresh" }
        val tokens = MemoryTokens()

        assertEquals(OperationResult.Success(Unit), settings(auth, revocation = PendingRevocation(database, auth, dbDispatcher, tokens)).changePassword("Old12345", "123456", "New12345"))

        // the change went through, and the check's session waits to be ended at the next opening
        assertEquals("New12345", auth.newPassword)
        assertEquals(listOf("check-refresh"), tokens.tokens)
    }

    @Test
    fun anAccountWithoutAPasswordHasNoPasswordToChange() = runBlocking {
        val auth = Auth().apply { user = AccountUser("account-1", "anna@example.com", "Anna", "Kovács", hasPassword = false) }

        assertEquals(OperationResult.Error(AuthError.UNKNOWN), settings(auth).changePassword("Old12345", "123456", "New12345"))
        assertEquals(0, auth.passwordChecks)
        assertNull(auth.newPassword)
    }

    // A session without an e-mail address used to say "the old password is wrong".
    @Test
    fun aMissingSessionOrEmailIsNeverAWrongPassword() = runBlocking {
        val auth = Auth().apply { user = null }
        assertEquals(OperationResult.Error(AuthError.SESSION_EXPIRED), settings(auth).sendPasswordCode())
        assertEquals(OperationResult.Error(AuthError.SESSION_EXPIRED), settings(auth).changePassword("Old12345", "123456", "New12345"))
        auth.user = AccountUser("account-1", null, null, null)
        assertEquals(OperationResult.Error(AuthError.UNKNOWN), settings(auth).sendPasswordCode())
        assertEquals(0, auth.codesSent)
    }

    @Test
    fun thePasswordAndTheEmailNeverTouchTheSync() = runBlocking {
        // the sync has no field for the account itself: its shapes hold the profile, the settings and the learning data only
        val fields = (0 until SyncProfile.serializer().descriptor.elementsCount).map { SyncProfile.serializer().descriptor.getElementName(it) }.toSet() +
            (0 until SyncSettings.serializer().descriptor.elementsCount).map { SyncSettings.serializer().descriptor.getElementName(it) }.toSet()
        assertTrue(fields.none { it.contains("password") || it.contains("email") || it.contains("token") }, fields.toString())
        val repo = Repo(OperationResult.Error(SyncError.NETWORK))
        val auth = Auth()
        assertEquals(OperationResult.Success(Unit), settings(auth, engine(repo)).changePassword("Old12345", "123456", "New12345"))
        assertEquals("New12345", auth.newPassword)
    }

    // S2-2: the current password goes with the change, for the server's own check
    @Test
    fun theChangeCarriesTheCurrentPasswordToTheServer() = runBlocking {
        val auth = Auth()
        assertEquals(OperationResult.Success(Unit), settings(auth).changePassword("Old12345", "123456", "New12345"))
        assertEquals("Old12345", auth.sentCurrentPassword)
    }

    @Test
    fun theExportIsHandedOverWithTheDateInItsName() = runBlocking {
        var saved: Pair<String, String>? = null
        val saver = ExportSaver { name, text -> saved = name to text; "/home/anna/$name" }
        val settings = AccountSettingsImpl(Auth(), profiles, localUser, dbDispatcher, account = Server(), exportSaver = saver, today = { "2026-11-12" })

        assertEquals(OperationResult.Success("/home/anna/maturimate-adataim-2026-11-12.json"), settings.exportData())
        assertEquals("maturimate-adataim-2026-11-12.json", saved?.first)
        // the same data, one field per line, so a person can read the file
        val text = saved!!.second
        assertEquals(Json.parseToJsonElement(EXPORT), Json.parseToJsonElement(text))
        assertTrue("\n    \"profile\": {\n        \"first_name\": \"Anna\"" in text, text)
        val offline = AccountSettingsImpl(Auth(), profiles, localUser, dbDispatcher, account = Server(fails = true), exportSaver = saver)
        assertEquals(OperationResult.Error(AuthError.NETWORK), offline.exportData())
    }

    @Test
    fun aResetRemovesWhatWasDoneButKeepsTheStreakAndTheStudyTime() = runBlocking {
        val at = Instant.fromEpochMilliseconds(1_000)
        progress.saveUnitProgress(UnitProgress(50, UnitProgressStatus.COMPLETED, at, at))
        val attempt = (progress.startAttempt(AttemptType.UNIT_ROUND, null, 51, at) as OperationResult.Success).data
        progress.finishAttempt(attempt, AttemptStatus.FINISHED, at, 5, 5)
        progress.saveStreak(Streak(4, 7, LocalDate(2026, 11, 4)))
        progress.addActivity(DailyActivity(LocalDate(2026, 11, 4), secondsStudied = 600, tasksAnswered = 5))

        assertEquals(OperationResult.Success(Unit), progress.resetProgress())

        assertEquals(emptyList(), (progress.unitProgress() as OperationResult.Success).data)
        assertEquals(emptyList(), (progress.attempts() as OperationResult.Success).data)
        assertEquals(4, (progress.streak() as OperationResult.Success).data!!.currentStreak)
        val days = (progress.activity(LocalDate(2026, 11, 4), LocalDate(2026, 11, 4)) as OperationResult.Success).data
        assertEquals(600, days.single().secondsStudied)
        // the profile remembers when, so a sync can tell which progress is older than the reset
        assertEquals(1_000L, database.profileQueries.selectProfile(localUser.userId()).executeAsOneOrNull()?.progress_reset_at)
    }
}
