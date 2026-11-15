package com.madovygabor.maturimate

import com.madovygabor.maturimate.features.auth.domain.SignedInAccount
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
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
import com.madovygabor.maturimate.features.learning.domain.ContentRepository
import com.madovygabor.maturimate.features.learning.domain.LearningError
import com.madovygabor.maturimate.features.learning.domain.model.Subject
import com.madovygabor.maturimate.features.learning.domain.model.Topic
import com.madovygabor.maturimate.features.learning.domain.model.Track
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.account_browse
import maturimate.shared.generated.resources.common_back
import maturimate.shared.generated.resources.common_next
import maturimate.shared.generated.resources.date_title
import maturimate.shared.generated.resources.goal_title
import maturimate.shared.generated.resources.language_hu_desc
import maturimate.shared.generated.resources.language_title
import maturimate.shared.generated.resources.level_unknown
import maturimate.shared.generated.resources.plan_title
import maturimate.shared.generated.resources.role_learner
import maturimate.shared.generated.resources.role_student
import maturimate.shared.generated.resources.role_title
import maturimate.shared.generated.resources.subject_mat
import maturimate.shared.generated.resources.subjects_title_learner
import maturimate.shared.generated.resources.subjects_title_student
import maturimate.shared.generated.resources.account_title
import maturimate.shared.generated.resources.common_save
import maturimate.shared.generated.resources.profile_settings
import maturimate.shared.generated.resources.language_sk
import maturimate.shared.generated.resources.settings_app_language
import maturimate.shared.generated.resources.onboarding_step_goal
import maturimate.shared.generated.resources.onboarding_step_language
import maturimate.shared.generated.resources.onboarding_step_role
import maturimate.shared.generated.resources.onboarding_step_date
import maturimate.shared.generated.resources.language_sk_desc
import maturimate.shared.generated.resources.welcome_start
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import maturimate.shared.generated.resources.practice_free_title

