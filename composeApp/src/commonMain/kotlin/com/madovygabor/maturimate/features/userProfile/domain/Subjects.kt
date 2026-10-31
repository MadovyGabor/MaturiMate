package com.madovygabor.maturimate.features.userProfile.domain

/** Level of a subject that has levels (English). "I don't know yet" is saved as [B1]. */
enum class SubjectLevel { B1, B2 }

/** A subject the user studies or teaches (`user_subject`). */
data class UserSubject(
    val subjectId: Long,
    /** Compulsory for the user's exam (`source` = AUTO); picked by the user otherwise (CHOSEN). */
    val compulsory: Boolean,
    val level: SubjectLevel? = null,
)

/**
 * The exam subjects the onboarding offers (docs/onboarding/README.md, section 3). The ids and
 * codes are the subject rows of the content: they never change, and content written for a
 * subject uses them (tools/content-pipeline/README.md).
 *
 * @param onlyFor The teaching language whose schools have this subject; null for every school.
 * @param compulsory Compulsory exam subject, preselected and locked for students.
 * @param hasLevels Asks for the level (B1 or B2).
 */
enum class CatalogSubject(
    val id: Long,
    val code: String,
    val onlyFor: TeachingLanguage?,
    val compulsory: Boolean,
    val hasLevels: Boolean = false,
) {
    HUNGARIAN(1, "HU_MJL", onlyFor = TeachingLanguage.HU, compulsory = true),
    SLOVAK(2, "SK_SJL", onlyFor = null, compulsory = true),
    ENGLISH(3, "ANJ", onlyFor = null, compulsory = true, hasLevels = true),
    MATHEMATICS(4, "MAT", onlyFor = null, compulsory = false);

    companion object {
        /** The subjects of a school with this teaching language, in the order they are shown. */
        fun forLanguage(language: TeachingLanguage?): List<CatalogSubject> =
            entries.filter { it.onlyFor == null || it.onlyFor == language }

        fun byId(id: Long): CatalogSubject? = entries.firstOrNull { it.id == id }
    }
}
