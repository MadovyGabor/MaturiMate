package com.madovygabor.maturimate.features.appOnboarding.presentation

import com.madovygabor.maturimate.core.presentation.navigation.Route
import com.madovygabor.maturimate.features.auth.domain.SignedInAccount
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime

/** "Már van fiókom" in the onboarding: where it goes on after the sign-in (F2-F4). */
@OptIn(ExperimentalTime::class)
class OnboardingSignInNavigationTest {

    private val answered = UserProfile(
        teachingLanguage = TeachingLanguage.HU,
        role = UserRole.STUDENT,
        graduationDate = LocalDate(2027, 5, 1),
        uiLanguage = TeachingLanguage.HU,
        dailyGoalMinutes = 10,
        timezone = "Europe/Bratislava",
        onboardingCompletedAt = null,
        subjects = listOf(UserSubject(1, compulsory = true)),
    )

    @Test
    fun aSetUpAccountGoesHomeByItself() {
        assertNull(stackAfterSignIn(SignedInAccount(onboardingFinished = true, profile = answered, isSetUp = true)))
    }

    // An account that is not set up must give the consents: the guest's answered onboarding goes on at the account form.
    @Test
    fun anAccountThatIsNotSetUpLandsOnTheAccountStep() {
        val stack = stackAfterSignIn(SignedInAccount(onboardingFinished = false, profile = answered, isSetUp = false))

        assertEquals(Route.AppOnboarding.Account, stack?.last())
    }

    // With questions still open, the onboarding goes on from the first open one (it ends at the account step).
    @Test
    fun anAccountThatIsNotSetUpWithOpenQuestionsGoesOnFromThem() {
        val partly = answered.copy(subjects = emptyList())

        val stack = stackAfterSignIn(SignedInAccount(onboardingFinished = false, profile = partly, isSetUp = false))

        assertEquals(onboardingBackStack(partly), stack)
        assertEquals(Route.AppOnboarding.Subjects, stack?.last())
    }
}
