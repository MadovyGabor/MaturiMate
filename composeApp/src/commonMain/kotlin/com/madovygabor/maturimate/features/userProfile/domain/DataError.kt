package com.madovygabor.maturimate.features.userProfile.domain

import com.madovygabor.maturimate.core.domain.error.Error

/** Errors of saving or reading the user's data on the phone. */
enum class DataError : Error {
    /** The device storage is full. */
    STORAGE_FULL,

    /** Any other failure of the local database. */
    IO_ERROR,
}
