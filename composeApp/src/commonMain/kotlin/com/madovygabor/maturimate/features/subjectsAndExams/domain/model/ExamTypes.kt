package com.madovygabor.maturimate.features.subjectsAndExams.domain.model

/**
 * Defines the supported interaction types for exam questions.
 * Each type requires a different UI component to render and validate answers.
 */
enum class TaskType {
    /** Multiple choice with 4 options (A, B, C, D). */
    CHOICE,

    /** Free text input for short answers. */
    INPUT,

    /** Special Math-oriented choice with 5 options (A, B, C, D, E). */
    CHOICE_5,

    /** Language task where a word must be transformed (e.g., noun to adjective). */
    WORD_FORMATION,

    /** Selection from a shared pool of options (e.g., fitting sentences into a text). */
    GAP_FILL_SELECTION,

    /** Fallback for unsupported or legacy task types. */
    UNKNOWN
}

/**
 * Defines the type of material associated with a set of tasks.
 * Helps the UI decide whether to show a text reader, an audio player, or a gap-fill interface.
 */
enum class ContentUnitType {
    /** Standard reading passage with HTML formatting. */
    TEXT,

    /** Audio resource for listening comprehension tasks. */
    AUDIO,

    /** A complex unit where sentences or phrases must be fitted into a text. */
    GAP_FILL,

    /** Fallback for unsupported or unknown content types. */
    UNKNOWN
}