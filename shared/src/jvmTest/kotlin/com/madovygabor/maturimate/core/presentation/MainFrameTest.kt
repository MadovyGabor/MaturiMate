package com.madovygabor.maturimate.core.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.navigation3.runtime.NavKey
import com.madovygabor.maturimate.core.presentation.navigation.Route
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MainFrameTest {

    private val defaultLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    @Test
    fun theFourTabsInHungarianWithoutTheOldHistoryTab() = runDesktopComposeUiTest(412, 915) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        var selected: NavKey? = null
        setContent { AppTheme { MainFrame(selectedKey = Route.Main.Home, onSelectedKey = { selected = it }) {} } }

        listOf("Kezdőlap", "Tantárgyak", "Gyakorlás", "Profil").forEach { onNodeWithText(it).assertExists() }
        onAllNodesWithText("Előzmények").assertCountEquals(0)
        onNodeWithText("Gyakorlás").performClick()
        assertEquals(Route.Main.Practice, selected)
    }

    @Test
    fun theTabsInSlovakOnTheRailOfAWideWindow() = runDesktopComposeUiTest(1280, 800) {
        Locale.setDefault(Locale.forLanguageTag("sk"))
        setContent { AppTheme { MainFrame(selectedKey = Route.Main.Home, onSelectedKey = {}) {} } }

        listOf("Domov", "Predmety", "Cvičenie", "Profil").forEach { onNodeWithText(it).assertExists() }
    }
}
