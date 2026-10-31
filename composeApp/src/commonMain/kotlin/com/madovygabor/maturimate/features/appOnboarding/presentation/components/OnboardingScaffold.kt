package com.madovygabor.maturimate.features.appOnboarding.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.madovygabor.maturimate.core.presentation.systemPadding
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButton
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButtonVariant
import com.madovygabor.maturimate.core.presentation.uiComponents.AppLogo
import com.madovygabor.maturimate.core.presentation.uiComponents.AppProgressBar
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingProgress
import maturimate.composeapp.generated.resources.Res
import maturimate.composeapp.generated.resources.app_name
import maturimate.composeapp.generated.resources.common_back
import maturimate.composeapp.generated.resources.onboarding_side_tagline
import maturimate.composeapp.generated.resources.onboarding_step
import org.jetbrains.compose.resources.stringResource

/** A button at the bottom of an onboarding screen. */
data class OnboardingAction(
    val text: String,
    val onClick: () -> Unit,
    val variant: AppButtonVariant = AppButtonVariant.FILLED,
    val enabled: Boolean = true,
)

/**
 * The frame of every onboarding screen, following docs/onboarding/prototype.html:
 * one column on a phone; a centered column from `breakpoint-medium`; from
 * `breakpoint-expanded` two panes, with the logo, a short text and the step list on the left.
 *
 * @param actions The buttons at the bottom, the main one first.
 * @param stackedActions True: the buttons fill the width, one under the other, at every size
 *   (welcome, account). False: from `breakpoint-medium` they sit in a row on the right.
 * @param progress Shows the back button, the step count and the step list; null on the welcome screen.
 * @param content The screen's own content, scrolled when it does not fit.
 */
@Composable
fun OnboardingScaffold(
    actions: List<OnboardingAction>,
    modifier: Modifier = Modifier,
    stackedActions: Boolean = false,
    progress: OnboardingProgress? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemPadding(),
    ) {
        val layout = MaterialTheme.layout
        val wide = maxWidth >= layout.breakpointMedium
        if (maxWidth >= layout.breakpointExpanded) {
            Row(Modifier.fillMaxSize()) {
                SidePanel(progress, Modifier.width(layout.sidePanelWidth).fillMaxHeight())
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MainColumn(wide = true, actions, stackedActions, progress, content)
            }
        } else {
            MainColumn(wide, actions, stackedActions, progress, content)
        }
    }
}

@Composable
private fun MainColumn(
    wide: Boolean,
    actions: List<OnboardingAction>,
    stackedActions: Boolean,
    progress: OnboardingProgress?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val layout = MaterialTheme.layout
    val sideMargin = if (wide) spacing.space6 else spacing.space4
    val columnModifier = Modifier.widthIn(max = layout.contentMaxWidth).fillMaxWidth()
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (progress != null) {
            StepHeader(progress, columnModifier.padding(start = spacing.space2, end = sideMargin, top = spacing.space3))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .then(columnModifier)
                .padding(horizontal = sideMargin, vertical = if (wide) spacing.space8 else spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space4),
            content = content,
        )
        val actionsModifier = columnModifier.padding(start = sideMargin, end = sideMargin, top = spacing.space3, bottom = spacing.space6)
        if (stackedActions || !wide) {
            Column(actionsModifier, verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
                actions.forEach { ActionButton(it, Modifier.fillMaxWidth()) }
            }
        } else {
            // main action on the right, as in the prototype
            Row(actionsModifier, horizontalArrangement = Arrangement.spacedBy(spacing.space2, Alignment.End)) {
                actions.asReversed().forEach { ActionButton(it, Modifier.widthIn(min = layout.buttonMinWidth)) }
            }
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
    )
}

/** Back button, "1. lépés / 8" and the progress bar. */
@Composable
private fun StepHeader(progress: OnboardingProgress, modifier: Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space2),
    ) {
        IconButton(onClick = progress.onBack) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(Res.string.common_back),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space2),
        ) {
            Text(
                text = stringResource(Res.string.onboarding_step, progress.number, progress.steps.size).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppProgressBar(fraction = progress.number.toFloat() / progress.steps.size)
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
            style = MaterialTheme.typography.bodyMedium,
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
    Column(verticalArrangement = Arrangement.spacedBy(spacing.space1)) {
        progress.steps.forEachIndexed { index, step ->
            val number = index + 1
            val done = number < progress.number
            val current = number == progress.number
            val stepColor = if (current) colors.onPrimaryContainer else colors.onSurfaceVariant
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (current) colors.primaryContainer else Color.Transparent,
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
                        color = if (done) colors.primary else Color.Transparent,
                        contentColor = if (done) colors.onPrimary else stepColor,
                        border = if (done) null else BorderStroke(layout.borderThick, if (current) stepColor else colors.outline),
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
