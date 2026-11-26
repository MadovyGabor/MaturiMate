package com.madovygabor.maturimate.features.auth.presentation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.madovygabor.maturimate.core.presentation.locale.LocalAppLocale
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.uiComponents.AppTextField
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import kotlinx.coroutines.runBlocking
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.account_first_name
import maturimate.shared.generated.resources.account_last_name
import maturimate.shared.generated.resources.account_title
import maturimate.shared.generated.resources.account_create
import maturimate.shared.generated.resources.code_title
import maturimate.shared.generated.resources.common_cancel
import maturimate.shared.generated.resources.common_loading
import maturimate.shared.generated.resources.password_hide
import maturimate.shared.generated.resources.password_show
import maturimate.shared.generated.resources.signin_button
import maturimate.shared.generated.resources.signin_replace_confirm
import maturimate.shared.generated.resources.signin_replace_title
import maturimate.shared.generated.resources.signup_app_code_title
import maturimate.shared.generated.resources.signup_app_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The function of the account forms: the replace dialog, name order, show/hide, busy fields, the in-app texts. */
@OptIn(ExperimentalTestApi::class)
class AuthFormsTest {

    private val defaultLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun text(key: StringResource) = runBlocking { getString(key) }

    private val filled = SignInUiState(email = "anna@example.com", password = "secret", hasLocalAnswers = true)

    @Test
    fun signInAsksBeforeReplacingTheAnswersOfThisPhone() = runDesktopComposeUiTest {
        var signIns = 0
        setContent { AppTheme(darkTheme = false) { SignInContent(filled, SignInActions(onSignIn = { signIns++ }), confirmReplace = true) } }

        onNodeWithText(text(Res.string.signin_button)).performClick()
        onNodeWithText(text(Res.string.signin_replace_title)).assertExists()
        assertEquals(0, signIns)

        onNodeWithText(text(Res.string.signin_replace_confirm)).performClick()
        assertEquals(1, signIns)
        onAllNodesWithText(text(Res.string.signin_replace_title)).assertCountEquals(0)
    }

    @Test
    fun cancellingTheReplaceDialogSignsNothingIn() = runDesktopComposeUiTest {
        var signIns = 0
        setContent { AppTheme(darkTheme = false) { SignInContent(filled, SignInActions(onSignIn = { signIns++ }), confirmReplace = true) } }

        onNodeWithText(text(Res.string.signin_button)).performClick()
        onNodeWithText(text(Res.string.common_cancel)).performClick()

        assertEquals(0, signIns)
        onAllNodesWithText(text(Res.string.signin_replace_title)).assertCountEquals(0)
    }

    @Test
    fun noDialogWithoutLocalAnswers() = runDesktopComposeUiTest {
        var signIns = 0
        setContent { AppTheme(darkTheme = false) { SignInContent(filled.copy(hasLocalAnswers = false), SignInActions(onSignIn = { signIns++ }), confirmReplace = true) } }

        onNodeWithText(text(Res.string.signin_button)).performClick()
        assertEquals(1, signIns)
    }

    // answers there, but the sign-in is not at the end of the onboarding (it comes from the profile)
    @Test
    fun noDialogOutsideTheOnboarding() = runDesktopComposeUiTest {
        var signIns = 0
        setContent { AppTheme(darkTheme = false) { SignInContent(filled, SignInActions(onSignIn = { signIns++ }), confirmReplace = false) } }

        onNodeWithText(text(Res.string.signin_button)).performClick()
        assertEquals(1, signIns)
    }

    @Test
    fun theFamilyNameComesFirstInHungarianAndTheFirstNameFirstInSlovak() {
        for ((tag, lastFirst) in listOf("hu" to true, "sk" to false)) {
            runDesktopComposeUiTest(412, 915) {
                setContent {
                    CompositionLocalProvider(LocalAppLocale provides tag) {
                        AppTheme(darkTheme = false) { AccountContent(AccountUiState(), UserRole.STUDENT, AccountActions()) }
                    }
                }
                // the two name fields share a row: the order is the order from the left
                val last = onAllNodesWithText(text(Res.string.account_last_name))[0].getBoundsInRoot().left
                val first = onAllNodesWithText(text(Res.string.account_first_name))[0].getBoundsInRoot().left
                assertEquals(lastFirst, last < first, "name order for $tag")
            }
        }
    }

