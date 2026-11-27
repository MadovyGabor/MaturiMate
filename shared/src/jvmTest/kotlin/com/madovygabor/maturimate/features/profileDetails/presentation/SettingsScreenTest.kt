package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingStep
import com.madovygabor.maturimate.features.auth.domain.AccountUser
import com.madovygabor.maturimate.features.sync.domain.SyncError
import com.madovygabor.maturimate.features.sync.domain.SyncState
import com.madovygabor.maturimate.features.userProfile.domain.ExamDateRepository
import com.madovygabor.maturimate.features.userProfile.domain.ExamDates
import com.madovygabor.maturimate.features.userProfile.domain.OfficialExamDate
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.testutil.awaitFirst
import com.madovygabor.maturimate.testutil.awaitWithin
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.settings_group_learning
import org.jetbrains.compose.resources.getString

@OptIn(ExperimentalTestApi::class)
class SettingsScreenTest : SettingsTestBase() {

    @Test
    fun studentSeesEveryAnswerAndTheDaysLeft() = runBlocking {
        val state = settings(student(repository())).loaded()

        assertEquals(135, state.daysLeft)
        assertEquals(
            listOf(OnboardingStep.ROLE, OnboardingStep.SUBJECTS, OnboardingStep.DATE, OnboardingStep.GOAL),
            state.learningSteps,
        )
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
    fun theTwoLanguagesAreSetApart() = runBlocking {
        val repository = student(repository())
        val viewModel = settings(repository)
        viewModel.loaded()

        viewModel.setAppLanguage(TeachingLanguage.SK)
        viewModel.state.awaitFirst { it?.profile?.uiLanguage == TeachingLanguage.SK }
        assertEquals(TeachingLanguage.HU, repository.profile.first()!!.teachingLanguage)

        val asked = CompletableDeferred<Unit>()
        viewModel.setSchoolLanguage(TeachingLanguage.SK) { asked.complete(Unit) }
        viewModel.state.awaitFirst { it?.profile?.teachingLanguage == TeachingLanguage.SK }
        // asked once the language is saved
        asked.awaitWithin()
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
        viewModel.state.awaitFirst { it?.profile?.reminderTime != null }
        assertTrue(viewModel.setReminder(true))
        viewModel.state.awaitFirst { it?.profile?.reminderEnabled == true }
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
        // the reset asks first, then asks for the confirmation word, then runs
        onNodeWithText("Haladás újrakezdése").performClick()
        assertEquals(SettingsDialog.RESET, opened)
        onNodeWithText("Újrakezded a haladást?").assertExists()
        onNodeWithText("Tovább").performClick()
        assertEquals(SettingsDialog.RESET_CONFIRM, opened)
        onNodeWithText("Megerősítő szó").performTextInput("ÚJRAKEZDÉS")
        onNodeWithText("Újrakezdés").performClick()
        assertTrue(reset)

        account = account.copy(hasPassword = false)
        onAllNodesWithText("Jelszó megváltoztatása").assertCountEquals(0)
    }

    @Test
    fun anAccountShowsItsEmailAndItsNameRowOpensTheNameDialog() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val loaded = runBlocking { settings(student(repository())).loaded() }
        var opened: SettingsDialog? = null
        setContent {
            AppTheme {
                SettingsContent(
                    loaded.copy(account = AccountUser("id", "anna@example.com", "Anna", "Kovács"), guestPhone = false),
                    SettingsActions(onOpenDialog = { opened = it }),
                )
            }
        }

        // the email is shown without the "Nem módosítható." note (plus in the header), the guest's line is gone
        onAllNodesWithText("anna@example.com").assertCountEquals(2)
        onAllNodesWithText("Nem módosítható.").assertCountEquals(0)
        onAllNodesWithText("Még nincs fiókod").assertCountEquals(0)
        onNodeWithText("Név").performClick()
        assertEquals(SettingsDialog.NAME, opened)
    }

    // an account phone whose session is gone: the rows show what is saved, but the name and the password need the server
    @Test
    fun anAccountPhoneWithoutASessionOffersNoNameOrPasswordChange() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val loaded = runBlocking { settings(student(repository())).loaded() }
        var opened: SettingsDialog? = null
        setContent { AppTheme { SettingsContent(loaded.copy(account = null, guestPhone = false), SettingsActions(onOpenDialog = { opened = it })) } }

        onNodeWithText("Név").assertExists()
        onNodeWithText("Név").performClick()
        assertEquals(null, opened)
        onNodeWithText("Még nincs fiókod").assertExists()
        onAllNodesWithText("E-mail").assertCountEquals(0)
        onAllNodesWithText("Jelszó megváltoztatása").assertCountEquals(0)
        // still an account phone: no guest rows
        onAllNodesWithText("Fiók létrehozása").assertCountEquals(0)
        onNodeWithText("Kijelentkezés").assertExists()
    }

    @Test
    fun theSyncRowShowsHowTheUploadStands() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val loaded = runBlocking { settings(student(repository())).loaded() }
        val account = AccountUser("id", "anna@example.com", "Anna", "Kovács")
        var sync by mutableStateOf(SyncState(signedIn = true))
        var tapped = 0
        setContent { AppTheme { SettingsContent(loaded.copy(account = account, guestPhone = false, sync = sync), SettingsActions(onSync = { tapped++ })) } }

        // never synced
        onNodeWithText("Még nincs szinkronizálva").assertExists()
        sync = SyncState(signedIn = true, pendingRounds = 2)
        onNodeWithText("Feltöltésre vár: 2 kör").assertExists()
        sync = SyncState(signedIn = true, running = true)
        onNodeWithText("Szinkronizálás…").assertExists()
        val noon = today.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() + 12 * 3_600_000L
        sync = SyncState(signedIn = true, lastSuccessAt = noon, lastError = SyncError.NETWORK)
        onNodeWithText("Hálózatra vár · Utoljára: ma 12:00").assertExists()
        sync = SyncState(signedIn = true, lastSuccessAt = noon, lastError = SyncError.SERVER)
        onNodeWithText("Sikertelen · Utoljára: ma 12:00").assertExists()
        onAllNodesWithText("Újra")[0].performClick()
        assertEquals(1, tapped)
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
    fun theSyncRowSaysTodayFromTheStatesDay() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val loaded = runBlocking { settings(student(repository())).loaded() }
        val account = AccountUser("id", "anna@example.com", "Anna", "Kovács")
        val noon = today.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() + 12 * 3_600_000L
        setContent { AppTheme { SettingsContent(loaded.copy(account = account, guestPhone = false, sync = SyncState(signedIn = true, lastSuccessAt = noon)), SettingsActions()) } }

        onNodeWithText("Szinkronizálva · ma 12:00").assertExists()
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
