package com.madovygabor.maturimate.features.profileDetails.domain

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.learning.domain.LearningCourse
import com.madovygabor.maturimate.features.learning.domain.LearningError
import com.madovygabor.maturimate.features.learning.domain.RecentActivities
import com.madovygabor.maturimate.features.learning.domain.RecentActivity
import com.madovygabor.maturimate.features.learning.domain.StudyTotals
import com.madovygabor.maturimate.features.learning.domain.StudyTracker
import com.madovygabor.maturimate.features.learning.domain.model.SubjectBreakdown
import com.madovygabor.maturimate.features.userProfile.domain.CatalogSubject
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlin.time.ExperimentalTime

/** A subject of the user. [progress] is empty while the installed content has no such subject ("Hamarosan"). */
data class ProfileSubject(val subject: CatalogSubject, val progress: SubjectBreakdown?)

/** Everything the profile tab shows. */
@OptIn(ExperimentalTime::class)
data class ProfileSummary(
    /** Empty for a guest, who has no name until sign-up. */
    val firstName: String?,
    val streak: Int,
    val totals: StudyTotals,
    val subjects: List<ProfileSubject>,
    val recent: List<RecentActivity>,
    /** Today in the user's time zone, to say "Ma" or "Tegnap" next to an activity. */
    val today: LocalDate,
)

/**
 * Gathers the profile tab: the streak and the study so far ([StudyTracker]), the progress in each of the user's
 * subjects ([LearningCourse]) and the last activities ([RecentActivities]). The profile shows facts only; what to
 * work on is the practice tab's job.
 */
@OptIn(ExperimentalTime::class)
class ProfileOverview(
    private val tracker: StudyTracker,
    private val course: LearningCourse,
    private val recent: RecentActivities,
    private val profile: UserProfileRepository,
) {

    suspend fun load(): OperationResult<ProfileSummary, LearningError> {
        val today = when (val result = tracker.today()) {
            is OperationResult.Success -> result.data.date
            is OperationResult.Error -> return OperationResult.Error(result.error)
        }
        val streak = when (val result = tracker.currentStreak()) {
            is OperationResult.Success -> result.data
            is OperationResult.Error -> return OperationResult.Error(result.error)
        }
        val totals = when (val result = tracker.totals()) {
            is OperationResult.Success -> result.data
            is OperationResult.Error -> return OperationResult.Error(result.error)
        }
        val installed = when (val result = course.breakdown()) {
            is OperationResult.Success -> result.data
            is OperationResult.Error -> return OperationResult.Error(result.error)
        }
        val latest = when (val result = recent.latest(RECENT_COUNT)) {
            is OperationResult.Success -> result.data
            is OperationResult.Error -> return OperationResult.Error(result.error)
        }
        val user = profile.profile.first()
        // the user's subjects in the order of the catalogue, as on the subjects tab
        val subjects = user?.subjects.orEmpty()
            .mapNotNull { CatalogSubject.byId(it.subjectId) }
            .distinct()
            .sortedBy { it.ordinal }
            .map { subject -> ProfileSubject(subject, installed.firstOrNull { it.subjectId == subject.id }) }
        return OperationResult.Success(ProfileSummary(user?.firstName, streak, totals, subjects, latest, today))
    }

    companion object {
        /** The activities the profile shows; the rest is on the full history. */
        const val RECENT_COUNT = 3
    }
}
