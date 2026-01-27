package com.madovygabor.maturimate.core.error

typealias RootError = Error

/**
 * A generic wrapper for operations that can either result in success or failure.
 * [D] Represents the success Data type.
 * [E] Represents the Error type, which must implement [RootError].
 */
sealed interface OperationResult<out D, out E : RootError> {
    /**
     * Represents a successful operation containing the resulting [data].
     */
    data class Success<out D, out E: RootError>(val data: D): OperationResult<D, E>
    /**
     * Represents a failed operation containing the specific [error].
     */
    data class Error<out D, out E: RootError>(val error: E): OperationResult<D, E>

}