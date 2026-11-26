package com.madovygabor.maturimate.features.appOnboarding.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import com.madovygabor.maturimate.core.presentation.systemPadding
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.motion
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButton
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButtonVariant
import com.madovygabor.maturimate.core.presentation.uiComponents.AppLogo
import com.madovygabor.maturimate.core.presentation.uiComponents.AppProgressBar
import com.madovygabor.maturimate.features.appOnboarding.presentation.LocalEditingAnswer
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingProgress
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.app_name
import maturimate.shared.generated.resources.common_back
import maturimate.shared.generated.resources.onboarding_side_tagline
import maturimate.shared.generated.resources.onboarding_step
import org.jetbrains.compose.resources.stringResource

/** A button at the bottom of an onboarding screen. */
data class OnboardingAction(
    val text: String,
    val onClick: () -> Unit,
    val variant: AppButtonVariant = AppButtonVariant.FILLED,
    val enabled: Boolean = true,
    /** Work is under way: the button shows a spinner. */
    val loading: Boolean = false,
)

/**
 * The frame of every onboarding screen, following docs/onboarding/prototype.html:
 * one column on a phone; a centered column from `breakpoint-medium`; from
 * `breakpoint-expanded` two panes, with the logo, a short text and the step list on the left.
 *
 * @param actions The buttons at the bottom, the main one first.
 * @param stackedActions True: the buttons fill the width, one under the other, at every size
 *   (welcome, account). False: from `breakpoint-medium` they sit in a row on the right.
 * @param centered The content is centered in the free height (the welcome screen); it still scrolls when it does not fit.
 * @param progress Shows the back button, the step count and the step list; null on the welcome screen. On arriving, the
 *   progress bar and the step list move on from the previous step (`duration-medium`).
 * @param onBack A back button without a step count, for screens outside the steps (sign-in).
 * @param content The screen's own content, scrolled when it does not fit.
 */
@Composable
fun OnboardingScaffold(
    actions: List<OnboardingAction>,
    modifier: Modifier = Modifier,
    stackedActions: Boolean = false,
    centered: Boolean = false,
    progress: OnboardingProgress? = null,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // opened from the profile: no steps, a plain back button, and no side panel next to the app's own navigation
    val editing = LocalEditingAnswer.current
    @Suppress("NAME_SHADOWING") val onBack = if (editing) onBack ?: progress?.onBack else onBack
    @Suppress("NAME_SHADOWING") val progress = progress.takeUnless { editing }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemPadding()
            // the keyboard takes its height off the screen: the scrolling form shrinks with it, so the field being typed in,
            // the fields below it and the buttons stay reachable (edge-to-edge windows are not resized by the system)
            .imePadding(),
    ) {
        val layout = MaterialTheme.layout
        val wide = maxWidth >= layout.breakpointMedium
        if (maxWidth >= layout.breakpointExpanded && !editing) {
            Row(Modifier.fillMaxSize()) {
                SidePanel(progress, Modifier.width(layout.sidePanelWidth).fillMaxHeight())
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MainColumn(wide = true, actions, stackedActions, centered, progress, onBack, content)
            }
        } else {
            MainColumn(wide, actions, stackedActions, centered, progress, onBack, content)
        }
    }
}

@Composable
private fun MainColumn(
    wide: Boolean,
    actions: List<OnboardingAction>,
    stackedActions: Boolean,
    centered: Boolean,
    progress: OnboardingProgress?,
    onBack: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val layout = MaterialTheme.layout
    val sideMargin = if (wide) spacing.space6 else spacing.space4
    val columnModifier = Modifier.widthIn(max = layout.contentMaxWidth).fillMaxWidth()
    // With the keyboard open the buttons do not ride up on top of it, where they would take a third of the little room left
    // for the form: they follow the last field instead, at the end of the scrolling form (as in the big apps), and the
    // form gets the whole height above the keyboard. Without the keyboard they stay at the bottom edge.
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0 && !centered
    val actionsBlock: @Composable (Modifier) -> Unit = { modifier ->
        if (stackedActions || !wide) {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
                actions.forEach { ActionButton(it, Modifier.fillMaxWidth()) }
            }
        } else {
            // main action on the right, as in the prototype
            Row(modifier, horizontalArrangement = Arrangement.spacedBy(spacing.space2, Alignment.End)) {
                actions.asReversed().forEach { ActionButton(it, Modifier.widthIn(min = layout.buttonMinWidth)) }
            }
        }
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val headerModifier = columnModifier.padding(start = spacing.space2, end = sideMargin, top = spacing.space3)
        if (progress != null) {
            StepHeader(progress, headerModifier)
        } else if (onBack != null) {
            Row(headerModifier) { BackButton(onBack) }
        }
        val contentPadding = Modifier.padding(horizontal = sideMargin, vertical = if (wide) spacing.space8 else spacing.space4)
        if (centered) {
            // the scrolling column is as tall as its content, so the box around it can center it
            Box(Modifier.weight(1f).then(columnModifier), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).then(contentPadding),
                    verticalArrangement = Arrangement.spacedBy(spacing.space4),
                    content = content,
                )
            }
        } else {
            val scroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scroll)
                    .then(columnModifier)
                    .then(contentPadding),
                verticalArrangement = Arrangement.spacedBy(spacing.space4),
            ) {
                content()
                if (keyboardOpen) actionsBlock(Modifier.fillMaxWidth().padding(top = spacing.space2))
            }
            // content runs on under the buttons: a thin line shows that the form goes on, so a half-hidden line does not look cut off
            if (scroll.canScrollForward && !keyboardOpen) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (!keyboardOpen) {
            actionsBlock(columnModifier.padding(start = sideMargin, end = sideMargin, top = spacing.space3, bottom = spacing.space3))
        }
    }
}

