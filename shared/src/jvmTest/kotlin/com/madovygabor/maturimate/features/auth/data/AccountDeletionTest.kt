package com.madovygabor.maturimate.features.auth.data

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.DeletionProof
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class AccountDeletionTest : AccountSettingsImplTestBase() {

    @Test
    fun deletingTheAccountOnAGuestPhoneKeepsTheGuestsData() = runBlocking {
        guestPhone()
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val guest = localUser.userId()
        val auth = Auth()
        val server = Server()

        assertEquals(OperationResult.Success(Unit), AccountSettingsImpl(auth, profiles, localUser, dbDispatcher, account = server).deleteAccount(DeletionProof.Password("Old12345")))

        assertTrue(server.deleted)
        assertTrue(auth.localSessionCleared)
        assertEquals(guest, localUser.userId())
        assertEquals(TeachingLanguage.HU, profiles.profile.first()!!.teachingLanguage)
    }

    @Test
    fun aDeletedAccountLeavesNothingOnThisPhone() = runBlocking {
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val auth = Auth()
        val server = Server()
        val settings = AccountSettingsImpl(auth, profiles, localUser, dbDispatcher, account = server)

        assertEquals(OperationResult.Success(Unit), settings.deleteAccount(DeletionProof.Password("Old12345")))

        assertTrue(server.deleted)
        // the server no longer knows the user: only the local session goes, no server sign-out
        assertTrue(auth.localSessionCleared)
        assertTrue(!auth.signedOut)
        assertNull(profiles.profile.first())
        assertTrue(localUser.isGuest())
    }

    @Test
    fun withoutANetworkNothingIsDeleted() = runBlocking {
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val settings = AccountSettingsImpl(Auth(), profiles, localUser, dbDispatcher, account = Server(fails = true))

        assertEquals(OperationResult.Error(AuthError.NETWORK), settings.deleteAccount(DeletionProof.Password("Old12345")))
        assertEquals(TeachingLanguage.HU, profiles.profile.first()!!.teachingLanguage)
    }

    // Card 11: the deletion asked only for a typed word; a phone left open was enough to delete the account for good.
    @Test
    fun aDeletionWithoutTheRightPasswordDeletesNothing() = runBlocking {
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val server = Server()
        val auth = Auth()
        val settings = AccountSettingsImpl(auth, profiles, localUser, dbDispatcher, account = server)

        assertEquals(OperationResult.Error(AuthError.INVALID_CREDENTIALS), settings.deleteAccount(DeletionProof.Password("Wrong123")))
        assertEquals(OperationResult.Error(AuthError.INVALID_CREDENTIALS), settings.deleteAccount(DeletionProof.Password(" ")))
        // an account with a password cannot use the Google proof instead
        assertEquals(OperationResult.Error(AuthError.PROOF_NEEDED), settings.deleteAccount(DeletionProof.FreshSignIn))
        auth.rateLimited = true
        assertEquals(OperationResult.Error(AuthError.TOO_MANY_REQUESTS), settings.deleteAccount(DeletionProof.Password("Old12345")))

        assertTrue(!server.deleted)
        assertEquals(TeachingLanguage.HU, profiles.profile.first()!!.teachingLanguage)
        assertTrue(!localUser.isGuest())
    }

    @Test
    fun aDeletionTheServerFindsUnprovenDeletesNothingHere() = runBlocking {
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val auth = Auth()
        val settings = AccountSettingsImpl(auth, profiles, localUser, dbDispatcher, account = Server(refusal = AuthError.PROOF_NEEDED))

        assertEquals(OperationResult.Error(AuthError.PROOF_NEEDED), settings.deleteAccount(DeletionProof.Password("Old12345")))

        assertTrue(!auth.localSessionCleared)
        assertEquals(TeachingLanguage.HU, profiles.profile.first()!!.teachingLanguage)
        assertTrue(!localUser.isGuest())
    }
}
