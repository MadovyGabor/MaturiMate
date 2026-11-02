package com.madovygabor.maturimate.core.presentation.utils.snackbar

import com.madovygabor.maturimate.core.presentation.utils.UiText
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

class SnackbarControllerTest {

    // Sending used to wait until the snackbar on screen went away (about 4 seconds), and the
    // ViewModels kept their buttons busy meanwhile.
    @Test
    fun sendingDoesNotWaitForTheScreen() = runBlocking {
        val controller = SnackbarController()

        // nobody collects yet, as while a snackbar is shown: both sends return at once
        withTimeout(1_000) {
            controller.showSnackbar(UiText.DynamicString("first"))
            controller.showSnackbar(UiText.DynamicString("second"))
        }

        val shown = withTimeout(1_000) { controller.events.take(2).toList() }
        assertEquals(listOf("first", "second"), shown.map { (it.message as UiText.DynamicString).value })
    }
}
