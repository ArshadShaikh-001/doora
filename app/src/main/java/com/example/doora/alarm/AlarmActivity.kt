package com.example.doora.alarm

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.doora.DooraApplication
import com.example.doora.data.model.Priority
import com.example.doora.ui.components.formatTime
import com.example.doora.ui.theme.DooraTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmActivity : ComponentActivity() {

    private var occurrenceId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Configure window to turn screen on and show above keyguard/lockscreen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        occurrenceId = intent.getLongExtra(AlarmHelper.EXTRA_OCCURRENCE_ID, -1L)
        val title = intent.getStringExtra(AlarmHelper.EXTRA_TASK_TITLE) ?: "Task Reminder"
        val colorInt = intent.getIntExtra(AlarmHelper.EXTRA_TASK_COLOR, 0xFF42A5F5.toInt())
        val priorityStr = intent.getStringExtra(AlarmHelper.EXTRA_TASK_PRIORITY) ?: Priority.MEDIUM.name
        val priority = runCatching { Priority.valueOf(priorityStr) }.getOrDefault(Priority.MEDIUM)
        val timeMinutes = intent.getIntExtra(AlarmHelper.EXTRA_TASK_TIME, 0)

        // Start ringing and vibrating
        AlarmSoundPlayer.start(this)

        setContent {
            DooraTheme {
                AlarmScreen(
                    title = title,
                    timeMinutes = timeMinutes,
                    priority = priority,
                    color = Color(colorInt),
                    onDismiss = { handleDismiss() },
                    onSnooze = { handleSnooze() }
                )
            }
        }
    }

    private fun handleDismiss() {
        AlarmSoundPlayer.stop()
        clearNotification()
        finish()
    }

    private fun handleSnooze() {
        AlarmSoundPlayer.stop()
        clearNotification()
        if (occurrenceId != -1L) {
            val repo = (application as DooraApplication).repository
            CoroutineScope(Dispatchers.IO).launch {
                repo.snoozeOccurrence(occurrenceId, 10)
            }
        }
        finish()
    }

    private fun clearNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (occurrenceId != -1L) {
            nm?.cancel(occurrenceId.toInt())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AlarmSoundPlayer.stop()
    }
}

@Composable
fun AlarmScreen(
    title: String,
    timeMinutes: Int,
    priority: Priority,
    color: Color,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 40.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(color.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = formatTime(timeMinutes),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(Modifier.height(8.dp))

                val priorityColor = when (priority) {
                    Priority.HIGH -> Color(0xFFEF5350)
                    Priority.MEDIUM -> Color(0xFFFFA726)
                    Priority.LOW -> Color(0xFF66BB6A)
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = priorityColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${priority.name} PRIORITY",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = priorityColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Task Title
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(
                        text = "DISMISS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onError
                    )
                }

                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "SNOOZE (10 MIN)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
