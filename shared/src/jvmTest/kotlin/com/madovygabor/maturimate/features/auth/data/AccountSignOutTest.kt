package com.madovygabor.maturimate.features.auth.data

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.sync.domain.SyncReason
import com.madovygabor.maturimate.features.sync.domain.SyncEngine
import com.madovygabor.maturimate.features.sync.domain.SyncError
import com.madovygabor.maturimate.features.sync.domain.SyncResponse
import com.madovygabor.maturimate.features.sync.domain.SyncSession
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgress
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgressStatus
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class AccountSignOutTest : AccountSettingsImplTestBase() {

    @Test
    fun aSignOutRemovesEverythingAndANewGuestStarts() = runBlocking {
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        progress.saveUnitProgress(UnitProgress(50, UnitProgressStatus.COMPLETED, Instant.fromEpochMilliseconds(1), Instant.fromEpochMilliseconds(1)))
        val before = localUser.userId()
        val auth = Auth()

        assertEquals(OperationResult.Success(Unit), settings(auth).signOut())
        assertTrue(auth.signedOut)
        assertTrue(localUser.userId() != before && localUser.isGuest())
        // the new guest has no profile: the app starts with the welcome screen
        assertNull(profiles.profile.first())
        assertEquals(emptyList(), (progress.unitProgress() as OperationResult.Success).data)
    }

    // N1: on a guest phone the session is not this phone's account; the sign-out ends the session and keeps the guest's
    // data, which was never synced anywhere.
    @Test
    fun aSignOutOnAGuestPhoneEndsTheSessionAndKeepsTheGuestsData() = runBlocking {
        guestPhone()
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        progress.saveUnitProgress(UnitProgress(50, UnitProgressStatus.COMPLETED, Instant.fromEpochMilliseconds(1), Instant.fromEpochMilliseconds(1)))
        val guest = localUser.userId()
        val auth = Auth()

        assertEquals(OperationResult.Success(Unit), settings(auth).signOut())

        assertTrue(auth.signedOut)
        assertEquals(guest, localUser.userId())
        assertEquals(TeachingLanguage.HU, profiles.profile.first()!!.teachingLanguage)
        assertEquals(1, (progress.unitProgress() as OperationResult.Success).data.size)
    }

    @Test
    fun theSettingsKnowAGuestPhone() = runBlocking {
        assertTrue(!settings().isGuestPhone())
        guestPhone()
        assertTrue(settings().isGuestPhone())
    }

    @Test
    fun aSignOutUploadsFirstAndKeepsEverythingWhenTheUploadFails() = runBlocking {
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val auth = Auth()
        val repo = Repo(OperationResult.Error(SyncError.NETWORK))
        val before = localUser.userId()

        assertEquals(OperationResult.Error(AuthError.NETWORK), settings(auth, engine(repo)).signOut())

        assertTrue(!auth.signedOut)
        assertEquals(before, localUser.userId())
        assertEquals(TeachingLanguage.HU, profiles.profile.first()!!.teachingLanguage)
    }

    @Test
    fun aSignOutAfterAGoodUploadRemovesTheRows() = runBlocking {
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val auth = Auth()
        val repo = Repo(OperationResult.Success(SyncResponse(serverTime = 1_000, cursor = "c")))

        assertEquals(OperationResult.Success(Unit), settings(auth, engine(repo)).signOut())

        assertTrue(auth.signedOut)
        assertNull(profiles.profile.first())
        // the sync state went with the user (this test's session always says "signed in", so the new guest may have an empty one)
        val state = database.syncQueries.selectSyncState().executeAsOneOrNull()
        assertTrue(state == null || (state.user_id == localUser.userId() && state.pull_cursor == null))
    }

    @Test
    fun theSyncStateOfTheOldAccountGoesWithTheSignOut() = runBlocking {
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        // signed in while the phone is not a guest's
        val session = object : SyncSession {
            override suspend fun isSignedIn() = !localUser.isGuest()
            override suspend fun deviceId() = "dev"
        }
        localUser.adoptAccount("account-1")
        val engine = SyncEngine(Repo(OperationResult.Error(SyncError.NETWORK)), store, session, now = { 1_000 })
        engine.sync(com.madovygabor.maturimate.features.sync.domain.SyncReason.MANUAL)
        assertTrue(engine.state.value.signedIn)

        assertEquals(OperationResult.Success(Unit), settings(Auth(), engine).signOut(force = true))

        // the new guest sees no sync state and no cloud badge of the old account
        assertTrue(!engine.state.value.signedIn)
        assertNull(engine.state.value.lastError)
    }

    @Test
    fun theUserCanSignOutAnywayAndLoseWhatWasNotUploaded() = runBlocking {
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val auth = Auth()
        val repo = Repo(OperationResult.Error(SyncError.NETWORK))

        assertEquals(OperationResult.Success(Unit), settings(auth, engine(repo)).signOut(force = true))

        assertTrue(auth.signedOut)
        assertNull(profiles.profile.first())
    }
}
