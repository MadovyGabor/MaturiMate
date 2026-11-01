package com.madovygabor.maturimate.features.appOnboarding.domain

import com.madovygabor.maturimate.core.domain.error.Error
import com.madovygabor.maturimate.core.domain.error.OperationResult
import kotlinx.datetime.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Validator class responsible for verifying onboarding-related input data.
 * This implementation uses [LocalDate] for all comparisons to ensure
 * KMP compatibility and type safety.
 */
class MaturitaValidator {

    companion object {
        private const val MAX_YEARS_IN_FUTURE = 3
    }

    /**
     * Validates the selected graduation (maturita) date.
     * * @param maturitaDate The selected date as a [LocalDate].
     * @return [OperationResult.Success] if the date is valid,
     * or [OperationResult.Error] with [MaturitaDateError].
     */
    @OptIn(ExperimentalTime::class)
    fun validateDate(maturitaDate: LocalDate?): OperationResult<Unit, MaturitaDateError> {
        if (maturitaDate == null) {
            return OperationResult.Error(MaturitaDateError.DATE_IS_NULL)
        }

        // 1. Get current date in the user's system timezone
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

        // 2. Define a maximum offset (e.g., 3 years from today)
        val maxFutureDate = today.plus(DatePeriod(years = MAX_YEARS_IN_FUTURE))

        return when {
            // Check if date is in the past
            maturitaDate < today -> {
                OperationResult.Error(MaturitaDateError.DATE_IN_PAST)
            }
            // Check if the date is unrealistically far in the future
            maturitaDate > maxFutureDate -> {
                OperationResult.Error(MaturitaDateError.DATE_TOO_FAR_IN_FUTURE)
            }
            // Everything is correct
            else -> {
                OperationResult.Success(Unit)
            }
        }
    }

    /**
     * Specific error types for maturita date validation.
     */
    enum class MaturitaDateError : Error {
        DATE_IN_PAST,
        DATE_TOO_FAR_IN_FUTURE,
        DATE_IS_NULL
    }
}