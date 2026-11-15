package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.utils.UiText
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.auth.domain.AccountSettings
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import com.madovygabor.maturimate.features.auth.domain.DeletionProof
import com.madovygabor.maturimate.features.auth.domain.ExportSaver
import com.madovygabor.maturimate.features.auth.presentation.toUiText
import com.madovygabor.maturimate.features.learning.domain.LearningError
import com.madovygabor.maturimate.features.learning.domain.ProgressRepository
import com.madovygabor.maturimate.features.userProfile.domain.DataError
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import java.lang.reflect.Proxy
import maturimate.shared.generated.resources.error_io
import maturimate.shared.generated.resources.settings_export_done
import maturimate.shared.generated.resources.settings_export_shared
import maturimate.shared.generated.resources.settings_password_done
import maturimate.shared.generated.resources.settings_reset_done
import androidx.compose.ui.test.captureToImage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingStep
import com.madovygabor.maturimate.features.auth.domain.AccountUser
import com.madovygabor.maturimate.features.userProfile.data.UserProfileRepositoryImpl
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.ExamDateRepository
import com.madovygabor.maturimate.features.userProfile.domain.ExamDates
import com.madovygabor.maturimate.features.userProfile.domain.OfficialExamDate
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.daysUntil
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.settings_group_learning
import org.jetbrains.compose.resources.getString
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Clock
import com.madovygabor.maturimate.features.sync.domain.SyncState
import com.madovygabor.maturimate.features.sync.domain.SyncError
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SettingsTest {

    private val defaultLocale: Locale = Locale.getDefault()
    private val today = LocalDate(2026, 10, 31)

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun repository(): UserProfileRepositoryImpl {
        val database = UserDatabase(
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
        )
        return UserProfileRepositoryImpl(database, LocalUserProvider(database), Dispatchers.Unconfined)
    }

    private suspend fun student(repository: UserProfileRepositoryImpl) = repository.apply {
        saveTeachingLanguage(TeachingLanguage.HU)
        saveRole(UserRole.STUDENT)
        saveSubjects(listOf(UserSubject(1, true), UserSubject(5, true), UserSubject(6, true)))
        saveGraduationDate(LocalDate(2027, 3, 15))
        saveDailyGoal(10)
    }

    private object NoExamDates : ExamDateRepository {
        override val dates = flowOf(emptyList<OfficialExamDate>())
        override suspend fun isAnswered(date: OfficialExamDate) = false
        override suspend fun markAnswered(date: OfficialExamDate) = Unit
    }

    /**
     * The settings' view model with a fake for each dependency (C13: none of them may be missing). By default a guest's
     * phone: nobody signed in.
     */
    private fun settings(
        repository: UserProfileRepository,
        auth: AuthRepository = SignedIn(user = null),
        account: AccountSettings = GuestPhone(guest = true),
        progress: ProgressRepository = progress(OperationResult.Success(Unit)),
        snackbar: SnackbarController = SnackbarController(),
        examDates: ExamDateRepository = NoExamDates,
    ) = SettingsViewModel(repository, auth, account, progress, snackbar, examDates, fixedToday = today)

    private suspend fun SettingsViewModel.loaded() = withTimeout(5_000) { state.first { it?.profile?.role != null } }!!

    @Test
    fun studentSeesEveryAnswerAndTheDaysLeft() = runBlocking {
        val state = settings(student(repository())).loaded()

        assertEquals(135, state.daysLeft)
        assertEquals(
            listOf(OnboardingStep.ROLE, OnboardingStep.SUBJECTS, OnboardingStep.DATE, OnboardingStep.GOAL),
            state.learningSteps,
        )
    }

    /** The account half of the settings, faked: whether this phone is a guest's, and who is signed in. */
    private class GuestPhone(private val guest: Boolean) : com.madovygabor.maturimate.features.auth.domain.AccountSettings {
        private val ok: com.madovygabor.maturimate.core.domain.error.OperationResult<Unit, com.madovygabor.maturimate.features.auth.domain.AuthError> = com.madovygabor.maturimate.core.domain.error.OperationResult.Success(Unit)
        override suspend fun isGuestPhone() = guest
        override suspend fun saveNames(firstName: String, lastName: String) = ok
        override suspend fun sendPasswordCode() = ok
        override suspend fun changePassword(oldPassword: String, code: String, newPassword: String) = ok
        override suspend fun signOut(force: Boolean) = ok
    }

    /** Who is signed in: [user], or nobody (a guest). */
    private open class SignedIn(private val user: AccountUser? = AccountUser("account-y", "bea@example.com", "Bea", "Nagy")) : AuthRepository {
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

    // N1: a guest phone with a session (an account that is not set up) showed the account rows, and its sign-out
    // removed the guest's data. The guest rule is the local user's, the same as the sync's.
    @Test
    fun aGuestPhoneWithASessionIsAGuestInTheSettings() = runBlocking {
        val guest = settings(student(repository()), auth = SignedIn(), account = GuestPhone(guest = true)).loaded()
        assertTrue(guest.isGuest)
        assertEquals(null, guest.account)

        val account = settings(student(repository()), auth = SignedIn(), account = GuestPhone(guest = false)).loaded()
        assertFalse(account.isGuest)
        assertEquals("bea@example.com", account.account?.email)
    }

    @Test
    fun theDaysLeftFollowThePublishedOfficialDate() = runBlocking {
        val repository = student(repository())
        // the rule's date of 2027 is replaced by the official one, as on home and in the plan
        repository.saveGraduationDate(ExamDates.secondWeekWeekday(2027))
        val official = OfficialExamDate(1, 2027, null, LocalDate(2027, 3, 17), publishedAt = 1)
        val dates = object : ExamDateRepository {
            override val dates = flowOf(listOf(official))
            override suspend fun isAnswered(date: OfficialExamDate) = false
            override suspend fun markAnswered(date: OfficialExamDate) = Unit
        }

        val state = settings(repository, examDates = dates).loaded()

        assertEquals(today.daysUntil(official.date), state.daysLeft)
    }

    @Test
    fun teacherHasNoExamDateNorGoal() = runBlocking {
        val repository = repository()
        repository.saveRole(UserRole.TEACHER)
        val state = settings(repository).loaded()

        assertEquals(null, state.daysLeft)
        assertEquals(listOf(OnboardingStep.ROLE, OnboardingStep.SUBJECTS), state.learningSteps)
    }

    @Test
    fun aNewRoleAsksOnlyForWhatIsMissing() = runBlocking {
        val repository = repository()
        repository.saveRole(UserRole.TEACHER)
        val viewModel = settings(repository)
        viewModel.loaded()

        assertEquals(listOf(OnboardingStep.DATE, OnboardingStep.GOAL), viewModel.missingAfterRoleChange(UserRole.STUDENT))
        assertEquals(listOf(OnboardingStep.GOAL), viewModel.missingAfterRoleChange(UserRole.LEARNER))
        assertEquals(emptyList(), viewModel.missingAfterRoleChange(UserRole.TEACHER))
    }

    @Test
    fun theTwoLanguagesAreSetApart() = runBlocking {
        val repository = student(repository())
        val viewModel = settings(repository)
        viewModel.loaded()

        viewModel.setAppLanguage(TeachingLanguage.SK)
        withTimeout(5_000) { viewModel.state.first { it?.profile?.uiLanguage == TeachingLanguage.SK } }
        assertEquals(TeachingLanguage.HU, repository.profile.first()!!.teachingLanguage)

        val asked = CompletableDeferred<Unit>()
        viewModel.setSchoolLanguage(TeachingLanguage.SK) { asked.complete(Unit) }
        withTimeout(5_000) { viewModel.state.first { it?.profile?.teachingLanguage == TeachingLanguage.SK } }
        // asked once the language is saved
        withTimeout(5_000) { asked.await() }
        var askedForSubjects = false
        // the same language again asks nothing
        askedForSubjects = false
        viewModel.setSchoolLanguage(TeachingLanguage.SK) { askedForSubjects = true }
        assertFalse(askedForSubjects)
    }

    @Test
    fun theReminderSwitchKeepsTheTimeAndAsksForOneWhenThereIsNone() = runBlocking {
        val repository = student(repository())
        val viewModel = settings(repository)
        viewModel.loaded()
        // no time saved yet: the screen opens the reminder question
        assertFalse(viewModel.setReminder(true))

        repository.saveReminder(false, LocalTime(18, 0))
        withTimeout(5_000) { viewModel.state.first { it?.profile?.reminderTime != null } }
        assertTrue(viewModel.setReminder(true))
        withTimeout(5_000) { viewModel.state.first { it?.profile?.reminderEnabled == true } }
        assertEquals(LocalTime(18, 0), repository.profile.first()!!.reminderTime)
    }

    // the JVM is the desktop: no reminder, so no reminder group in the settings
    @Test
    fun theDesktopSettingsHaveNoReminderRows() = runDesktopComposeUiTest(412, 2000) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val state = runBlocking { settings(student(repository())).loaded() }
        setContent { AppTheme { SettingsContent(state, SettingsActions()) } }

        onAllNodesWithText("Napi emlékeztető").assertCountEquals(0)
        onAllNodesWithText("Értesítések").assertCountEquals(0)
    }

    @Test
    fun aGuestGetsTheAccountRowsAndLaterStepsAreComingSoon() = runDesktopComposeUiTest(412, 2000) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val state = runBlocking { settings(student(repository())).loaded() }
        var signUp = false
        setContent { AppTheme { SettingsContent(state, SettingsActions(onSignUp = { signUp = true })) } }

        onNodeWithText("Fiók létrehozása").performClick()
        assertTrue(signUp)
        // a guest has nothing to sync
        onAllNodesWithText("Szinkronizálás").assertCountEquals(0)
        onNodeWithText("Az iskolád tanítási nyelve").assertExists()
        onNodeWithText("Az app nyelve").assertExists()
        // sign-out and deletion only with an account
        onAllNodesWithText("Kijelentkezés").assertCountEquals(0)
    }

    @Test
    fun anAccountGetsTheRedRowsAndAGoogleAccountHasNoPassword() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val loaded = runBlocking { settings(student(repository())).loaded() }
        var account by mutableStateOf(AccountUser("id", "anna@example.com", "Anna", "Kovács"))
        var reset = false
        var opened: SettingsDialog? = null
        var dialogs by mutableStateOf(SettingsDialogState())
        setContent {
            AppTheme {
                SettingsContent(
                    loaded.copy(account = account, guestPhone = false),
                    SettingsActions(onReset = { reset = true }, onOpenDialog = { opened = it; dialogs = SettingsDialogState(it) }),
                    dialogs,
                )
            }
        }

        onNodeWithText("Jelszó megváltoztatása").assertExists()
        onNodeWithText("Kijelentkezés").assertExists()
        // the reset asks first, then runs
        onNodeWithText("Haladás újrakezdése").performClick()
        assertEquals(SettingsDialog.RESET, opened)
        onNodeWithText("Újrakezded a haladást?").assertExists()
        onNodeWithText("Újrakezdés").performClick()
        assertTrue(reset)

        account = account.copy(hasPassword = false)
        onAllNodesWithText("Jelszó megváltoztatása").assertCountEquals(0)
    }

    @Test
    fun theSyncRowShowsHowTheUploadStands() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val loaded = runBlocking { settings(student(repository())).loaded() }
        val account = AccountUser("id", "anna@example.com", "Anna", "Kovács")
        var sync by mutableStateOf(SyncState(signedIn = true))
        var tapped = 0
        setContent { AppTheme { SettingsContent(loaded.copy(account = account, guestPhone = false, sync = sync), SettingsActions(onSync = { tapped++ })) } }

        // never uploaded
        onNodeWithText("Még nincs feltöltve").assertExists()
        sync = SyncState(signedIn = true, pendingRounds = 2)
        onNodeWithText("Feltöltésre vár: 2 kör").assertExists()
        sync = SyncState(signedIn = true, running = true)
        onNodeWithText("Szinkronizálás…").assertExists()
        sync = SyncState(signedIn = true, lastError = SyncError.SERVER)
        onNodeWithText("Nem sikerült. Koppints az újrapróbáláshoz.").assertExists()
        onNodeWithText("Szinkronizálás").performClick()
        assertEquals(1, tapped)
    }

    // ---- state, feedback and dialogs (milestone 13, card 6) ----

    private val ok: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)

    /** The account half of the settings, faked: each answer can be set, each call is remembered. */
    private class FakeAccount : AccountSettings {
        var canShare = false
        var namesResult: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        var codeResult: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        var changeResult: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        var deleteResult: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        var exportResult: OperationResult<String, AuthError> = OperationResult.Success("maturimate.json")
        var codesSent = 0
        /** The old password, the code and the new password of the last change. */
        var changed: Triple<String, String, String>? = null
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

    /** A progress repository that only knows the reset (a proxy: the interface is long, the settings use one call). */
    private fun progress(result: OperationResult<Unit, LearningError>): ProgressRepository =
        Proxy.newProxyInstance(ProgressRepository::class.java.classLoader, arrayOf(ProgressRepository::class.java)) { _, method, _ ->
            if (method.name == "resetProgress") result else error("the settings do not call ${method.name}")
        } as ProgressRepository

    private fun viewModel(
        account: FakeAccount = FakeAccount(),
        progress: ProgressRepository = progress(OperationResult.Success(Unit)),
        repository: UserProfileRepository = runBlocking { student(repository()) },
        snackbar: SnackbarController = SnackbarController(),
        user: AccountUser = AccountUser("account-y", "bea@example.com", "Bea", "Nagy"),
    ) = settings(repository, auth = SignedIn(user), account = account, progress = progress, snackbar = snackbar)

    private suspend fun SnackbarController.next(): UiText = withTimeout(5_000) { events.first() }.message

    private suspend fun SnackbarController.isQuiet() = withTimeoutOrNull(300) { events.first() } == null

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun said(res: org.jetbrains.compose.resources.StringResource, vararg args: Any) = UiText.StringRes(res, arrayOf(*args))

    @Test
    fun aFailedDeletionShowsTheErrorAndKeepsTheDialog() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val account = FakeAccount().apply { deleteResult = OperationResult.Error(AuthError.NETWORK) }
        setContent { AppTheme { SettingsScreen(viewModel = viewModel(account)) } }

        waitForText("A fiók törlése")
        onNodeWithText("A fiók törlése").performClick()
        onNodeWithText("Törlöd a fiókodat?").assertExists()
        // the typed word and the password guard the button: nothing happens before both are there
        onNodeWithText("Végleges törlés").assertIsNotEnabled()
        onNodeWithText("A megerősítéshez írd be: TÖRLÉS.").performTextInput("töRLés ")
        onNodeWithText("Végleges törlés").assertIsNotEnabled()
        onNodeWithText("A fiókod jelszava").performTextInput("Old12345")
        onNodeWithText("Végleges törlés").assertIsEnabled().performClick()

        waitForText("Nincs internetkapcsolat. Próbáld újra, ha újra van net.")
        onNodeWithText("Törlöd a fiókodat?").assertExists()
        assertEquals(1, account.deletes)
        assertEquals("Old12345", (account.proofs.single() as DeletionProof.Password).password)
    }

    // Card 11: the deletion needs the proof that the user is the owner: a wrong password is said at its field.
    @Test
    fun aWrongPasswordIsSaidAtTheDeletionsPasswordField() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val account = FakeAccount().apply { deleteResult = OperationResult.Error(AuthError.INVALID_CREDENTIALS) }
        setContent { AppTheme { SettingsScreen(viewModel = viewModel(account)) } }

        waitForText("A fiók törlése")
        onNodeWithText("A fiók törlése").performClick()
        onNodeWithText("A megerősítéshez írd be: TÖRLÉS.").performTextInput("TÖRLÉS")
        onNodeWithText("A fiókod jelszava").performTextInput("Wrong123")
        onNodeWithText("Végleges törlés").performClick()

        waitForText("A jelszó nem jó.")
        onNodeWithText("Törlöd a fiókodat?").assertExists()
    }

    // Card 11: an account made with Google has no password; its proof is a fresh Google sign-in of the same account.
    @Test
    fun aGoogleAccountsDeletionAsksForAFreshGoogleSignIn() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val loaded = runBlocking { settings(student(repository())).loaded() }
        var google = 0
        val state = loaded.copy(account = AccountUser("id", "anna@example.com", "Anna", "Kovács", hasPassword = false), guestPhone = false)
        setContent {
            AppTheme { SettingsContent(state, SettingsActions(onDeleteWithGoogle = { google++ }), SettingsDialogState(SettingsDialog.DELETE)) }
        }

        onNodeWithText("A Google-lel létrehozott fiók törléséhez jelentkezz be újra Google-lel, ugyanazzal a fiókkal.").assertExists()
        onAllNodesWithText("A fiókod jelszava").assertCountEquals(0)
        onNodeWithText("Bejelentkezés és törlés").assertIsNotEnabled()
        onNodeWithText("A megerősítéshez írd be: TÖRLÉS.").performTextInput("TÖRLÉS")
        onNodeWithText("Bejelentkezés és törlés").assertIsEnabled().performClick()
        assertEquals(1, google)
    }

    @Test
    fun aFreshGoogleSignInOfTheSameAccountDeletesIt() = runBlocking<Unit> {
        val account = FakeAccount()
        val viewModel = viewModel(account, user = AccountUser("account-y", "bea@example.com", "Bea", "Nagy", hasPassword = false))
        viewModel.loaded()
        viewModel.openDialog(SettingsDialog.DELETE)

        val opened = CompletableDeferred<Unit>()
        viewModel.startGoogleDeletion { opened.complete(Unit) }
        // Google opens only after the session is remembered (and the sync held)
        withTimeout(5_000) { opened.await() }
        assertEquals(1, account.proofsStarted)
        // the Google flow is open: the dialog waits
        assertTrue(viewModel.busy.value)
        viewModel.onGoogleDeletionSignedIn()

        withTimeout(5_000) { viewModel.dialogs.first { it.dialog == null } }
        assertEquals(listOf<DeletionProof>(DeletionProof.FreshSignIn), account.proofs)
        withTimeout(5_000) { viewModel.busy.first { !it } }
    }

    @Test
    fun aGoogleSignInOfAnotherAccountDeletesNothing() = runBlocking<Unit> {
        val account = FakeAccount().apply { freshSignIn = OperationResult.Error(AuthError.WRONG_ACCOUNT) }
        val viewModel = viewModel(account, user = AccountUser("account-y", "bea@example.com", "Bea", "Nagy", hasPassword = false))
        viewModel.loaded()
        viewModel.openDialog(SettingsDialog.DELETE)

        viewModel.startGoogleDeletion {}
        withTimeout(5_000) { while (account.proofsStarted == 0) kotlinx.coroutines.yield() }
        viewModel.onGoogleDeletionSignedIn()

        val dialog = withTimeout(5_000) { viewModel.dialogs.first { it.error != null } }
        assertEquals(SettingsDialogState(SettingsDialog.DELETE, AuthError.WRONG_ACCOUNT), dialog)
        assertEquals(0, account.deletes)
        withTimeout(5_000) { viewModel.busy.first { !it } }

        // S2-1: a closed or failed Google flow frees the dialog again and ends the proof (the sync goes on)
        viewModel.startGoogleDeletion {}
        viewModel.onGoogleDeletionClosed()
        assertFalse(viewModel.busy.value)
        viewModel.startGoogleDeletion {}
        viewModel.onGoogleDeletionFailed(AuthError.NETWORK)
        assertEquals(AuthError.NETWORK, viewModel.dialogs.value.error)
        assertFalse(viewModel.busy.value)
        withTimeout(5_000) { while (account.cancels < 2) kotlinx.coroutines.yield() }
        assertEquals(0, account.deletes)
    }

    @Test
    fun aProofThatCannotStartNeverOpensGoogle() = runBlocking<Unit> {
        val account = FakeAccount().apply { startResult = OperationResult.Error(AuthError.NETWORK) }
        val viewModel = viewModel(account, user = AccountUser("account-y", "bea@example.com", "Bea", "Nagy", hasPassword = false))
        viewModel.loaded()
        viewModel.openDialog(SettingsDialog.DELETE)
        var opened = false

        viewModel.startGoogleDeletion { opened = true }

        assertEquals(AuthError.NETWORK, withTimeout(5_000) { viewModel.dialogs.first { it.error != null } }.error)
        withTimeout(5_000) { viewModel.busy.first { !it } }
        assertFalse(opened)
    }

    // S2-1: the settings never show another account's session on this account's phone
    @Test
    fun aForeignSessionIsEndedBeforeTheSettingsShowTheAccount() = runBlocking<Unit> {
        val account = FakeAccount()
        viewModel(account).loaded()
        assertTrue(account.foreignChecks > 0)
    }

    // The sign-out and the deletion left their dialog open (and its button busy) until the app was rebuilt.
    @Test
    fun theDialogClosesWhenTheSignOutOrTheDeletionWentThrough() = runBlocking {
        val account = FakeAccount()
        val viewModel = viewModel(account)
        viewModel.loaded()

        viewModel.openDialog(SettingsDialog.SIGN_OUT)
        viewModel.signOut()
        withTimeout(5_000) { viewModel.dialogs.first { it.dialog == null } }

        viewModel.openDialog(SettingsDialog.SIGN_OUT_UNSYNCED)
        viewModel.signOut(force = true)
        withTimeout(5_000) { viewModel.dialogs.first { it.dialog == null } }

        viewModel.openDialog(SettingsDialog.DELETE)
        viewModel.deleteAccount("Old12345")
        withTimeout(5_000) { viewModel.dialogs.first { it.dialog == null } }
        assertEquals(1, account.deletes)
    }

    @Test
    fun theTypedWordMustMatchBeforeTheDeleteButtonWorks() = runDesktopComposeUiTest(412, 1000) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        var deleted = 0
        setContent {
            AppTheme { ConfirmDialog("Cím", "Szöveg", "Töröl", busy = false, onConfirm = { deleted++ }, onDismiss = {}, word = "TÖRLÉS", wordLabel = "Írd be") }
        }

        onNodeWithText("Töröl").assertIsNotEnabled()
        onNodeWithText("Írd be").performTextInput("TÖRL")
        onNodeWithText("Töröl").assertIsNotEnabled()
        onNodeWithText("TÖRL").performTextClearance()
        onNodeWithText("Írd be").performTextInput("  törlés ")
        onNodeWithText("Töröl").assertIsEnabled().performClick()
        assertEquals(1, deleted)
    }

    @Test
    fun aBusyConfirmDialogSaysSoAndWaits() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        setContent { AppTheme { ConfirmDialog("Cím", "Szöveg", "Töröl", busy = true, onConfirm = {}, onDismiss = {}) } }

        onNodeWithText("Betöltés…").assertIsNotEnabled()
        onAllNodesWithText("Töröl").assertCountEquals(0)
    }

    @Test
    fun aFailedResetKeepsTheDialogAndASuccessSaysSo() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val snackbar = SnackbarController()
        var result: OperationResult<Unit, LearningError> = OperationResult.Error(LearningError.NOT_FOUND)
        val progress = Proxy.newProxyInstance(ProgressRepository::class.java.classLoader, arrayOf(ProgressRepository::class.java)) { _, _, _ -> result } as ProgressRepository
        setContent { AppTheme { SettingsScreen(viewModel = viewModel(progress = progress, snackbar = snackbar)) } }

        waitForText("Haladás újrakezdése")
        onNodeWithText("Haladás újrakezdése").performClick()
        onNodeWithText("Újrakezdés").performClick()
        waitForText("Valami nem sikerült. Próbáld újra.")
        onNodeWithText("Újrakezded a haladást?").assertExists()
        assertTrue(runBlocking { snackbar.isQuiet() })

        // the same dialog, tried again: it closes and a snackbar says it
        result = OperationResult.Success(Unit)
        onNodeWithText("Újrakezdés").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Újrakezded a haladást?").fetchSemanticsNodes().isEmpty() }
        assertEquals(said(Res.string.settings_reset_done), runBlocking { snackbar.next() })
    }

    @Test
    fun theLanguageChoicesCarryTheirBadges() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val state = runBlocking { settings(student(repository())).loaded() }
        setContent { AppTheme { SettingsContent(state, SettingsActions()) } }

        onNodeWithText("Az app nyelve").performClick()
        onAllNodesWithText("HU").assertCountEquals(1)
        onAllNodesWithText("SK").assertCountEquals(1)
    }

    @Test
    fun theSubjectsAreAskedAgainOnlyAfterTheLanguageIsSaved() = runBlocking {
        val real = student(repository())
        val snackbar = SnackbarController()
        val saved = viewModel(repository = real, snackbar = snackbar)
        saved.loaded()
        val changed = CompletableDeferred<Unit>()
        saved.setSchoolLanguage(TeachingLanguage.SK) { changed.complete(Unit) }
        withTimeout(5_000) { changed.await() }
        assertEquals(TeachingLanguage.SK, real.profile.first()!!.teachingLanguage)

        // a save that fails: no callback, and the user is told
        val repo = student(repository())
        val failing = object : UserProfileRepository by repo {
            override suspend fun saveSchoolLanguage(language: TeachingLanguage): OperationResult<Unit, DataError> =
                OperationResult.Error(DataError.IO_ERROR)
        }
        val broken = viewModel(repository = failing, snackbar = snackbar)
        broken.loaded()
        var asked = false
        broken.setSchoolLanguage(TeachingLanguage.SK) { asked = true }
        assertEquals(said(Res.string.error_io), snackbar.next())
        assertFalse(asked)
    }

    @Test
    fun aFailedLanguageOrReminderSaveIsSaidInASnackbar() = runBlocking {
        val real = student(repository())
        real.saveReminder(false, LocalTime(18, 0))
        val failing = object : UserProfileRepository by real {
            override suspend fun saveUiLanguage(language: TeachingLanguage): OperationResult<Unit, DataError> = OperationResult.Error(DataError.IO_ERROR)
            override suspend fun saveReminder(enabled: Boolean, time: LocalTime?): OperationResult<Unit, DataError> = OperationResult.Error(DataError.STORAGE_FULL)
        }
        val snackbar = SnackbarController()
        val viewModel = viewModel(repository = failing, snackbar = snackbar)
        viewModel.loaded()

        viewModel.setAppLanguage(TeachingLanguage.SK)
        assertEquals(said(Res.string.error_io), snackbar.next())
        withTimeout(5_000) { viewModel.state.first { it?.profile?.reminderTime != null } }
        assertTrue(viewModel.setReminder(true))
        assertEquals(said(Res.string.error_io), snackbar.next())
    }

    @Test
    fun thePasswordChangeGoesInStepsAndEndsWithASnackbar() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val account = FakeAccount()
        val snackbar = SnackbarController()
        setContent { AppTheme { SettingsScreen(viewModel = viewModel(account, snackbar = snackbar)) } }

        waitForText("Jelszó megváltoztatása")
        onNodeWithText("Jelszó megváltoztatása").performClick()
        // step 1: the code goes to the e-mail address
        onNodeWithText("Kód küldése").performClick()
        waitForText("6 jegyű kód")
        assertEquals(1, account.codesSent)
        // step 2: the code, the current password and the new one twice; Save waits for all of them
        onNodeWithText("Mentés").assertIsNotEnabled()
        onNodeWithText("6 jegyű kód").performTextInput("123456")
        onNodeWithText("Jelenlegi jelszó").performTextInput("Old12345")
        onNodeWithText("Új jelszó").performTextInput("New12345")
        onNodeWithText("Új jelszó újra").performTextInput("New12399")
        onNodeWithText("A két új jelszó nem egyezik.").assertExists()
        onNodeWithText("Mentés").assertIsNotEnabled()
        onNodeWithText("New12399").performTextClearance()
        onNodeWithText("Új jelszó újra").performTextInput("New12345")
        onNodeWithText("Mentés").assertIsEnabled().performClick()

        waitUntil(timeoutMillis = 5_000) { account.changed != null }
        assertEquals(Triple("Old12345", "123456", "New12345"), account.changed)
        assertEquals(said(Res.string.settings_password_done), runBlocking { snackbar.next() })
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Új jelszó").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun aWrongCodeIsSaidAtTheCode() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val account = FakeAccount().apply { changeResult = OperationResult.Error(AuthError.INVALID_CODE) }
        setContent { AppTheme { SettingsScreen(viewModel = viewModel(account)) } }

        fillThePasswordDialog()

        waitForText("Hibás vagy lejárt kód. Ellenőrizd, vagy kérj újat.")
        onAllNodesWithText("6 jegyű kód").assertCountEquals(1)
    }

    // Card 11: a wrong current password is said at its own field, in words of the settings (not "wrong e-mail or password").
    @Test
    fun aWrongCurrentPasswordIsSaidAtItsField() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val account = FakeAccount().apply { changeResult = OperationResult.Error(AuthError.INVALID_CREDENTIALS) }
        setContent { AppTheme { SettingsScreen(viewModel = viewModel(account)) } }

        fillThePasswordDialog()

        waitForText("A jelszó nem jó.")
        onAllNodesWithText("Hibás e-mail cím vagy jelszó.").assertCountEquals(0)
        // the dialog stays, with what was typed
        onNodeWithText("Jelenlegi jelszó").assertExists()
    }

    @Test
    fun tooManyTriesAreSaidInTheirOwnWords() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val account = FakeAccount().apply { changeResult = OperationResult.Error(AuthError.TOO_MANY_REQUESTS) }
        setContent { AppTheme { SettingsScreen(viewModel = viewModel(account)) } }

        fillThePasswordDialog()

        waitForText("Túl sok próbálkozás. Várj egy kicsit, és próbáld újra.")
    }

    private fun ComposeUiTest.fillThePasswordDialog() {
        waitForText("Jelszó megváltoztatása")
        onNodeWithText("Jelszó megváltoztatása").performClick()
        onNodeWithText("Kód küldése").performClick()
        waitForText("6 jegyű kód")
        onNodeWithText("6 jegyű kód").performTextInput("000000")
        onNodeWithText("Jelenlegi jelszó").performTextInput("Old12345")
        onNodeWithText("Új jelszó").performTextInput("New12345")
        onNodeWithText("Új jelszó újra").performTextInput("New12345")
        onNodeWithText("Mentés").performClick()
    }

    @Test
    fun theExportSaysSavedWithThePlaceNothingOnCancelAndAnErrorOnFailure() = runBlocking {
        val account = FakeAccount()
        val snackbar = SnackbarController()
        val viewModel = viewModel(account, snackbar = snackbar)
        viewModel.loaded()

        account.exportResult = OperationResult.Success("C:\\Users\\anna\\maturimate.json")
        viewModel.exportData()
        assertEquals(said(Res.string.settings_export_done, "C:\\Users\\anna\\maturimate.json"), snackbar.next())
        assertEquals(false, account.exportedAsShare)

        // the picker was closed: nothing to show
        account.exportResult = OperationResult.Success(ExportSaver.CANCELLED)
        viewModel.exportData()
        withTimeout(5_000) { viewModel.busy.first { !it } }
        assertTrue(snackbar.isQuiet())

        account.exportResult = OperationResult.Error(AuthError.UNKNOWN)
        viewModel.exportData()
        assertEquals(AuthError.UNKNOWN.toUiText(), snackbar.next())
    }

    @Test
    fun sharingSaysSharedNotSaved() = runBlocking {
        val account = FakeAccount().apply { canShare = true }
        val snackbar = SnackbarController()
        val viewModel = viewModel(account, snackbar = snackbar)
        assertTrue(viewModel.loaded().canShareExport)

        viewModel.exportData(share = true)
        assertEquals(said(Res.string.settings_export_shared), snackbar.next())
        assertEquals(true, account.exportedAsShare)
    }

    @Test
    fun theExportOffersSharingOnlyWhereItWorks() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val loaded = runBlocking { settings(student(repository())).loaded() }
        val account = AccountUser("id", "anna@example.com", "Anna", "Kovács")
        var shared = 0
        var exports = mutableListOf<Boolean>()
        var dialogs by mutableStateOf(SettingsDialogState())
        var canShare by mutableStateOf(true)
        setContent {
            AppTheme {
                SettingsContent(
                    loaded.copy(account = account, guestPhone = false, canShareExport = canShare),
                    SettingsActions(onOpenDialog = { dialogs = SettingsDialogState(it) }, onExport = { exports += it; if (it) shared++ }),
                    dialogs,
                )
            }
        }

        onNodeWithText("Adataim letöltése").performClick()
        onNodeWithText("Mentés fájlba").assertExists()
        onNodeWithText("Megosztás").performClick()
        assertEquals(listOf(true), exports)
        assertEquals(1, shared)

        // a desktop cannot share: the row goes straight to the save dialog
        dialogs = SettingsDialogState()
        canShare = false
        onNodeWithText("Adataim letöltése").performClick()
        onAllNodesWithText("Megosztás").assertCountEquals(0)
        assertEquals(listOf(true, false), exports)
    }

    @Test
    fun theSyncRowSaysTodayFromTheStatesDay() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val loaded = runBlocking { settings(student(repository())).loaded() }
        val account = AccountUser("id", "anna@example.com", "Anna", "Kovács")
        val noon = today.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() + 12 * 3_600_000L
        setContent { AppTheme { SettingsContent(loaded.copy(account = account, guestPhone = false, sync = SyncState(signedIn = true, lastSuccessAt = noon)), SettingsActions()) } }

        onNodeWithText("Utoljára: ma 12:00").assertExists()
    }

    @Test
    fun theKeyboardActionKeyMovesOnAndSubmitsThePasswordSteps() = runDesktopComposeUiTest(412, 1200) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        var saved: Triple<String, String, String>? = null
        setContent {
            AppTheme {
                PasswordDialog("a@b.hu", PasswordStep.FORM, busy = false, error = null, onSendCode = {},
                    onClearError = {}, onSave = { old, c, n -> saved = Triple(old, c, n) }, onDismiss = {})
            }
        }

        onNodeWithText("6 jegyű kód").performTextInput("123456")
        onNodeWithText("Jelenlegi jelszó").performTextInput("Old12345")
        onNodeWithText("Új jelszó").performTextInput("New12345")
        onNodeWithText("Új jelszó újra").performTextInput("New12345")
        onNodeWithText("Új jelszó újra").performImeAction()
        assertEquals(Triple("Old12345", "123456", "New12345"), saved)
    }

    @Test
    fun anOpenDialogLivesInTheViewModelAndSoSurvivesANewScreen() {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val viewModel = viewModel()
        runDesktopComposeUiTest(412, 2400) {
            setContent { AppTheme { SettingsScreen(viewModel = viewModel) } }
            waitForText("Név")
            viewModel.openDialog(SettingsDialog.NAME)
            waitForText("A neved")
        }
        // a new screen (a rotation builds one) shows the dialog that was open
        runDesktopComposeUiTest(412, 2400) {
            setContent { AppTheme { SettingsScreen(viewModel = viewModel) } }
            waitForText("A neved")
        }
    }

    /** Pictures of the settings, both themes and languages, into build/screenshots/settings. */
    @Test
    fun rendersAtEverySizeAndTheme() {
        val state = runBlocking { settings(student(repository())).loaded() }
        val sizes = mapOf("phone" to (412 to 915), "desktop" to (1280 to 800))
        val dir = File("build/screenshots/settings").apply { mkdirs() }
        for (language in listOf("hu", "sk")) {
            Locale.setDefault(Locale.forLanguageTag(language))
            for ((name, size) in sizes) for (dark in listOf(false, true)) {
                runDesktopComposeUiTest(size.first, size.second) {
                    setContent { AppTheme(darkTheme = dark) { SettingsContent(state, SettingsActions()) } }
                    onAllNodesWithText(runBlocking { getString(Res.string.settings_group_learning) })[0].assertExists()
                    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "settings-$language-$name-${if (dark) "dark" else "light"}.png"))
                }
            }
        }
    }
}
