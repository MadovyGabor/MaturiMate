package com.madovygabor.maturimate

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
import com.madovygabor.maturimate.features.auth.domain.AccountSetup
import com.madovygabor.maturimate.features.auth.domain.AccountUser
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import com.madovygabor.maturimate.features.auth.domain.AuthState
import com.madovygabor.maturimate.features.learning.domain.ContentRepository
import com.madovygabor.maturimate.features.learning.domain.LearningError
import com.madovygabor.maturimate.features.learning.domain.model.Subject
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
import maturimate.composeapp.generated.resources.Res
import maturimate.composeapp.generated.resources.account_browse
import maturimate.composeapp.generated.resources.common_next
import maturimate.composeapp.generated.resources.date_title
import maturimate.composeapp.generated.resources.goal_title
import maturimate.composeapp.generated.resources.language_hu_desc
import maturimate.composeapp.generated.resources.language_title
import maturimate.composeapp.generated.resources.level_unknown
import maturimate.composeapp.generated.resources.plan_title
import maturimate.composeapp.generated.resources.reminder_not_now
import maturimate.composeapp.generated.resources.role_learner
import maturimate.composeapp.generated.resources.role_student
import maturimate.composeapp.generated.resources.role_title
import maturimate.composeapp.generated.resources.subject_mat
import maturimate.composeapp.generated.resources.subjects_title_learner
import maturimate.composeapp.generated.resources.subjects_title_student
import maturimate.composeapp.generated.resources.account_title
import maturimate.composeapp.generated.resources.common_save
import maturimate.composeapp.generated.resources.profile_title
import maturimate.composeapp.generated.resources.onboarding_step_goal
import maturimate.composeapp.generated.resources.onboarding_step_language
import maturimate.composeapp.generated.resources.onboarding_step_role
import maturimate.composeapp.generated.resources.onboarding_step_date
import maturimate.composeapp.generated.resources.language_sk_desc
import maturimate.composeapp.generated.resources.welcome_start
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

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
        override suspend fun resetPassword(email: String, code: String, newPassword: String) = ok
    }

    private object NoSetup : AccountSetup {
        override suspend fun completeSignUp(firstName: String, lastName: String, marketing: Boolean, finishedAt: Instant): OperationResult<Unit, AuthError> =
            OperationResult.Success(Unit)
        override suspend fun completeSignIn(): OperationResult<Boolean, AuthError> = OperationResult.Success(false)
    }

    // only subjects() is used; every other call fails
    private object StarterContent : ContentRepository by unsupportedContent {
        override suspend fun subjects(): OperationResult<List<Subject>, LearningError> =
            OperationResult.Success(listOf(Subject(1, "HU_MJL", "Magyar", "Maďarčina", Track.HU, null)))
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
                    single<SupabaseClient> {
                        createSupabaseClient("https://test.supabase.co", "test-key") {
                            httpEngine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }
                            install(Auth) {
                                sessionManager = MemorySessionManager()
                                codeVerifierCache = MemoryCodeVerifierCache()
                                autoLoadFromStorage = false
                                enableLifecycleCallbacks = false
                            }
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

    private fun ComposeUiTest.click(key: StringResource) = onNodeWithText(text(key)).performClick()

    private fun ComposeUiTest.waitFor(key: StringResource) = waitUntil(timeoutMillis = 5_000) {
        onAllNodes(androidx.compose.ui.test.hasText(text(key))).fetchSemanticsNodes().isNotEmpty()
    }

    /** From the welcome screen to the home screen as a Hungarian student who looks around as a guest. */
    private fun ComposeUiTest.goThroughAsStudent() {
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
        waitFor(Res.string.reminder_not_now)
        click(Res.string.reminder_not_now)
        waitFor(Res.string.plan_title)
        click(Res.string.common_next)
        waitFor(Res.string.account_title)
        click(Res.string.account_browse)

        // the home screen
        waitUntil(timeoutMillis = 5_000) { onAllNodes(androidx.compose.ui.test.hasText("Tantárgyak")).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun profile() = runBlocking { KoinPlatform.getKoin().get<UserProfileRepository>().profile.first() }!!

    @Test
    fun studentGoesThroughEveryStepAndLooksAroundAsAGuest() = runDesktopComposeUiTest(412, 915) {
        setContent { App() }
        goThroughAsStudent()

        val profile = profile()
        assertEquals(UserRole.STUDENT, profile.role)
        assertEquals(listOf(1L, 2L, 3L), profile.subjects.map { it.subjectId })
        assertEquals(10, profile.dailyGoalMinutes)
        assertTrue(profile.graduationDate != null && profile.isOnboardingCompleted)
        assertTrue(!profile.reminderEnabled)
    }

    @Test
    fun everyAnswerCanBeChangedInTheProfile() = runDesktopComposeUiTest(412, 915) {
        setContent { App() }
        goThroughAsStudent()
        onAllNodes(androidx.compose.ui.test.hasText("Profil")).onFirst().performClick()
        waitFor(Res.string.profile_title)

        // the goal opens its onboarding question, saved with "Mentés"
        click(Res.string.onboarding_step_goal)
        waitFor(Res.string.goal_title)
        onNodeWithText("20 perc").performClick()
        click(Res.string.common_save)
        waitFor(Res.string.profile_title)
        assertEquals(20, profile().dailyGoalMinutes)
        onNodeWithText("20 perc").assertExists()

        // a new language rebuilds the app in it, still on the profile
        click(Res.string.onboarding_step_language)
        waitFor(Res.string.language_title)
        click(Res.string.language_sk_desc)
        click(Res.string.common_save)
        Locale.setDefault(Locale.forLanguageTag("sk"))
        waitFor(Res.string.profile_title)
        assertEquals(TeachingLanguage.SK, profile().teachingLanguage)
    }

    @Test
    fun aNewRoleAsksForTheSubjectsAndWhatTheRoleStillNeeds() = runDesktopComposeUiTest(412, 915) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        setContent { App() }
        goThroughAsStudent()
        onAllNodes(androidx.compose.ui.test.hasText("Profil")).onFirst().performClick()
        waitFor(Res.string.profile_title)

        click(Res.string.onboarding_step_role)
        waitFor(Res.string.role_title)
        click(Res.string.role_learner)
        click(Res.string.common_save)
        waitFor(Res.string.subjects_title_learner)
        click(Res.string.subject_mat)
        click(Res.string.common_save)

        // a learner has everything else already: back to the answers, without an exam date
        waitFor(Res.string.profile_title)
        assertEquals(UserRole.LEARNER, profile().role)
        assertEquals(0, onAllNodes(androidx.compose.ui.test.hasText(text(Res.string.onboarding_step_date))).fetchSemanticsNodes().size)
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
