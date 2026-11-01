package com.madovygabor.maturimate.features.userProfile.domain

/** The level of an English exam ([CatalogSubject.ENGLISH_B1], [CatalogSubject.ENGLISH_B2]). */
enum class SubjectLevel { B1, B2 }

/** A subject the user studies or teaches (`user_subject`). */
data class UserSubject(
    val subjectId: Long,
    /** Compulsory for the user's exam (`source` = AUTO); picked by the user otherwise (CHOSEN). */
    val compulsory: Boolean,
    /** Not written since English became two subjects (DECISIONS 2026-11-01); the column stays. */
    val level: SubjectLevel? = null,
)

/**
 * The exam subjects the onboarding offers (docs/onboarding/README.md, section 3). The ids and
 * codes are the subject rows of the content: they never change, and content written for a
 * subject uses them (tools/content-pipeline/README.md). One subject is one exam, so English
 * B1 and B2 are two subjects.
 *
 * @param onlyFor The teaching language whose schools have this subject; null for every school.
 * @param compulsory Compulsory exam subject, preselected and locked for students.
 * @param level The English level of the exam; null for the other subjects.
 */
enum class CatalogSubject(
    val id: Long,
    val code: String,
    val onlyFor: TeachingLanguage?,
    val compulsory: Boolean,
    val level: SubjectLevel? = null,
) {
    HUNGARIAN(1, "HU_MJL", onlyFor = TeachingLanguage.HU, compulsory = true),
    /** Slovak in Hungarian schools ("Szlovák nyelv és szlovák irodalom"), a different exam from [SLOVAK]. */
    SLOVAK_HU(5, "HU_SJSL", onlyFor = TeachingLanguage.HU, compulsory = true),
    SLOVAK(2, "SK_SJL", onlyFor = TeachingLanguage.SK, compulsory = true),
    ENGLISH_B1(3, "ANJ_B1", onlyFor = null, compulsory = true, level = SubjectLevel.B1),
    ENGLISH_B2(6, "ANJ_B2", onlyFor = null, compulsory = true, level = SubjectLevel.B2),
    MATHEMATICS(4, "MAT", onlyFor = null, compulsory = false);

    companion object {
        /** The subjects of a school with this teaching language, in the order they are shown. */
        fun forLanguage(language: TeachingLanguage?): List<CatalogSubject> =
            entries.filter { it.onlyFor == null || it.onlyFor == language }

        /**
         * The subjects a user can pick. A teacher may teach at a school of either language, so
         * a teacher gets every subject, the own school's first; everyone else gets the subjects
         * of their school.
         */
        fun offeredTo(role: UserRole?, language: TeachingLanguage?): List<CatalogSubject> =
            if (role == UserRole.TEACHER) forLanguage(language) + entries.filterNot { it in forLanguage(language) }
            else forLanguage(language)

        /** The English subject of a level. */
        fun english(level: SubjectLevel): CatalogSubject = entries.first { it.level == level }

        fun byId(id: Long): CatalogSubject? = entries.firstOrNull { it.id == id }
    }
}
