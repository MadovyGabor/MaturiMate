package com.madovygabor.maturimate.core.presentation.utils.snackbar

import com.madovygabor.maturimate.testutil.TEST_TIMEOUT
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.madovygabor.maturimate.core.presentation.MainFrame
import com.madovygabor.maturimate.core.presentation.navigation.Route
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.utils.UiText
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The one snackbar host sits above the bottom bar of the main frame and at the bottom of every other screen (the
 * onboarding, a lesson): the offset is the height the main frame reports, not a number written in the host.
 */
@OptIn(ExperimentalTestApi::class)
class SnackbarOffsetScreenTest {

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

    /** The space between the bottom of the window and the bottom of the snackbar, in pixels. */
    private fun ComposeUiTest.gapBelowSnackbar(): Float {
        runBlocking { controller.showSnackbar(UiText.DynamicString("Mentve")) }
        waitUntil(timeoutMillis = TEST_TIMEOUT.inWholeMilliseconds) { onAllNodesWithTextCount("Mentve") > 0 }
        val bounds = onNodeWithText("Mentve").fetchSemanticsNode().boundsInRoot
        return onRoot().fetchSemanticsNode().size.height - bounds.bottom
    }

    private fun ComposeUiTest.onAllNodesWithTextCount(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size

    @Test
    fun outsideTheMainFrameTheOffsetIsZero() = runDesktopComposeUiTest(412, 915) {
        lateinit var offset: BottomBarHeight
        setContent {
            AppTheme { MainScaffoldWrapper { offset = LocalBottomBarHeight.current; Box(Modifier.fillMaxSize()) } }
        }
        waitForIdle()

        assertEquals(0.dp, offset.value)
    }

    @Test
    fun inTheMainFrameTheSnackbarClearsTheBottomBar() {
        var gapPlain = 0f
        runDesktopComposeUiTest(412, 915) {
            setContent { AppTheme { MainScaffoldWrapper { Box(Modifier.fillMaxSize()) } } }
            gapPlain = gapBelowSnackbar()
        }

        var gapFramed = 0f
        var barHeightPx = 0f
        runDesktopComposeUiTest(412, 915) {
            lateinit var offset: BottomBarHeight
            setContent {
                AppTheme {
                    MainScaffoldWrapper {
                        offset = LocalBottomBarHeight.current
                        MainFrame(selectedKey = Route.Main.Home, onSelectedKey = {}) {}
                    }
                }
            }
            waitForIdle()
            assertTrue(offset.value > 0.dp, "the bar reports its height: ${offset.value}")
            barHeightPx = offset.value.value * density.density
            gapFramed = gapBelowSnackbar()
        }

        // the host is raised by exactly the height of the bar
        assertEquals(barHeightPx, gapFramed - gapPlain, 1f)
    }

    @Test
    fun theRailOfAWideWindowHasNoBottomBar() = runDesktopComposeUiTest(1280, 800) {
        lateinit var offset: BottomBarHeight
        setContent {
            AppTheme {
                MainScaffoldWrapper {
                    offset = LocalBottomBarHeight.current
                    MainFrame(selectedKey = Route.Main.Home, onSelectedKey = {}) {}
                }
            }
        }
        waitForIdle()

        assertEquals(0.dp, offset.value)
    }
}
