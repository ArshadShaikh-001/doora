package com.example.doora.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.doora.DooraApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver triggered by AlarmManager when an hourly task completion reminder is due.
 */
class HourlyReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val occurrenceId = intent.getLongExtra(AlarmHelper.EXTRA_OCCURRENCE_ID, -1L)
        if (occurrenceId == -1L) return

        val app = context.applicationContext as? DooraApplication ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.repository.triggerHourlyReminder(occurrenceId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
