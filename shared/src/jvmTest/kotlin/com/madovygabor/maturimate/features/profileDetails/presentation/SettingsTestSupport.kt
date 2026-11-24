package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.utils.UiText
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.auth.domain.AccountSettings
import com.madovygabor.maturimate.features.auth.domain.AccountUser
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import com.madovygabor.maturimate.features.auth.domain.DeletionProof
import com.madovygabor.maturimate.features.learning.domain.LearningError
import com.madovygabor.maturimate.features.learning.domain.ProgressRepository
import com.madovygabor.maturimate.features.userProfile.data.UserProfileRepositoryImpl
import com.madovygabor.maturimate.features.userProfile.domain.ExamDateRepository
import com.madovygabor.maturimate.features.userProfile.domain.OfficialExamDate
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import com.madovygabor.maturimate.testutil.TEST_TIMEOUT
import com.madovygabor.maturimate.testutil.awaitFirst
import com.madovygabor.maturimate.testutil.emitsNothingWithin
import com.madovygabor.maturimate.testutil.testDbDispatcher
import java.lang.reflect.Proxy
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate

/** The account half of the settings, faked: whether this phone is a guest's, and who is signed in. */
internal class GuestPhone(private val guest: Boolean) : com.madovygabor.maturimate.features.auth.domain.AccountSettings {
    private val ok: com.madovygabor.maturimate.core.domain.error.OperationResult<Unit, com.madovygabor.maturimate.features.auth.domain.AuthError> = com.madovygabor.maturimate.core.domain.error.OperationResult.Success(Unit)
    override suspend fun isGuestPhone() = guest
    override suspend fun saveNames(firstName: String, lastName: String) = ok
    override suspend fun sendPasswordCode() = ok
    override suspend fun changePassword(oldPassword: String, code: String, newPassword: String) = ok
    override suspend fun signOut(force: Boolean) = ok
}

/** Who is signed in: [user], or nobody (a guest). */
internal open class SignedIn(private val user: AccountUser? = AccountUser("account-y", "bea@example.com", "Bea", "Nagy")) : AuthRepository {
    private val ok: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
    override val state = kotlinx.coroutines.flow.emptyFlow<com.madovygabor.maturimate.features.auth.domain.AuthState>()
    override fun currentUser() = user
    override suspend fun signUp(email: String, password: String) = ok
    override suspend fun confirmSignUp(email: String, code: String) = ok
    override suspend fun resendSignUpCode(email: String) = ok
    override suspend fun signIn(email: String, password: String) = ok
    override suspend fun signOut() = ok
    override suspend fun sendPasswordResetCode(email: String) = ok
    override suspend fun verifyResetCode(email: String, code: String) = ok
    override suspend fun setNewPassword(newPassword: String) = ok
}

/** The account half of the settings, faked: each answer can be set, each call is remembered. */
internal class FakeAccount : AccountSettings {
    var canShare = false
    var namesResult: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
    var codeResult: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
    var changeResult: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
    var deleteResult: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
    var exportResult: OperationResult<String, AuthError> = OperationResult.Success("maturimate.json")
    var codesSent = 0
    /** The old password, the code and the new password of the last change. */
    @Volatile var changed: Triple<String, String, String>? = null
    var deletes = 0
    val proofs = mutableListOf<DeletionProof>()
    var exportedAsShare: Boolean? = null
    var signOutResult: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
    @Volatile var proofsStarted = 0
    var freshSignIn: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
    override val canShareExport get() = canShare
    override suspend fun isGuestPhone() = false
    override suspend fun saveNames(firstName: String, lastName: String) = namesResult
    override suspend fun sendPasswordCode(): OperationResult<Unit, AuthError> { codesSent++; return codeResult }
    override suspend fun changePassword(oldPassword: String, code: String, newPassword: String): OperationResult<Unit, AuthError> {
        changed = Triple(oldPassword, code, newPassword)
        return changeResult
    }
    override suspend fun signOut(force: Boolean): OperationResult<Unit, AuthError> = signOutResult
    override suspend fun deleteAccount(proof: DeletionProof): OperationResult<Unit, AuthError> { deletes++; proofs += proof; return deleteResult }
    var startResult: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
    @Volatile var cancels = 0
    @Volatile var foreignChecks = 0
    override suspend fun startFreshSignInProof(): OperationResult<Unit, AuthError> { proofsStarted++; return startResult }
    override suspend fun finishFreshSignInProof() = freshSignIn
    override suspend fun cancelFreshSignInProof() { cancels++ }
    override suspend fun endForeignSession(): Boolean { foreignChecks++; return false }
    override suspend fun exportData(share: Boolean): OperationResult<String, AuthError> { exportedAsShare = share; return exportResult }
}

