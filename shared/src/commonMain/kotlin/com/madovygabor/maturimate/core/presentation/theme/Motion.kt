package com.madovygabor.maturimate.core.presentation.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Values from docs/design-system/tokens.json (motion). Change them there first.

/**
 * The motion of the app: restrained, short, one easing curve. Screens and components take every duration, offset and
 * scale from here and ask [spec] for the animation spec, so the system setting "animations off" is respected in one
 * place: with [reduced] every spec is [snap], a change is instant.
 *
 * @param reduced The system turns animations off ([rememberReducedMotion]).
 */
@Immutable
data class Motion(
    /** A reaction: a selection, a press, a color change, something leaving. */
    val durationShort: Int = 200,
    /** Something arriving: a screen, a message, the progress bar, an entrance. */
    val durationMedium: Int = 300,
    /** Delay between the parts of an entrance. */
    val staggerStep: Int = 60,
    /** How far a fading screen or message slides. */
    val slideOffset: Dp = 24.dp,
    /** The size of a pressed button, chip or card. */
    val pressScale: Float = 0.97f,
    val reduced: Boolean = false,
) {
    /** The one easing curve of the app (`easing-standard`). */
    val easing: Easing get() = StandardEasing

    /** The length of an animation of [ms] milliseconds: zero when motion is reduced. */
    fun duration(ms: Int): Int = if (reduced) 0 else ms

    /** An animation of [ms] (default [durationMedium]) with the standard easing; [snap] when motion is reduced. */
    fun <T> spec(ms: Int = durationMedium, delayMs: Int = 0): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(durationMillis = ms, delayMillis = delayMs, easing = easing)

    /** The delay of the [order]th part of an entrance (0 at once). */
    fun stagger(order: Int): Int = order.coerceAtLeast(0) * staggerStep
}

/** `easing-standard`: cubic-bezier(0.2, 0, 0, 1). */
val StandardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

val LocalMotion = staticCompositionLocalOf { Motion() }

/** The motion values, next to [MaterialTheme.shapes] and spacing. */
val MaterialTheme.motion: Motion
    @Composable
    @ReadOnlyComposable
    get() = LocalMotion.current

/** True when the system asks for no animations (Android: animator duration scale 0). Desktop: false. */
@Composable
expect fun rememberReducedMotion(): Boolean

/**
 * A pressed element shrinks to `press-scale` and comes back. Pass the same [source] to the clickable.
 */
@Composable
fun Modifier.pressScale(source: InteractionSource): Modifier {
    val motion = MaterialTheme.motion
    val pressed by source.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed) motion.pressScale else 1f,
        animationSpec = motion.spec(motion.durationShort),
        label = "pressScale",
    )
    return graphicsLayer {
        val s = scale.value
        scaleX = s
        scaleY = s
    }
}

/**
 * A soft entrance: fades in while sliding up by `slide-offset`, once. The [order]th part starts `stagger-step` later
 * than the one before. It plays the first time the screen is shown; coming back to the screen (back navigation, a
 * rebuild) shows it at once, unless [replay] is true (a message, which comes in every time).
 */
@Composable
fun Modifier.entrance(order: Int = 0, replay: Boolean = false): Modifier {
    val motion = MaterialTheme.motion
    var played by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if ((played && !replay) || motion.reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) progress.animateTo(1f, motion.spec(motion.durationMedium, motion.stagger(order)))
        played = true
    }
    val offset = with(LocalDensity.current) { motion.slideOffset.toPx() }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * offset
    }
}
