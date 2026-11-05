package com.madovygabor.maturimate.core.presentation.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavKey
import com.madovygabor.maturimate.core.presentation.navigation.MainFrame.MainFrameNavigationState
import kotlin.test.Test
import kotlin.test.assertEquals

/** The tabs of the main screen: the start tab's stack stays under the open tab, so Back returns to it. */
class MainFrameNavigationStateTest {

    private fun state(open: NavKey) = MainFrameNavigationState(
        startRoute = Route.Main.Subject,
        topLevelRoute = mutableStateOf(open),
        backStacks = emptyMap(),
    )

    @Test
    fun onTheStartTabOnlyItsStackIsShown() {
        assertEquals(listOf<NavKey>(Route.Main.Subject), state(Route.Main.Subject).stacksInUse)
    }

    @Test
    fun anotherTabIsShownOnTopOfTheStartTab() {
        assertEquals(listOf<NavKey>(Route.Main.Subject, Route.Main.Profile), state(Route.Main.Profile).stacksInUse)
    }

    @Test
    fun switchingTabsChangesTheStacks() {
        val state = state(Route.Main.Subject)

        state.topLevelRoute = Route.Main.Practice

        assertEquals(listOf<NavKey>(Route.Main.Subject, Route.Main.Practice), state.stacksInUse)
    }
}
