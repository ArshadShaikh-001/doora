package com.example.doora.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.example.doora.data.db.entity.TaskOccurrence
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object AlarmHelper {

    const val CHANNEL_ID = "doora_alarm_channel"
    const val EXTRA_OCCURRENCE_ID = "extra_occurrence_id"
    const val EXTRA_TASK_TITLE = "extra_task_title"
    const val EXTRA_TASK_COLOR = "extra_task_color"
    const val EXTRA_TASK_PRIORITY = "extra_task_priority"
    const val EXTRA_TASK_TIME = "extra_task_time"

    const val ACTION_DISMISS = "com.example.doora.ACTION_DISMISS_ALARM"
    const val ACTION_SNOOZE = "com.example.doora.ACTION_SNOOZE_ALARM"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val defaultRingtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Doora Task Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts for scheduled tasks"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                setSound(defaultRingtoneUri, audioAttributes)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * Calculates trigger epoch millis for a task occurrence.
     * Uses [snoozedUntilMillis] if active, otherwise computes from scheduledDate + scheduledTimeMinutes.
     */
    fun computeTriggerMillis(occurrence: TaskOccurrence): Long {
        if (occurrence.snoozedUntilMillis != null && occurrence.snoozedUntilMillis > 0L) {
            return occurrence.snoozedUntilMillis
        }
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = runCatching { sdf.parse(occurrence.scheduledDate) }.getOrNull() ?: return 0L
        val cal = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, occurrence.scheduledTimeMinutes / 60)
            set(Calendar.MINUTE, occurrence.scheduledTimeMinutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /**
     * Schedules exact alarm for the given task occurrence.
     * If trigger time is in the past, no alarm is scheduled.
     */
    fun scheduleAlarm(context: Context, occurrence: TaskOccurrence) {
        val triggerAtMillis = computeTriggerMillis(occurrence)
        val now = System.currentTimeMillis()

        // Do not schedule if time already passed
        if (triggerAtMillis <= now) {
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_OCCURRENCE_ID, occurrence.id)
            putExtra(EXTRA_TASK_TITLE, occurrence.title)
            putExtra(EXTRA_TASK_COLOR, occurrence.color)
            putExtra(EXTRA_TASK_PRIORITY, occurrence.priority.name)
            putExtra(EXTRA_TASK_TIME, occurrence.scheduledTimeMinutes)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            occurrence.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Show alarm clock info if possible for reliable OEM wakeups
        val showIntent = Intent(context, com.example.doora.MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            occurrence.id.toInt() + 100000,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent)
        try {
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } catch (_: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }
    }

    /**
     * Cancels any scheduled alarm for the given occurrence id.
     */
    fun cancelAlarm(context: Context, occurrenceId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            occurrenceId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
