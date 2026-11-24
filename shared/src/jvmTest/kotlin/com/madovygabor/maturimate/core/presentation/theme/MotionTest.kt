package com.madovygabor.maturimate.core.presentation.theme

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MotionTest {

    @AfterTest
    fun clearProperty() {
        System.clearProperty("maturimate.reducedMotion")
    }

    @Test
    fun aSpecUsesTheTokenDurationAndTheOneEasing() {
        val motion = Motion()
        val spec = assertIs<TweenSpec<Float>>(motion.spec<Float>(motion.durationShort, delayMs = 60))
        assertEquals(200, spec.durationMillis)
        assertEquals(60, spec.delay)
        assertEquals(StandardEasing, spec.easing)
        assertEquals(300, assertIs<TweenSpec<Float>>(motion.spec<Float>()).durationMillis)
    }

    @Test
    fun reducedMotionMakesEverySpecInstant() {
        val motion = Motion(reduced = true)
        assertIs<SnapSpec<Float>>(motion.spec<Float>())
        assertIs<SnapSpec<Float>>(motion.spec<Float>(motion.durationShort, delayMs = 120))
        assertEquals(0, motion.duration(300))
    }

    @Test
    fun theStaggerStepsAddUpAndNeverGoBelowZero() {
        val motion = Motion()
        assertEquals(0, motion.stagger(0))
        assertEquals(120, motion.stagger(2))
        assertEquals(0, motion.stagger(-3))
    }

    @Test
    fun theThemeProvidesMotionAndFollowsTheSystemSetting() = runComposeUiTest {
        var reduced: Boolean? = null
        setContent { AppTheme(darkTheme = false) { reduced = MaterialTheme.motion.reduced } }
        waitForIdle()
        assertFalse(reduced!!)
    }

    @Test
    fun theDesktopSettingTurnsMotionOff() = runComposeUiTest {
        System.setProperty("maturimate.reducedMotion", "true")
        var reduced: Boolean? = null
        setContent { AppTheme(darkTheme = false) { reduced = MaterialTheme.motion.reduced } }
        waitForIdle()
        assertTrue(reduced!!)
    }
}
