package com.madovygabor.maturimate.features.appOnboarding.domain

import com.madovygabor.maturimate.core.error.Error
import com.madovygabor.maturimate.core.error.OperationResult
import kotlin.time.ExperimentalTime

/**
 * Validator class responsible for verifying onboarding-related input data.
 */
class OnBoardingDataValidator {

    /**
     * Validates the selected graduation (maturita) date.
     * * @param maturitaDate The selected date in epoch milliseconds.
     * @return [OperationResult.Success] if the date is valid, or [OperationResult.Error] with [MaturitaDateError].
     */
    @OptIn(ExperimentalTime::class)
    fun validateMaturitaDate(maturitaDate: Long?): OperationResult<Unit, MaturitaDateError> {

        // Define a maximum offset for future dates (e.g., 2 years)
        // Calculation: 2 years * 365 days * 24h * 60m * 60s * 1000ms
        val maxFutureDateOffset = 2L * 365 * 24 * 60 * 60 * 1000

        // Using Clock.System for KMP-consistent current time
        val currentTime = kotlin.time.Clock.System.now().toEpochMilliseconds()

        return when {
            maturitaDate == null -> {
                OperationResult.Error(MaturitaDateError.DATE_IS_NULL)
            }
            maturitaDate < currentTime -> {
                OperationResult.Error(MaturitaDateError.DATE_IN_PAST)
            }
            maturitaDate > currentTime + maxFutureDateOffset -> {
                OperationResult.Error(MaturitaDateError.DATE_TOO_FAR_IN_FUTURE)
            }
            else -> {
                OperationResult.Success(Unit)
            }
        }
    }

    /**
     * Specific error types for maturita date validation.
     */
    enum class MaturitaDateError : Error {
        /** The date cannot be in the past. */
        DATE_IN_PAST,
        /** The date is too far (more than 2 years) into the future. */
        DATE_TOO_FAR_IN_FUTURE,
        /** No date was selected. */
        DATE_IS_NULL
    }
}