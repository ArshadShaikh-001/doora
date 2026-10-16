package com.example.doora.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.doora.DooraApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            return
        }

        val app = context.applicationContext as? DooraApplication ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Ensure channel
                AlarmHelper.createNotificationChannel(context)
                // First process day transition so past pending become MISSED
                app.repository.processDayTransition()
                // Then reschedule future alarms (does not create catch-up alarms for past)
                app.repository.rescheduleAllFutureAlarms()
                // Reconcile active hourly reminders
                app.repository.reconcileActiveReminders()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
