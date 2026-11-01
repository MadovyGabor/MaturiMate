package com.madovygabor.maturimate.features.auth.presentation

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.auth.domain.AccountSetup
import com.madovygabor.maturimate.features.auth.domain.AccountUser
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import com.madovygabor.maturimate.features.auth.domain.AuthState
import com.madovygabor.maturimate.features.userProfile.data.UserProfileRepositoryImpl
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.account_create
import maturimate.shared.generated.resources.account_consent_required
import maturimate.shared.generated.resources.account_google
import maturimate.shared.generated.resources.account_title
import maturimate.shared.generated.resources.code_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class, ExperimentalTime::class)
class AccountScreenTest {

    private val defaultLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun text(key: StringResource) = runBlocking { getString(key) }

    private class FakeAuth(var signUpResult: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)) : AuthRepository {
        var signedUp: Pair<String, String>? = null
        var confirmed: String? = null
        var user: AccountUser? = null
        private val ok: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        override val state: Flow<AuthState> = emptyFlow()
        override fun currentUser() = user
        override suspend fun signUp(email: String, password: String) = signUpResult.also { signedUp = email to password }
        override suspend fun confirmSignUp(email: String, code: String): OperationResult<Unit, AuthError> =
            if (code == "123456") ok.also { confirmed = code } else OperationResult.Error(AuthError.INVALID_CODE)
        override suspend fun resendSignUpCode(email: String) = ok
        override suspend fun signIn(email: String, password: String) = ok
        override suspend fun signOut() = ok
        override suspend fun sendPasswordResetCode(email: String) = ok
        override suspend fun resetPassword(email: String, code: String, newPassword: String) = ok
    }

    private class FakeSetup(val existingProfile: Boolean = false) : AccountSetup {
        var signedUp: Triple<String, String, Boolean>? = null
        override suspend fun completeSignUp(firstName: String, lastName: String, marketing: Boolean, finishedAt: Instant): OperationResult<Unit, AuthError> {
            signedUp = Triple(firstName, lastName, marketing)
            return OperationResult.Success(Unit)
        }
        override suspend fun completeSignIn(): OperationResult<Boolean, AuthError> = OperationResult.Success(existingProfile)
    }

    private fun profiles(): UserProfileRepositoryImpl {
        val database = UserDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) })
        return UserProfileRepositoryImpl(database, LocalUserProvider(database), Dispatchers.Unconfined)
    }

    private suspend fun AccountViewModel.idle() = withTimeout(5_000) { state.first { !it.busy } }

    private fun AccountViewModel.fill(email: String = "anna@example.com", password: String = "Passw0rdA") {
        onChanged(AccountField.LAST_NAME, "Kovács")
        onChanged(AccountField.FIRST_NAME, "Anna")
        onChanged(AccountField.EMAIL, email)
        onChanged(AccountField.PASSWORD, password)
        onConsentChanged(true)
    }

    @Test
    fun wrongFieldsAreMarkedAndNothingIsSent() = runBlocking {
        val auth = FakeAuth()
        val viewModel = AccountViewModel(auth, FakeSetup(), profiles(), SnackbarController())
        viewModel.fill(email = "anna", password = "short")

        viewModel.onCreateAccount()

        assertEquals(
            mapOf(AccountField.EMAIL to FieldError.EMAIL_INVALID, AccountField.PASSWORD to FieldError.PASSWORD_WEAK),
            viewModel.state.value.errors,
        )
        assertNull(auth.signedUp)
    }

    @Test
    fun signUpAsksForTheCodeThenSetsUpTheAccount() = runBlocking {
        val auth = FakeAuth()
        val setup = FakeSetup()
        val viewModel = AccountViewModel(auth, setup, profiles(), SnackbarController())
        viewModel.fill()
        viewModel.onNewsChanged(true)

        viewModel.onCreateAccount()
        assertEquals("anna@example.com", viewModel.idle().codeSentTo)

        viewModel.onCodeChanged("000000")
        viewModel.onConfirmCode()
        assertEquals(FieldError.CODE_INVALID, viewModel.idle().codeError)

        viewModel.onCodeChanged("123456")
        viewModel.onConfirmCode()
        viewModel.idle()
        assertEquals(Triple("Anna", "Kovács", true), setup.signedUp)
    }

    @Test
    fun takenAddressIsShownOnTheField() = runBlocking {
        val viewModel = AccountViewModel(FakeAuth(OperationResult.Error(AuthError.EMAIL_TAKEN)), FakeSetup(), profiles(), SnackbarController())
        viewModel.fill()

        viewModel.onCreateAccount()

        assertEquals(FieldError.EMAIL_TAKEN, viewModel.idle().errors[AccountField.EMAIL])
        assertNull(viewModel.state.value.codeSentTo)
    }

    @Test
    fun browsingAsAGuestFinishesTheOnboarding() = runBlocking {
        val profiles = profiles()
        val viewModel = AccountViewModel(FakeAuth(), FakeSetup(), profiles, SnackbarController())

        viewModel.onBrowseAsGuest()
        viewModel.idle()

        assertTrue(profiles.profile.first()!!.isOnboardingCompleted)
    }

    @Test
    fun googleAccountWithAProfileIsASignInAndANewOneKeepsTheForm() = runBlocking {
        val existing = FakeSetup(existingProfile = true)
        val signIn = AccountViewModel(FakeAuth(), existing, profiles(), SnackbarController())
        signIn.onGoogleSignedIn()
        signIn.idle()
        assertNull(existing.signedUp)

        val auth = FakeAuth().apply { user = AccountUser("id", "anna@gmail.com", "Anna", "Kovács") }
        val fresh = FakeSetup(existingProfile = false)
        val signUp = AccountViewModel(auth, fresh, profiles(), SnackbarController())
        signUp.onConsentChanged(true)
        signUp.onGoogleSignedIn()
        signUp.idle()
        // the name from Google and the consent are there, so the account is set up at once
        assertEquals(Triple("Anna", "Kovács", false), fresh.signedUp)
    }

    @Test
    fun newGoogleAccountWaitsForTheNameAndTheConsent() = runBlocking {
        val auth = FakeAuth().apply { user = AccountUser("id", "anna@gmail.com", "Anna", "Kovács") }
        val setup = FakeSetup(existingProfile = false)
        val viewModel = AccountViewModel(auth, setup, profiles(), SnackbarController())

        viewModel.onGoogleSignedIn()
        viewModel.idle()
        // back from Google: the name is filled in, but nothing is set up before the consent
        assertEquals("anna@gmail.com", viewModel.state.value.googleEmail)
        assertEquals("Anna", viewModel.state.value.firstName)
        assertNull(setup.signedUp)

        viewModel.onConsentChanged(true)
        viewModel.onCreateAccount()
        viewModel.idle()
        assertEquals(Triple("Anna", "Kovács", false), setup.signedUp)
    }

    @Test
    fun googleOpensBeforeTheConsentButTheSignUpAsksForIt() = runBlocking {
        val auth = FakeAuth()
        val viewModel = AccountViewModel(auth, FakeSetup(), profiles(), SnackbarController())
        var googleOpened = false

        viewModel.onGoogle { googleOpened = true }
        assertTrue(googleOpened)
        assertFalse(viewModel.state.value.consentMissing)

        viewModel.fill()
        viewModel.onConsentChanged(false)
        viewModel.onCreateAccount()
        assertNull(auth.signedUp)
        assertTrue(viewModel.state.value.consentMissing)

        viewModel.onConsentChanged(true)
        assertFalse(viewModel.state.value.consentMissing)
    }

    @Test
    fun theButtonsWorkBeforeTheConsentAndTheMissingConsentIsShown() = runComposeUiTest {
        setContent { AppTheme(darkTheme = false) { AccountContent(AccountUiState(consentMissing = true), UserRole.STUDENT, AccountActions()) } }
        onNodeWithText(text(Res.string.account_create)).assertIsEnabled()
        onNodeWithText(text(Res.string.account_google)).assertIsEnabled()
        onNodeWithText(text(Res.string.account_consent_required)).assertExists()
    }

    /** Pictures of the form and the code step, both themes and languages, into build/screenshots/account. */
    @Test
    fun rendersAtEverySizeAndTheme() {
        val sizes = mapOf("phone" to (412 to 915), "desktop" to (1280 to 800))
        val dir = File("build/screenshots/account").apply { mkdirs() }
        val form = AccountUiState(lastName = "Kovács", firstName = "Anna", email = "anna", consent = true, errors = mapOf(AccountField.EMAIL to FieldError.EMAIL_INVALID))
        val code = AccountUiState(codeSentTo = "anna@example.com", code = "1234")
        for (language in listOf("hu", "sk")) {
            Locale.setDefault(Locale.forLanguageTag(language))
            for ((name, size) in sizes) for (dark in listOf(false, true)) for ((step, state) in listOf("form" to form, "code" to code)) {
                runDesktopComposeUiTest(size.first, size.second) {
                    setContent { AppTheme(darkTheme = dark) { AccountContent(state, UserRole.STUDENT, AccountActions()) } }
                    onNodeWithText(text(if (step == "form") Res.string.account_title else Res.string.code_title)).assertExists()
                    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "account-$step-$language-$name-${if (dark) "dark" else "light"}.png"))
                }
            }
        }
    }
}
