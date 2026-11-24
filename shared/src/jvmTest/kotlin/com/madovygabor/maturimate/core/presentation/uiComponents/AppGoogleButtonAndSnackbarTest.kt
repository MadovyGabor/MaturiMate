package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.utils.UiText
import com.madovygabor.maturimate.core.presentation.utils.snackbar.MainScaffoldWrapper
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarKind
import com.madovygabor.maturimate.testutil.TEST_TIMEOUT
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AppGoogleButtonTest {

    @Test
    fun itIsAButtonThatCallsBack() = runComposeUiTest {
        var clicks = 0
        setContent { AppTheme(darkTheme = false) { AppGoogleButton("Folytatás Google-lel", { clicks++ }) } }

        val button = onNode(androidx.compose.ui.test.hasClickAction())
        assertEquals(Role.Button, button.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        onNodeWithText("Folytatás Google-lel").assertExists()
        button.performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun aDisabledButtonDoesNothing() = runComposeUiTest {
        var clicks = 0
        setContent { AppTheme(darkTheme = true) { AppGoogleButton("Google", { clicks++ }, enabled = false) } }

        onNodeWithText("Google").assertIsNotEnabled().performClick()

        assertEquals(0, clicks)
    }

    @Test
    fun itWorksInBothThemes() = runComposeUiTest {
        var dark by androidx.compose.runtime.mutableStateOf(false)
        setContent { AppTheme(darkTheme = dark) { AppGoogleButton("Google", {}) } }
        onNodeWithText("Google").assertIsEnabled()
        dark = true
        waitForIdle()
        onNodeWithText("Google").assertIsEnabled()
    }
}

@OptIn(ExperimentalTestApi::class)
class AppSnackbarTest {

    private val defaultLocale: Locale = Locale.getDefault()
    private val controller = SnackbarController()

    @BeforeTest
    fun start() {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        startKoin { modules(module { single { controller } }) }
    }

    @AfterTest
    fun stop() {
        stopKoin()
        Locale.setDefault(defaultLocale)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.show(kind: SnackbarKind, text: String) {
        runBlocking { controller.showSnackbar(UiText.DynamicString(text), kind = kind) }
        waitUntil(timeoutMillis = TEST_TIMEOUT.inWholeMilliseconds) {
            onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun theKindTravelsWithTheEvent() = runBlocking {
        val c = SnackbarController()
        c.showSnackbar(UiText.DynamicString("x"), kind = SnackbarKind.ERROR)
        c.showSnackbar(UiText.DynamicString("y"))
        assertEquals(SnackbarKind.ERROR, c.events.first().kind)
    }

    @Test
    fun anErrorMessageHasItsIconAndASuccessHasItsOwn() = runComposeUiTest {
        setContent { AppTheme(darkTheme = false) { MainScaffoldWrapper { Box(Modifier.fillMaxSize()) } } }

        show(SnackbarKind.ERROR, "Nem sikerült")
        onNodeWithContentDescription("Hiba").assertExists()
        onNodeWithContentDescription("Kész").assertDoesNotExist()
    }

    @Test
    fun aPlainMessageHasNoIcon() = runComposeUiTest {
        setContent { AppTheme(darkTheme = false) { MainScaffoldWrapper { Box(Modifier.fillMaxSize()) } } }

        show(SnackbarKind.INFO, "Csak egy üzenet")
        onNodeWithContentDescription("Hiba").assertDoesNotExist()
        onNodeWithContentDescription("Kész").assertDoesNotExist()
    }
}
