package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AppDialogTest {

    private val defaultLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    @Test
    fun rendersTitleTextAndInvokesConfirmAndDismiss() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        var confirmed = 0
        var dismissed = 0
        setContent {
            AppTheme {
                AppDialog(
                    title = "Kijelentkezel?",
                    text = "A haladásod a fiókodban marad.",
                    confirmText = "Kijelentkezés",
                    onConfirm = { confirmed++ },
                    dismissText = "Mégse",
                    onDismiss = { dismissed++ },
                    destructive = true,
                )
            }
        }

        onNodeWithText("Kijelentkezel?").assertExists()
        onNodeWithText("A haladásod a fiókodban marad.").assertExists()
        onNodeWithText("Kijelentkezés").assertIsEnabled().performClick()
        onNodeWithText("Mégse").assertIsEnabled().performClick()
        assertEquals(1, confirmed)
        assertEquals(1, dismissed)
    }

    @Test
    fun loadingDisablesButtonsAndRendersCustomContent() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        var confirmed = 0
        setContent {
            AppTheme {
                AppDialog(
                    title = "Cím",
                    confirmText = "Mentés",
                    onConfirm = { confirmed++ },
                    confirmLoading = true,
                    dismissText = "Mégse",
                    onDismiss = {},
                    content = { Text("Egyedi tartalom") },
                )
            }
        }

        onNodeWithText("Egyedi tartalom").assertExists()
        onNodeWithText("Mentés").assertIsNotEnabled().performClick()
        onNodeWithText("Mégse").assertIsNotEnabled()
        assertEquals(0, confirmed)
    }
}
