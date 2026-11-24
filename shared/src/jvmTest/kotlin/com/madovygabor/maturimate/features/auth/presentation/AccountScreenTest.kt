package com.madovygabor.maturimate.features.auth.presentation

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import maturimate.shared.generated.resources.account_browse
import maturimate.shared.generated.resources.common_back
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.account_create
import maturimate.shared.generated.resources.account_consent_required
import maturimate.shared.generated.resources.account_google
import maturimate.shared.generated.resources.account_signed_in_as
import maturimate.shared.generated.resources.account_title
import maturimate.shared.generated.resources.code_title
import org.jetbrains.compose.resources.getString
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import org.jetbrains.compose.resources.StringResource

@OptIn(ExperimentalTestApi::class, ExperimentalTime::class)
class AccountScreenTest {

    private fun text(key: StringResource) = runBlocking { getString(key) }

    private val defaultLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    @Test
    fun aSignedInAccountShowsItsAddressWithoutNamingGoogle() = runComposeUiTest {
        setContent { AppTheme(darkTheme = false) { AccountContent(AccountUiState(signedInEmail = "anna@example.com"), UserRole.STUDENT, AccountActions()) } }
        onNodeWithText(runBlocking { getString(Res.string.account_signed_in_as, "anna@example.com") }).assertExists()
        onAllNodesWithText(text(Res.string.account_google)).assertCountEquals(0)
    }

    @Test
    fun theButtonsWorkBeforeTheConsentAndTheMissingConsentIsShown() = runComposeUiTest {
        setContent { AppTheme(darkTheme = false) { AccountContent(AccountUiState(consentMissing = true), UserRole.STUDENT, AccountActions()) } }
        onNodeWithText(text(Res.string.account_create)).assertIsEnabled()
        onNodeWithText(text(Res.string.account_google)).assertIsEnabled()
        onNodeWithText(text(Res.string.account_consent_required)).assertExists()
    }

    @Test
    fun fromTheProfileThereIsNoLookAroundButtonButABackArrow() = runComposeUiTest {
        var back = false
        setContent { AppTheme(darkTheme = false) { AccountContent(AccountUiState(), null, AccountActions(onBack = { back = true }), inApp = true) } }
        onNodeWithText(text(Res.string.account_create)).assertExists()
        onAllNodesWithText(text(Res.string.account_browse)).assertCountEquals(0)
        onNodeWithContentDescription(text(Res.string.common_back)).performClick()
        assertTrue(back)
    }

    @Test
    fun inTheOnboardingTheLookAroundButtonStays() = runComposeUiTest {
        setContent { AppTheme(darkTheme = false) { AccountContent(AccountUiState(), UserRole.STUDENT, AccountActions()) } }
        onNodeWithText(text(Res.string.account_browse)).assertExists()
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
