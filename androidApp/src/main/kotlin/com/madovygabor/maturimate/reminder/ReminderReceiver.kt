package com.madovygabor.maturimate.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.madovygabor.maturimate.MainActivity
import com.madovygabor.maturimate.R
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.learning.domain.StudyTracker
import com.madovygabor.maturimate.features.reminder.domain.ReminderKind
import com.madovygabor.maturimate.features.reminder.domain.ReminderMessage
import com.madovygabor.maturimate.features.reminder.domain.ReminderScheduler
import com.madovygabor.maturimate.features.userProfile.domain.ExamCalendar
import com.madovygabor.maturimate.features.userProfile.domain.ExamDateRepository
import com.madovygabor.maturimate.features.reminder.domain.chooseReminder
import com.madovygabor.maturimate.features.reminder.domain.handleReminderAlarm
import com.madovygabor.maturimate.features.reminder.domain.publicReminderMessage
import com.madovygabor.maturimate.features.reminder.domain.reminderLanguageTag
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val CHANNEL_ID = "daily_reminder"
private const val NOTIFICATION_ID = 1

/**
 * The alarm of the daily reminder. It shows the notification unless the day's goal is already reached (or the
 * reminder was switched off meanwhile), and plans the next day's alarm either way.
 */
@OptIn(ExperimentalTime::class)
class ReminderReceiver : BroadcastReceiver(), KoinComponent {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                var profile: UserProfile? = null
                handleReminderAlarm(
                    scheduler = get<ReminderScheduler>(),
                    fallbackTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time,
                    readTime = {
                        profile = get<UserProfileRepository>().profile.first()
                        profile?.takeIf { it.reminderEnabled }?.reminderTime
                    },
                    show = { profile?.let { showIfDue(context, it) } },
                )
            } finally {
                pending.finish()
            }
        }
    }

    /** Shows the notification unless the day's goal is already reached. */
    private suspend fun showIfDue(context: Context, profile: UserProfile) {
        val tracker = get<StudyTracker>()
        if ((tracker.today() as? OperationResult.Success)?.data?.reached == true) return
        val streak = (tracker.currentStreak() as? OperationResult.Success)?.data ?: 0
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val message = chooseReminder(
            streak = streak,
            hadStreak = tracker.hadStreak(),
            // the official date when it replaces the rule's (the same calendar as the home screen)
            daysToExam = ExamCalendar(get<ExamDateRepository>().dates.first()).effectiveDate(profile.graduationDate)?.let { today.daysUntil(it) },
            dayNumber = today.toEpochDays(),
        )
        show(context, profile.uiLanguage, message)
    }

    /** The text of the message: the kind and the variant pick the resource, the number fills it in. */
    private fun text(texts: Context, message: ReminderMessage): String {
        val number = message.number ?: 0
        return when (message.kind) {
            ReminderKind.PLAIN -> texts.getString(PLAIN[message.variant])
            ReminderKind.COMEBACK -> texts.getString(COMEBACK[message.variant])
            ReminderKind.STREAK_SHORT -> texts.getString(STREAK_SHORT[message.variant], number)
            ReminderKind.STREAK_LONG -> texts.getString(STREAK_LONG[message.variant], number)
            ReminderKind.EXAM -> texts.resources.getQuantityString(EXAM[message.variant], number, number)
        }
    }

    private companion object {
        val PLAIN = listOf(R.string.reminder_plain_1, R.string.reminder_plain_2, R.string.reminder_plain_3)
        val COMEBACK = listOf(R.string.reminder_comeback_1, R.string.reminder_comeback_2)
        val STREAK_SHORT = listOf(R.string.reminder_streak_short_1, R.string.reminder_streak_short_2, R.string.reminder_streak_short_3)
        val STREAK_LONG = listOf(R.string.reminder_streak_long_1, R.string.reminder_streak_long_2, R.string.reminder_streak_long_3)
        val EXAM = listOf(R.plurals.reminder_exam_1, R.plurals.reminder_exam_2)
    }

    private fun show(context: Context, language: TeachingLanguage, message: ReminderMessage) {
        // the text follows the language saved in the profile, not the one of the phone
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(reminderLanguageTag(language)))
        val texts = context.createConfigurationContext(configuration)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, texts.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { lockscreenVisibility = Notification.VISIBILITY_PRIVATE }
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // on the lock screen only the name and a neutral line: no streak, no days to the exam
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(texts.getString(R.string.app_name))
            .setContentText(text(texts, publicReminderMessage()))
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(texts.getString(R.string.app_name))
            .setContentText(text(texts, message))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // the permission was taken back since the check
        }
    }
}
