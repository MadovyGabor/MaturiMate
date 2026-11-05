package com.madovygabor.maturimate.features.learning.presentation

import androidx.compose.runtime.Composable
import com.madovygabor.maturimate.core.presentation.uiComponents.ActivityIcon
import com.madovygabor.maturimate.core.presentation.uiComponents.TagStyle
import com.madovygabor.maturimate.features.appOnboarding.presentation.subjects.label
import com.madovygabor.maturimate.features.learning.domain.ActivityKind
import com.madovygabor.maturimate.features.learning.domain.RecentActivity
import com.madovygabor.maturimate.features.userProfile.domain.CatalogSubject
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.activity_result_points
import maturimate.shared.generated.resources.activity_subjects
import maturimate.shared.generated.resources.home_activity_meta
import maturimate.shared.generated.resources.practice_free_title
import maturimate.shared.generated.resources.practice_mistakes_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.ExperimentalTime

/*
 * How a finished activity is named in the lists of the home screen, the profile and the history. A lesson and a
 * test have their unit's title and topic; a practice round is named by its kind and the subjects it asked about.
 */

/** The icon of the activity's card. */
@OptIn(ExperimentalTime::class)
internal val RecentActivity.icon: ActivityIcon
    get() = when (kind) {
        ActivityKind.LESSON -> ActivityIcon.LESSON
        ActivityKind.TEST -> ActivityIcon.TEST
        ActivityKind.PRACTICE, ActivityKind.MISTAKE_PRACTICE -> ActivityIcon.PRACTICE
    }

/** The title: the unit's, or "Szabad gyakorlás" / "Hibagyakorlás". */
@OptIn(ExperimentalTime::class)
@Composable
internal fun activityTitle(activity: RecentActivity): String = when (activity.kind) {
    ActivityKind.PRACTICE -> stringResource(Res.string.practice_free_title)
    ActivityKind.MISTAKE_PRACTICE -> stringResource(Res.string.practice_mistakes_title)
    else -> activity.title
}

/** Where the activity belongs: the topic of a lesson or test; the subject of a practice round, or "2 tantárgy". */
@OptIn(ExperimentalTime::class)
@Composable
internal fun activityPlace(activity: RecentActivity): String {
    if (!activity.kind.isPractice) return activity.topicTitle
    val subjects = activity.subjectIds
    return when (subjects.size) {
        0 -> ""
        1 -> CatalogSubject.byId(subjects.single())?.let { stringResource(it.label) }.orEmpty()
        else -> pluralStringResource(Res.plurals.activity_subjects, subjects.size, subjects.size)
    }
}

/** The place and the day in one line ("Szóképek · Ma"); only the day when there is no place. */
@Composable
internal fun placeAndDay(place: String, day: String): String =
    if (place.isEmpty()) day else stringResource(Res.string.home_activity_meta, place, day)

/** The result tag of a practice round: "8 / 10", green when every answer was right. Practice has no passing. */
@OptIn(ExperimentalTime::class)
@Composable
internal fun practiceResult(activity: RecentActivity): Pair<String, TagStyle> {
    val earned = activity.earnedPoints ?: 0
    val max = activity.maxPoints ?: 0
    return stringResource(Res.string.activity_result_points, earned, max) to
        if (max > 0 && earned == max) TagStyle.SUCCESS else TagStyle.NEUTRAL
}
