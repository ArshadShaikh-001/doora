package com.example.doora.alarm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.doora.DooraApplication
import com.example.doora.R
import com.example.doora.data.model.OccurrenceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val occurrenceId = intent.getLongExtra(AlarmHelper.EXTRA_OCCURRENCE_ID, -1L)
        if (occurrenceId == -1L) return

        val app = context.applicationContext as? DooraApplication ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val occurrence = app.repository.getOccurrenceById(occurrenceId)
                // Only trigger alarm if still PENDING
                if (occurrence == null || occurrence.status != OccurrenceStatus.PENDING) {
                    return@launch
                }

                // If occurrence belongs to a past day and snooze is not actively in the future, mark MISSED
                val today = app.repository.todayString()
                val now = System.currentTimeMillis()
                if (occurrence.scheduledDate < today &&
                    (occurrence.snoozedUntilMillis == null || occurrence.snoozedUntilMillis <= now)
                ) {
                    app.repository.markMissed(occurrence.id)
                    return@launch
                }

                // 1. Build Full-Screen Intent for AlarmActivity
                val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
                    putExtras(intent)
                    putExtra(AlarmHelper.EXTRA_TASK_RINGTONE_URI, occurrence.ringtoneUri)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val fullScreenPendingIntent = PendingIntent.getActivity(
                    context,
                    occurrenceId.toInt(),
                    fullScreenIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // 2. Build Dismiss Action PendingIntent
                val dismissIntent = Intent(context, AlarmActionReceiver::class.java).apply {
                    action = AlarmHelper.ACTION_DISMISS
                    putExtra(AlarmHelper.EXTRA_OCCURRENCE_ID, occurrenceId)
                }
                val dismissPendingIntent = PendingIntent.getBroadcast(
                    context,
                    occurrenceId.toInt() + 200000,
                    dismissIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // 3. Build Snooze Action PendingIntents (10m and 20m for notification)
                fun createSnoozeIntent(minutes: Int): PendingIntent {
                    val snoozeIntent = Intent(context, AlarmActionReceiver::class.java).apply {
                        action = AlarmHelper.ACTION_SNOOZE
                        putExtra(AlarmHelper.EXTRA_OCCURRENCE_ID, occurrenceId)
                        putExtra(AlarmHelper.EXTRA_SNOOZE_MINUTES, minutes)
                    }
                    return PendingIntent.getBroadcast(
                        context,
                        occurrenceId.toInt() + 300000 + minutes,
                        snoozeIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                }

                // 4. Build Notification
                val notification = NotificationCompat.Builder(context, AlarmHelper.CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(occurrence.title)
                    .setContentText("Scheduled at ${com.example.doora.ui.components.formatTime(occurrence.scheduledTimeMinutes)}")
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setFullScreenIntent(fullScreenPendingIntent, true)
                    .setContentIntent(fullScreenPendingIntent)
                    .addAction(0, "Dismiss", dismissPendingIntent)
                    .addAction(0, "Snooze 10m", createSnoozeIntent(10))
                    .addAction(0, "Snooze 20m", createSnoozeIntent(20))
                    .build()

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.notify(occurrenceId.toInt(), notification)

                // 5. Attempt direct launch of AlarmActivity
                try {
                    context.startActivity(fullScreenIntent)
                } catch (_: Exception) {}

            } finally {
                pendingResult.finish()
            }
        }
    }
}
