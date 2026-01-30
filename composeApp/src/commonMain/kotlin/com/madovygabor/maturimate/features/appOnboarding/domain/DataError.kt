package com.madovygabor.maturimate.features.appOnboarding.domain

import com.madovygabor.maturimate.core.domain.error.Error

/**
 * Represents specific errors that can occur during data persistence or retrieval
 * within the onboarding feature.
 */
enum class DataError : Error {
    /**
     * Triggered when the device storage is full and no more data can be written.
     */
    STORAGE_FULL,

    /**
     * Triggered by general Input/Output failures, such as file corruption
     * or permission issues.
     */
    IO_ERROR,

    /**
     * A fallback error for unexpected exceptions that are not explicitly handled.
     */
    UNKNOWN
}