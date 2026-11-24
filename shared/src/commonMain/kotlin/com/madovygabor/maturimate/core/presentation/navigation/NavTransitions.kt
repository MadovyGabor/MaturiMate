package com.madovygabor.maturimate.core.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation3.scene.Scene
import com.madovygabor.maturimate.core.presentation.theme.Motion
import com.madovygabor.maturimate.core.presentation.theme.motion

/** A transition of a `NavDisplay`. */
typealias NavTransition<T> = AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform

/**
 * The transitions between screens, one shared set built from the `motion` tokens: a soft fade with a small slide
 * (`slide-offset`), no springs. With reduced motion every change is instant.
 *
 * Give them to a `NavDisplay` (the same set serves every one of them, so the app moves in one way):
 * ```
 * val transitions = rememberNavTransitions()
 * NavDisplay(
 *     transitionSpec = transitions.forward(),
 *     popTransitionSpec = transitions.back(),
 *     predictivePopTransitionSpec = transitions.predictiveBack(),
 * )
 * ```
 * [tabs] is for the destinations of a navigation bar, which are siblings: fade, with a slide up of half the offset,
 * and no direction.
 */
@Stable
class NavTransitions internal constructor(private val motion: Motion, private val offsetPx: Int) {

    /** A new screen opens: it fades in sliding from the end side, the old one fades out sliding a little to the start. */
    fun <T : Any> forward(): NavTransition<T> = { slide(+1) }

    /** Back: the mirror of [forward]. */
    fun <T : Any> back(): NavTransition<T> = { slide(-1) }

    /** The system back gesture (a swipe from an edge) is the same move as [back]. */
    fun <T : Any> predictiveBack(): AnimatedContentTransitionScope<Scene<T>>.(Int) -> ContentTransform = { slide(-1) }

    /** A tab is chosen: a soft fade-through with a small lift. */
    fun <T : Any> tabs(): NavTransition<T> = {
        val enter = fadeIn(motion.spec(motion.durationMedium)) +
            slideInVertically(motion.spec(motion.durationMedium)) { offsetPx / 2 }
        val exit = fadeOut(motion.spec(motion.durationShort)) +
            slideOutVertically(motion.spec(motion.durationShort)) { -offsetPx / 2 }
        enter togetherWith exit
    }

    private fun slide(direction: Int): ContentTransform {
        val enter = fadeIn(motion.spec(motion.durationMedium)) +
            slideInHorizontally(motion.spec(motion.durationMedium)) { direction * offsetPx }
        val exit = fadeOut(motion.spec(motion.durationShort)) +
            slideOutHorizontally(motion.spec(motion.durationShort)) { -direction * offsetPx }
        return enter togetherWith exit
    }
}

/** The shared [NavTransitions] of the current theme and screen density. */
@Composable
fun rememberNavTransitions(): NavTransitions {
    val motion = MaterialTheme.motion
    val density = LocalDensity.current
    return remember(motion, density.density) {
        NavTransitions(motion, with(density) { motion.slideOffset.roundToPx() })
    }
}
