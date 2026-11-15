package com.madovygabor.maturimate.features.profileDetails.presentation

import com.madovygabor.maturimate.core.presentation.navigation.Route
import com.madovygabor.maturimate.features.auth.domain.SignedInAccount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A guest signs in from the profile: an account that is not set up (no TERMS consent) goes on to the account form. */
class ProfileSignInTest {

    @Test
    fun aSetUpAccountGoesBackToTheProfile() {
        assertNull(pageAfterSignIn(SignedInAccount(onboardingFinished = true, profile = null, isSetUp = true)))
    }

    // Without this the guest landed back on the profile, signed in, and the consents were never asked.
    @Test
    fun anAccountThatIsNotSetUpGoesToTheAccountForm() {
        assertEquals(Route.Main.Profile.SignUp, pageAfterSignIn(SignedInAccount(onboardingFinished = true, profile = null, isSetUp = false)))
        assertEquals(Route.Main.Profile.SignUp, pageAfterSignIn(SignedInAccount(onboardingFinished = false, profile = null, isSetUp = false)))
    }
}
