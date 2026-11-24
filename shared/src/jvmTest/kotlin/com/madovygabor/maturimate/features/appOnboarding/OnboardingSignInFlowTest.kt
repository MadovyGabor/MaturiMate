package com.madovygabor.maturimate.features.appOnboarding

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.App
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.di.sharedModule
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.auth.domain.AccountSettings
import com.madovygabor.maturimate.features.auth.domain.AccountSetup
import com.madovygabor.maturimate.features.auth.domain.AccountUser
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import com.madovygabor.maturimate.features.auth.domain.AuthState
import com.madovygabor.maturimate.features.auth.domain.SignedInAccount
import com.madovygabor.maturimate.features.learning.domain.ContentRepository
import com.madovygabor.maturimate.features.learning.domain.LearningError
import com.madovygabor.maturimate.features.learning.domain.model.Subject
import com.madovygabor.maturimate.features.learning.domain.model.Topic
import com.madovygabor.maturimate.features.learning.domain.model.Track
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import com.madovygabor.maturimate.testutil.TEST_TIMEOUT
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.compose.auth.ComposeAuth
import io.github.jan.supabase.compose.auth.googleNativeLogin
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.account_email
import maturimate.shared.generated.resources.account_title
import maturimate.shared.generated.resources.common_back
import maturimate.shared.generated.resources.signin_replace_confirm
import maturimate.shared.generated.resources.signin_button
import maturimate.shared.generated.resources.signin_password
import maturimate.shared.generated.resources.welcome_have_account
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The sign-in ending of the onboarding (item #13), with the real navigation and `App` on an in-memory database: the
 * sign-in of an account changes the phone's user, and the app must land where the account belongs. The server is faked;
 * the fake sign-in setup writes into the database what the real one writes, in the app's database thread.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalTime::class)
class OnboardingSignInFlowTest {

    private val defaultLocale: Locale = Locale.getDefault()

    /** The account's setup after the sign-in: [setUp] tells whether the account finished its sign-up. */
    private class AccountSetupOf(private val setUp: Boolean) : AccountSetup {
        override suspend fun completeSignUp(firstName: String, lastName: String, marketing: Boolean, finishedAt: Instant): OperationResult<Unit, AuthError> =
            OperationResult.Success(Unit)

        // like the real one: the new user id rebuilds the screens and clears the sign-in's view model, the rest goes on
        override suspend fun completeSignIn(): OperationResult<SignedInAccount, AuthError> = withContext(NonCancellable) {
            val koin = KoinPlatform.getKoin()
            val profiles = koin.get<UserProfileRepository>()
            if (!setUp) {
                // an account that is not set up changes nothing: the phone stays the guest's, and the guest's answers lead on
                val local = profiles.profile.first()
                return@withContext OperationResult.Success(SignedInAccount(onboardingFinished = false, profile = local, isSetUp = false))
            }
            withContext(koin.get<CoroutineDispatcher>(named("userDatabase"))) {
                // the guest's rows become the account's: another user id on this phone
                koin.get<LocalUserProvider>().adoptAccount("account-1")
            }
            // the account's profile replaces the answers on this phone, the end of its onboarding last
            profiles.saveTeachingLanguage(TeachingLanguage.HU)
            profiles.saveRole(UserRole.STUDENT)
            profiles.saveSubjects(listOf(UserSubject(1, compulsory = true), UserSubject(5, compulsory = true), UserSubject(3, compulsory = false)))
            profiles.saveGraduationDate(LocalDate(2027, 5, 1))
            profiles.saveDailyGoal(10)
            profiles.completeOnboarding(Clock.System.now())
            OperationResult.Success(SignedInAccount(onboardingFinished = true, profile = profiles.profile.first(), isSetUp = true))
        }
    }

    private object AuthOk : AuthRepository {
        private val ok: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        override val state: Flow<AuthState> = emptyFlow()
        override fun currentUser(): AccountUser? = null
        override suspend fun signUp(email: String, password: String) = ok
        override suspend fun confirmSignUp(email: String, code: String) = ok
        override suspend fun resendSignUpCode(email: String) = ok
        override suspend fun signIn(email: String, password: String) = ok
        override suspend fun signOut() = ok
        override suspend fun sendPasswordResetCode(email: String) = ok
        override suspend fun verifyResetCode(email: String, code: String) = ok
        override suspend fun setNewPassword(newPassword: String) = ok
    }

    private object NoSettings : AccountSettings {
        override suspend fun saveNames(firstName: String, lastName: String): OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        override suspend fun sendPasswordCode(): OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        override suspend fun changePassword(oldPassword: String, code: String, newPassword: String): OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        override suspend fun signOut(force: Boolean): OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
    }

    // the subjects tab reads the subjects and their root topics; every other call fails
    private object StarterContent : ContentRepository by noContent {
        override suspend fun subjects(): OperationResult<List<Subject>, LearningError> =
            OperationResult.Success(listOf(Subject(1, "HU_MJL", "Magyar", "Maďarčina", Track.HU, null)))
        override suspend fun rootTopics(subjectId: Long): OperationResult<List<Topic>, LearningError> =
            OperationResult.Success(emptyList())
    }

    private fun start(setUp: Boolean) {
        // a Hungarian phone: the welcome screen and the sign-in are in Hungarian
        Locale.setDefault(Locale.forLanguageTag("hu"))
        startKoin {
            allowOverride(true)
            modules(
                sharedModule,
                module {
                    single { UserDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }) }
                    single<ContentRepository> { StarterContent }
                    single<AuthRepository> { AuthOk }
                    single<AccountSetup> { AccountSetupOf(setUp) }
                    single<AccountSettings> { NoSettings }
                    single<SupabaseClient> {
                        createSupabaseClient("https://test.supabase.co", "test-key") {
                            httpEngine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }
                            install(Auth) {
                                sessionManager = MemorySessionManager()
                                codeVerifierCache = MemoryCodeVerifierCache()
                                autoLoadFromStorage = false
                                enableLifecycleCallbacks = false
                            }
                            install(Postgrest)
                            install(ComposeAuth) { googleNativeLogin(serverClientId = "test") }
                        }
                    }
                },
            )
        }
    }

    @AfterTest
    fun stopApp() {
        stopKoin()
        Locale.setDefault(defaultLocale)
    }

    private fun text(key: StringResource) = runBlocking { getString(key) }

    private fun ComposeUiTest.waitFor(text: String) = waitUntil(timeoutMillis = TEST_TIMEOUT.inWholeMilliseconds) {
        onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun ComposeUiTest.waitFor(key: StringResource) = waitFor(text(key))

    /** Clicks the button with this text (the title of a screen can say the same words, but it cannot be clicked). */
    private fun ComposeUiTest.clickButton(key: StringResource) {
        val button = hasText(text(key)) and hasClickAction() and isEnabled()
        waitUntil(timeoutMillis = TEST_TIMEOUT.inWholeMilliseconds) { onAllNodes(button).fetchSemanticsNodes().isNotEmpty() }
        onNode(button).performClick()
    }

    /** From the welcome screen through "Már van fiókom" to the sign-in button. */
    private fun ComposeUiTest.signInFromTheWelcomeScreen() {
        waitFor(Res.string.welcome_have_account)
        clickButton(Res.string.welcome_have_account)
        waitFor(Res.string.signin_password)
        onNode(hasText(text(Res.string.account_email))).performTextInput("anna@example.com")
        onNode(hasText(text(Res.string.signin_password))).performTextInput("secret-1")
        clickButton(Res.string.signin_button)
    }

    private fun profile() = runBlocking { KoinPlatform.getKoin().get<UserProfileRepository>().profile.first() }!!

    // #13: the sign-in changes the phone's user while the onboarding is open; the app must not stay in the onboarding
    @Test
    fun signingInToASetUpAccountAtTheStartOfTheOnboardingGoesHome() = runDesktopComposeUiTest(412, 915) {
        start(setUp = true)
        setContent { App() }

        signInFromTheWelcomeScreen()

        // the home screen
        waitFor("Tantárgyak")
        assertTrue(profile().isOnboardingCompleted)
        assertEquals(UserRole.STUDENT, profile().role)
    }

    // an account without its sign-up consents changes nothing on the phone: the guest's answers lead to the account form
    @Test
    fun signingInToAnAccountThatIsNotSetUpGoesOnAtTheAccountStepWithTheGuestsAnswers() = runDesktopComposeUiTest(412, 915) {
        start(setUp = false)
        runBlocking {
            val profiles = KoinPlatform.getKoin().get<UserProfileRepository>()
            profiles.saveTeachingLanguage(TeachingLanguage.HU)
            profiles.saveRole(UserRole.STUDENT)
            profiles.saveSubjects(listOf(UserSubject(1, compulsory = true), UserSubject(5, compulsory = true), UserSubject(3, compulsory = false)))
            profiles.saveGraduationDate(LocalDate(2027, 5, 1))
            profiles.saveDailyGoal(10)
        }
        setContent { App() }
        // the onboarding resumed from the answers: Back, back to the welcome screen
        val back = hasContentDescription(text(Res.string.common_back))
        waitUntil(timeoutMillis = TEST_TIMEOUT.inWholeMilliseconds) {
            onAllNodes(hasText(text(Res.string.welcome_have_account))).fetchSemanticsNodes().isNotEmpty() ||
                onAllNodes(back).fetchSemanticsNodes().isNotEmpty()
        }
        repeat(10) {
            if (onAllNodes(hasText(text(Res.string.welcome_have_account))).fetchSemanticsNodes().isEmpty()) {
                onAllNodes(back).onFirst().performClick()
                waitForIdle()
            }
        }

        signInFromTheWelcomeScreen()
        // the answers on this phone are replaced by the account's: asked first
        clickButton(Res.string.signin_replace_confirm)

        waitFor(Res.string.account_title)
        assertFalse(profile().isOnboardingCompleted)
    }
}

/** A content repository whose calls fail, under the one call a test needs. */
private val noContent: ContentRepository = java.lang.reflect.Proxy.newProxyInstance(
    ContentRepository::class.java.classLoader,
    arrayOf(ContentRepository::class.java),
    java.lang.reflect.InvocationHandler { _, method, _ -> throw UnsupportedOperationException(method.name) },
) as ContentRepository