    // The semantics of the field itself do not tell masked from shown (the Password keyboard sets them in both), so the
    // test follows the button: it offers "show" while hidden and "hide" while shown, and is kept after a change of the text.
    @Test
    fun theShowHideButtonTogglesThePassword() = runDesktopComposeUiTest {
        setContent { AppTheme(darkTheme = false) { AppTextField(value = "secret", onValueChange = {}, label = "Heslo", password = true) } }

        onNodeWithContentDescription(text(Res.string.password_show)).assertExists()
        onNodeWithContentDescription(text(Res.string.password_hide)).assertDoesNotExist()
        onNodeWithContentDescription(text(Res.string.password_show)).performClick()
        onNodeWithContentDescription(text(Res.string.password_hide)).assertExists()
        onNodeWithContentDescription(text(Res.string.password_hide)).performClick()
        onNodeWithContentDescription(text(Res.string.password_show)).assertExists()
    }

    @Test
    fun aFieldWithoutPasswordHasNoToggle() = runDesktopComposeUiTest {
        setContent { AppTheme(darkTheme = false) { AppTextField(value = "x", onValueChange = {}, label = "E-mail") } }

        onAllNodesWithText(text(Res.string.password_show)).assertCountEquals(0)
        assertTrue(onAllNodesWithContentDescriptionCount(text(Res.string.password_show)) == 0)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.onAllNodesWithContentDescriptionCount(description: String) =
        onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(description))).fetchSemanticsNodes().size

    @Test
    fun whileBusyTheFieldsAreOffAndTheMainButtonShowsItIsLoading() = runDesktopComposeUiTest {
        setContent { AppTheme(darkTheme = false) { SignInContent(filled.copy(busy = true), SignInActions()) } }

        onAllNodes(hasSetTextAction()).assertAll(androidx.compose.ui.test.isNotEnabled())
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text(Res.string.common_loading))).assertExists()
    }

    @Test
    fun theAccountFormIsBusyToo() = runDesktopComposeUiTest {
        setContent { AppTheme(darkTheme = false) { AccountContent(AccountUiState(busy = true), UserRole.STUDENT, AccountActions()) } }

        onAllNodes(hasSetTextAction()).assertAll(androidx.compose.ui.test.isNotEnabled())
        onNodeWithText(text(Res.string.account_create)).assertIsNotEnabled()
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text(Res.string.common_loading))).assertExists()
    }

    @Test
    fun theInAppSignUpHasItsOwnTexts() = runDesktopComposeUiTest {
        setContent { AppTheme(darkTheme = false) { AccountContent(AccountUiState(), null, AccountActions(), inApp = true) } }
        onNodeWithText(text(Res.string.signup_app_title)).assertExists()
        onAllNodesWithText(text(Res.string.account_title)).assertCountEquals(0)
    }

    @Test
    fun theInAppCodeStepHasItsOwnTitleAndTheOnboardingKeepsTheOldOne() {
        runDesktopComposeUiTest {
            setContent { AppTheme(darkTheme = false) { AccountContent(AccountUiState(codeSentTo = "a@b.sk"), null, AccountActions(), inApp = true) } }
            onNodeWithText(text(Res.string.signup_app_code_title)).assertExists()
            onAllNodesWithText(text(Res.string.code_title)).assertCountEquals(0)
        }
        runDesktopComposeUiTest {
            setContent { AppTheme(darkTheme = false) { AccountContent(AccountUiState(codeSentTo = "a@b.sk"), UserRole.STUDENT, AccountActions()) } }
            onNodeWithText(text(Res.string.code_title)).assertExists()
        }
    }
}
