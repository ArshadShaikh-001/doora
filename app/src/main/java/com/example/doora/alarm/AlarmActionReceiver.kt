package com.example.doora.alarm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.doora.DooraApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val occurrenceId = intent.getLongExtra(AlarmHelper.EXTRA_OCCURRENCE_ID, -1L)
        if (occurrenceId == -1L) return

        AlarmSoundPlayer.stop()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(occurrenceId.toInt())

        when (intent.action) {
            AlarmHelper.ACTION_DISMISS -> {
                val app = context.applicationContext as? DooraApplication ?: return
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        app.repository.clearSnooze(occurrenceId)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            AlarmHelper.ACTION_SNOOZE -> {
                val minutes = intent.getIntExtra(AlarmHelper.EXTRA_SNOOZE_MINUTES, 10)
                val app = context.applicationContext as? DooraApplication ?: return
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        app.repository.snoozeOccurrence(occurrenceId, minutes)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