@Composable
private fun ActionButton(action: OnboardingAction, modifier: Modifier) {
    AppButton(
        text = action.text,
        onClick = action.onClick,
        modifier = modifier,
        variant = action.variant,
        enabled = action.enabled,
        loading = action.loading,
    )
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(
            Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(Res.string.common_back),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Back button, "1. lépés / 8" and the progress bar. */
@Composable
private fun StepHeader(progress: OnboardingProgress, modifier: Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space2),
    ) {
        BackButton(progress.onBack)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space2),
        ) {
            Text(
                text = stringResource(Res.string.onboarding_step, progress.number, progress.steps.size).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppProgressBar(fraction = rememberArrivingStep(progress.number).toFloat() / progress.steps.size)
        }
    }
}

@Composable
private fun SidePanel(progress: OnboardingProgress?, modifier: Modifier) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = spacing.space8, vertical = spacing.space8),
        verticalArrangement = Arrangement.spacedBy(spacing.space6),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            AppLogo(
                size = MaterialTheme.layout.logoSmall,
                contentDescription = null,
                tileShape = MaterialTheme.shapes.medium,
            )
            Text(
                text = stringResource(Res.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = stringResource(Res.string.onboarding_side_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (progress != null) StepList(progress)
    }
}

/** The steps, each done, current or still ahead. */
@Composable
private fun StepList(progress: OnboardingProgress) {
    val colors = MaterialTheme.colorScheme
    val layout = MaterialTheme.layout
    val spacing = MaterialTheme.spacing
    val motion = MaterialTheme.motion
    val colorSpec = motion.spec<Color>(motion.durationMedium)
    // arriving at a step, the list moves on from the previous one: the old one empties, the new one fills
    val shown = rememberArrivingStep(progress.number)
    Column(verticalArrangement = Arrangement.spacedBy(spacing.space1)) {
        progress.steps.forEachIndexed { index, step ->
            val number = index + 1
            val done = number < shown
            val current = number == shown
            val stepColor by animateColorAsState(if (current) colors.onPrimaryContainer else colors.onSurfaceVariant, colorSpec, label = "stepColor")
            val rowColor by animateColorAsState(if (current) colors.primaryContainer else Color.Transparent, colorSpec, label = "stepRow")
            val dotColor by animateColorAsState(if (done) colors.primary else Color.Transparent, colorSpec, label = "stepDot")
            val dotContent by animateColorAsState(if (done) colors.onPrimary else stepColor, colorSpec, label = "stepDotContent")
            val dotBorder by animateColorAsState(if (done) Color.Transparent else if (current) stepColor else colors.outline, colorSpec, label = "stepDotBorder")
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = rowColor,
                contentColor = stepColor,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.space3, vertical = spacing.space2),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.space3),
                ) {
                    Surface(
                        modifier = Modifier.size(layout.stepDot),
                        shape = CircleShape,
                        color = dotColor,
                        contentColor = dotContent,
                        border = if (done) null else BorderStroke(layout.borderThick, dotBorder),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (done) {
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(layout.iconSmall))
                            } else {
                                Text(number.toString(), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    Text(stringResource(step.label), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

/**
 * The step to show: on arriving, the one before [number] for one frame, then [number], so the progress bar and the
 * step list animate from the previous step to this one instead of appearing finished. The first step has none before it.
 */
@Composable
private fun rememberArrivingStep(number: Int): Int {
    var arrived by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { arrived = true }
    return if (arrived || number <= 1) number else number - 1
}