/**
 * The whole app from the welcome screen to the home screen, with the real navigation and
 * ViewModels on an in-memory database; the server and the content are faked.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalTime::class)
class OnboardingFlowTest {

    private val defaultLocale: Locale = Locale.getDefault()

    private object NoAuth : AuthRepository {
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

    /** The settings' account changes; the flow does not use them. */
    private object NoSettings : AccountSettings {
        override suspend fun saveNames(firstName: String, lastName: String): OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        override suspend fun sendPasswordCode(): OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        override suspend fun changePassword(oldPassword: String, code: String, newPassword: String): OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        override suspend fun signOut(force: Boolean): OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
    }

    private object NoSetup : AccountSetup {
        override suspend fun completeSignUp(firstName: String, lastName: String, marketing: Boolean, finishedAt: Instant): OperationResult<Unit, AuthError> =
            OperationResult.Success(Unit)
        override suspend fun completeSignIn(): OperationResult<SignedInAccount, AuthError> = OperationResult.Success(SignedInAccount(false, null, isSetUp = false))
    }

    // the subject list and the subjects tab read the subjects and their root topics; every other call fails
    private object StarterContent : ContentRepository by unsupportedContent {
        override suspend fun subjects(): OperationResult<List<Subject>, LearningError> =
            OperationResult.Success(listOf(Subject(1, "HU_MJL", "Magyar", "Maďarčina", Track.HU, null)))
        override suspend fun rootTopics(subjectId: Long): OperationResult<List<Topic>, LearningError> =
            OperationResult.Success(emptyList())
    }

    @BeforeTest
    fun startApp() {
        // a phone set to English: the app starts in Slovak
        Locale.setDefault(Locale.ENGLISH)
        startKoin {
            allowOverride(true)
            modules(
                sharedModule,
                module {
                    single { UserDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }) }
                    single<ContentRepository> { StarterContent }
                    single<AuthRepository> { NoAuth }
                    single<AccountSetup> { NoSetup }
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

    // A button of a question is disabled until its saved answer is read (on the database's thread): a click right after the
    // title appears would do nothing, and the flow would wait for the next screen for ever
    private fun ComposeUiTest.click(key: StringResource) {
        val enabled = androidx.compose.ui.test.hasText(text(key)) and androidx.compose.ui.test.isEnabled()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(enabled).fetchSemanticsNodes().isNotEmpty() }
        onNode(enabled).performClick()
    }

    private fun ComposeUiTest.waitFor(key: StringResource) = waitUntil(timeoutMillis = 5_000) {
        onAllNodes(androidx.compose.ui.test.hasText(text(key))).fetchSemanticsNodes().isNotEmpty()
    }

    /** From the welcome screen to the last step (the account) as a Hungarian student. */
    private fun ComposeUiTest.goToAccountStep() {
        waitFor(Res.string.welcome_start)
        click(Res.string.welcome_start)
        waitFor(Res.string.language_title)
        // the options are written in their own language whatever the app language is
        click(Res.string.language_hu_desc)
        click(Res.string.common_next)

        // Hungarian is saved: the screens are rebuilt in Hungarian and the onboarding goes on
        Locale.setDefault(Locale.forLanguageTag("hu"))
        waitFor(Res.string.role_title)
        click(Res.string.role_student)
        click(Res.string.common_next)

        waitFor(Res.string.subjects_title_student)
        click(Res.string.level_unknown)
        click(Res.string.common_next)

        waitFor(Res.string.date_title)
        click(Res.string.common_next)
        waitFor(Res.string.goal_title)
        click(Res.string.common_next)
        // the desktop app has no reminder: the goal is followed by the plan, and nothing saves a reminder
        waitFor(Res.string.plan_title)
        click(Res.string.common_next)
        waitFor(Res.string.account_title)
    }

    /** From the welcome screen to the home screen as a Hungarian student who looks around as a guest. */
    private fun ComposeUiTest.goThroughAsStudent() {
        goToAccountStep()
        click(Res.string.account_browse)

        // the home screen
        waitUntil(timeoutMillis = 5_000) { onAllNodes(androidx.compose.ui.test.hasText("Tantárgyak")).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun profile() = runBlocking { KoinPlatform.getKoin().get<UserProfileRepository>().profile.first() }!!

    /** The profile tab, then its gear: the onboarding answers (the settings screen from 9.4 on). */
    private fun ComposeUiTest.openAnswers() {
        onAllNodes(androidx.compose.ui.test.hasText("Profil")).onFirst().performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodes(androidx.compose.ui.test.hasContentDescription(text(Res.string.profile_settings))).fetchSemanticsNodes().isNotEmpty()
        }
        onNode(androidx.compose.ui.test.hasContentDescription(text(Res.string.profile_settings))).performClick()
        waitFor(Res.string.settings_app_language)
    }

    @Test
    fun studentGoesThroughEveryStepAndLooksAroundAsAGuest() = runDesktopComposeUiTest(412, 915) {
        setContent { App() }
        goThroughAsStudent()

        val profile = profile()
        assertEquals(UserRole.STUDENT, profile.role)
        assertEquals(listOf(1L, 5L, 3L), profile.subjects.map { it.subjectId })
        assertEquals(10, profile.dailyGoalMinutes)
        assertTrue(profile.graduationDate != null && profile.isOnboardingCompleted)
        assertTrue(!profile.reminderEnabled)
    }

    @Test
    fun everyAnswerCanBeChangedInTheProfile() = runDesktopComposeUiTest(412, 915) {
        setContent { App() }
        goThroughAsStudent()
        // the tabs keep working after the first switch
        onAllNodes(androidx.compose.ui.test.hasText("Gyakorlás")).onFirst().performClick()
        waitFor(Res.string.practice_free_title)
        openAnswers()

        // the goal opens its onboarding question, saved with "Mentés"
        click(Res.string.onboarding_step_goal)
        waitFor(Res.string.goal_title)
        onNodeWithText("20 perc").performClick()
        click(Res.string.common_save)
        waitFor(Res.string.settings_app_language)
        assertEquals(20, profile().dailyGoalMinutes)
        onNodeWithText("20 perc").assertExists()

        // a new app language rebuilds the app in it, still on the settings; the school's language stays
        click(Res.string.settings_app_language)
        click(Res.string.language_sk)
        Locale.setDefault(Locale.forLanguageTag("sk"))
        waitFor(Res.string.settings_app_language)
        assertEquals(TeachingLanguage.SK, profile().uiLanguage)
        assertEquals(TeachingLanguage.HU, profile().teachingLanguage)
    }

    @Test
    fun aNewRoleAsksForTheSubjectsAndWhatTheRoleStillNeeds() = runDesktopComposeUiTest(412, 915) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        setContent { App() }
        goThroughAsStudent()
        openAnswers()

        click(Res.string.onboarding_step_role)
        waitFor(Res.string.role_title)
        click(Res.string.role_learner)
        click(Res.string.common_save)
        waitFor(Res.string.subjects_title_learner)
        click(Res.string.subject_mat)
        click(Res.string.common_save)

        // a learner has everything else already: back to the answers, without an exam date
        waitFor(Res.string.settings_app_language)
        assertEquals(UserRole.LEARNER, profile().role)
        assertEquals(0, onAllNodes(androidx.compose.ui.test.hasText(text(Res.string.onboarding_step_date))).fetchSemanticsNodes().size)
    }

    // #6: the back stack is kept over the rebuild; the new language continues with the role, then asks the subjects again
    @Test
    fun aLanguageChangeOnTheWayBackContinuesWithTheRoleAndAsksTheSubjectsAgain() = runDesktopComposeUiTest(412, 915) {
        setContent { App() }
        goToAccountStep()

        // Back to the language question, six steps up (no reminder step on the desktop)
        repeat(6) { onAllNodes(androidx.compose.ui.test.hasContentDescription(text(Res.string.common_back))).onFirst().performClick() }
        waitFor(Res.string.language_title)
        click(Res.string.language_sk_desc)
        click(Res.string.common_next)

        // the school's language changed: the screens are rebuilt in Slovak and the onboarding goes on with the role
        Locale.setDefault(Locale.forLanguageTag("sk"))
        waitFor(Res.string.role_title)
        assertEquals(TeachingLanguage.SK, profile().teachingLanguage)
        assertEquals(UserRole.STUDENT, profile().role)
        assertTrue(profile().subjects.isEmpty())
        click(Res.string.role_student)
        click(Res.string.common_next)

        // the subjects are asked again, none is selected
        waitFor(Res.string.subjects_title_student)
        assertTrue(profile().subjects.isEmpty())
        click(Res.string.level_unknown)
        click(Res.string.common_next)
        waitFor(Res.string.date_title)
    }

    @Test
    fun learnerSkipsTheExamDate() = runDesktopComposeUiTest(412, 915) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        setContent { App() }

        waitFor(Res.string.welcome_start)
        click(Res.string.welcome_start)
        waitFor(Res.string.language_title)
        click(Res.string.language_hu_desc)
        click(Res.string.common_next)
        waitFor(Res.string.role_title)
        click(Res.string.role_learner)
        click(Res.string.common_next)
        waitFor(Res.string.subjects_title_learner)
        click(Res.string.subject_mat)
        click(Res.string.common_next)

        // no exam date for a learner: the daily goal comes next
        waitFor(Res.string.goal_title)
        assertEquals(0, onAllNodes(androidx.compose.ui.test.hasText(text(Res.string.date_title))).fetchSemanticsNodes().size)
    }
}

/** A content repository whose calls fail, under the one call a test needs. */
private val unsupportedContent: ContentRepository = java.lang.reflect.Proxy.newProxyInstance(
    ContentRepository::class.java.classLoader,
    arrayOf(ContentRepository::class.java),
    java.lang.reflect.InvocationHandler { _, method, _ -> throw UnsupportedOperationException(method.name) },
) as ContentRepository
