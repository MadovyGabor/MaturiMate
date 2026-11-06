package com.madovygabor.maturimate.features.learning.domain

import com.madovygabor.maturimate.core.domain.error.OperationResult

/**
 * Helpers for a calculation that reads content and progress in many steps: a failed read ends the whole
 * calculation with its error, so the code between the reads does not check every result.
 */

/** Carries the error of a failed read out of the middle of a calculation. */
internal class ReadFailure(val error: LearningError) : Exception()

/** The data of a successful read; a failed read leaves the calculation (see [guarded]). */
internal fun <T> OperationResult<T, LearningError>.orThrow(): T = when (this) {
    is OperationResult.Success -> data
    is OperationResult.Error -> throw ReadFailure(error)
}

/** Runs [block]; the error of the first failed read inside it becomes the result. */
internal suspend fun <T> guarded(block: suspend () -> T): OperationResult<T, LearningError> =
    try {
        OperationResult.Success(block())
    } catch (e: ReadFailure) {
        OperationResult.Error(e.error)
    }
