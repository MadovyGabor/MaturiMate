package com.madovygabor.maturimate.features.auth.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * N3: a Google flow whose composition goes away (Android rotation cancels compose-auth's picker, desktop leaves the screen)
 * never called back, so the screen stayed busy. The open flow now reports onClosed when it is disposed.
 */
@OptIn(ExperimentalTestApi::class)
class GoogleSignInTest {

    @Test
    fun anOpenFlowIsClosedWhenItsCompositionGoesAway() = runComposeUiTest {
        var shown by mutableStateOf(true)
        var closed = 0
        lateinit var flow: OpenGoogleFlow
        setContent { if (shown) flow = rememberOpenGoogleFlow(onClosed = { closed++ }) }
        runOnIdle { flow.opened() }

        shown = false
        waitForIdle()

        assertEquals(1, closed)
    }

    @Test
    fun anAnsweredFlowIsNotClosedAgain() = runComposeUiTest {
        var shown by mutableStateOf(true)
        var closed = 0
        lateinit var flow: OpenGoogleFlow
        setContent { if (shown) flow = rememberOpenGoogleFlow(onClosed = { closed++ }) }
        runOnIdle {
            flow.opened()
            // the one callback came (signed in, failed or closed)
            flow.answered()
        }

        shown = false
        waitForIdle()

        assertEquals(0, closed)
    }

    @Test
    fun aFlowNeverStartedIsNotClosed() = runComposeUiTest {
        var shown by mutableStateOf(true)
        var closed = 0
        setContent { if (shown) rememberOpenGoogleFlow(onClosed = { closed++ }) }

        shown = false
        waitForIdle()

        assertEquals(0, closed)
    }
}