internal object NoExamDates : ExamDateRepository {
    override val dates = flowOf(emptyList<OfficialExamDate>())
    override suspend fun isAnswered(date: OfficialExamDate) = false
    override suspend fun markAnswered(date: OfficialExamDate) = Unit
}

@OptIn(ExperimentalTestApi::class)
abstract class SettingsTestBase {

    internal val defaultLocale: Locale = Locale.getDefault()
    internal val today = LocalDate(2026, 10, 31)

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    internal fun repository(): UserProfileRepositoryImpl {
        val database = UserDatabase(
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
        )
        return UserProfileRepositoryImpl(database, LocalUserProvider(database), testDbDispatcher())
    }

    internal suspend fun student(repository: UserProfileRepositoryImpl) = repository.apply {
        saveTeachingLanguage(TeachingLanguage.HU)
        saveRole(UserRole.STUDENT)
        saveSubjects(listOf(UserSubject(1, true), UserSubject(5, true), UserSubject(6, true)))
        saveGraduationDate(LocalDate(2027, 3, 15))
        saveDailyGoal(10)
    }

    /**
     * The settings' view model with a fake for each dependency (C13: none of them may be missing). By default a guest's
     * phone: nobody signed in.
     */
    internal fun settings(
        repository: UserProfileRepository,
        auth: AuthRepository = SignedIn(user = null),
        account: AccountSettings = GuestPhone(guest = true),
        progress: ProgressRepository = progress(OperationResult.Success(Unit)),
        snackbar: SnackbarController = SnackbarController(),
        examDates: ExamDateRepository = NoExamDates,
    ) = SettingsViewModel(repository, auth, account, progress, snackbar, examDates, fixedToday = today)

    internal suspend fun SettingsViewModel.loaded() = state.awaitFirst { it?.profile?.role != null }!!

    internal val ok: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)

    /** A progress repository that only knows the reset (a proxy: the interface is long, the settings use one call). */
    internal fun progress(result: OperationResult<Unit, LearningError>): ProgressRepository =
        Proxy.newProxyInstance(ProgressRepository::class.java.classLoader, arrayOf(ProgressRepository::class.java)) { _, method, _ ->
            if (method.name == "resetProgress") result else error("the settings do not call ${method.name}")
        } as ProgressRepository

    internal fun viewModel(
        account: FakeAccount = FakeAccount(),
        progress: ProgressRepository = progress(OperationResult.Success(Unit)),
        repository: UserProfileRepository = runBlocking { student(repository()) },
        snackbar: SnackbarController = SnackbarController(),
        user: AccountUser = AccountUser("account-y", "bea@example.com", "Bea", "Nagy"),
    ) = settings(repository, auth = SignedIn(user), account = account, progress = progress, snackbar = snackbar)

    internal suspend fun SnackbarController.next(): UiText = events.awaitFirst().message

    internal suspend fun SnackbarController.isQuiet() = events.emitsNothingWithin(300.milliseconds)

    internal fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = TEST_TIMEOUT.inWholeMilliseconds) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    internal fun ComposeUiTest.waitForContentDescription(text: String) =
        waitUntil(timeoutMillis = TEST_TIMEOUT.inWholeMilliseconds) { onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty() }

    internal fun said(res: org.jetbrains.compose.resources.StringResource, vararg args: Any) = UiText.StringRes(res, arrayOf(*args))
}
