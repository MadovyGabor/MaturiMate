package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AppCodeFieldLogicTest {

    @Test
    fun typingAddsDigitsUpToSix() {
        assertEquals("1", applyCodeInput("", "1"))
        assertEquals("123456", applyCodeInput("12345", "123456"))
        assertEquals("123456", applyCodeInput("123456", "1234567"))
    }

    @Test
    fun backspaceRemovesTheLastDigit() {
        assertEquals("12", applyCodeInput("123", "12"))
        assertEquals("", applyCodeInput("1", ""))
    }

    @Test
    fun onlyDigitsCount() {
        assertEquals("123", applyCodeInput("", "1a2 3-"))
        assertEquals("", applyCodeInput("", "abc"))
    }

    @Test
    fun aPastedCodeFillsAllBoxesAlsoOnTopOfTypedDigits() {
        assertEquals("123456", applyCodeInput("", "123456"))
        assertEquals("123456", applyCodeInput("", "123 456"))
        assertEquals("123456", applyCodeInput("12", "12123456"))
        assertEquals("987654", applyCodeInput("", "9876543210"))
    }

    @Test
    fun theActiveBoxIsTheNextEmptyOneAndTheLastWhenFull() {
        assertEquals(0, activeCodeBox(""))
        assertEquals(3, activeCodeBox("123"))
        assertEquals(5, activeCodeBox("12345"))
        assertEquals(5, activeCodeBox("123456"))
    }
}

@OptIn(ExperimentalTestApi::class)
class AppCodeFieldTest {

    private val defaultLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    @Test
    fun theFieldKeepsOnlyDigitsAndCutsAtSix() = runComposeUiTest {
        var code by mutableStateOf("")
        setContent { AppTheme(darkTheme = false) { AppCodeField(code, { code = it }, label = "Kod") } }

        onNodeWithContentDescription("Kod").performTextInput("12a3")
        assertEquals("123", code)
        onNodeWithContentDescription("Kod").performTextInput("45678")
        assertEquals("123456", code)
    }

    @Test
    fun aPasteReplacesWhatWasTyped() = runComposeUiTest {
        var code by mutableStateOf("12")
        setContent { AppTheme(darkTheme = false) { AppCodeField(code, { code = it }, label = "Kod") } }

        onNodeWithContentDescription("Kod").performTextReplacement("12123456")

        assertEquals("123456", code)
    }

    @Test
    fun clearingGoesBackToEmpty() = runComposeUiTest {
        var code by mutableStateOf("1234")
        setContent { AppTheme(darkTheme = false) { AppCodeField(code, { code = it }, label = "Kod") } }

        onNodeWithContentDescription("Kod").performTextClearance()

        assertEquals("", code)
    }

    @Test
    fun theScreenReaderGetsOneNamedElementWithItsStateAndError() = runComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        setContent { AppTheme(darkTheme = false) { AppCodeField("123", {}, label = "6 jegyű kód", error = "Hibás kód") } }

        val config = onNodeWithContentDescription("6 jegyű kód").fetchSemanticsNode().config
        assertEquals("3 / 6 jegy beírva", config.getOrNull(SemanticsProperties.StateDescription))
        assertNotNull(config.getOrNull(SemanticsProperties.Error))
        onNodeWithText("Hibás kód").assertExists()
    }
}
