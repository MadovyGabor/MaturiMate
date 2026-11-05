package com.madovygabor.maturimate.features.practice.presentation

import maturimate.shared.generated.resources.practice_mistakes_title
import com.madovygabor.maturimate.features.practice.domain.PracticeMode
import androidx.compose.runtime.Composable
import com.madovygabor.maturimate.features.learning.presentation.RoundScreen
import com.madovygabor.maturimate.features.learning.presentation.TestSessionViewModel
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.practice_free_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named

/** The Koin name of the view-model of a free practice round (the same class as a test round, another source). */
const val PRACTICE_ROUND = "practiceRound"

/**
 * A practice round of the chosen topics (free practice, or with [mistakes] the mistake practice; no topic means
 * every mistake): the tasks one after the other like in a test, then the result with "Még egy kör". Closing it after
 * some answers shows their result; it never marks a step of the path as done.
 */
@Composable
fun PracticeRoundScreen(topicIds: List<Long>, count: Int, mistakes: Boolean, onClose: () -> Unit) {
    val title = stringResource(if (mistakes) Res.string.practice_mistakes_title else Res.string.practice_free_title)
    val mode = if (mistakes) PracticeMode.MISTAKES else PracticeMode.FREE
    val viewModel: TestSessionViewModel = koinViewModel(
        qualifier = named(PRACTICE_ROUND),
        key = "practice$mode${topicIds.joinToString(",")}x$count",
    ) { parametersOf(topicIds, count, title, mode) }
    RoundScreen(viewModel, onClose)
}
