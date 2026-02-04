package com.madovygabor.maturimate.features.subjectsAndExams.domain.model

import com.madovygabor.maturimate.core.domain.error.Error

/**
 * Domain-specific errors for exam-related operations.
 * These errors map technical failures (SQL, JSON) to business-level concepts.
 */
enum class ExamError : Error {
    /**
     * Occurs when a requested exam ID does not exist in the local database.
     */
    NOT_FOUND,

    /**
     * Triggered when JSON synchronization fails due to invalid format or missing required fields.
     */
    PARSE_ERROR,

    /**
     * General failure during database read/write operations (e.g., storage full, file corruption).
     */
    DATABASE_ERROR,

    /**
     * Fallback for unexpected or unhandled error cases.
     */
    UNKNOWN
}