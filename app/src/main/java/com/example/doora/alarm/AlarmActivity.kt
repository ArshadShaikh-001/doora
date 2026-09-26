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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.doora.DooraApplication
import com.example.doora.data.model.Priority
import com.example.doora.ui.components.formatTime
import com.example.doora.ui.theme.DooraTheme
import com.example.doora.ui.theme.PriorityHighColor
import com.example.doora.ui.theme.PriorityLowColor
import com.example.doora.ui.theme.PriorityMediumColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmActivity : ComponentActivity() {

    private var occurrenceId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Turn screen on and show above lockscreen
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
    // Subtle pulsating glow
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0B0D12) // Deep calm obsidian for maximum contrast and focus
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section with Glowing Icon & Scheduled Time
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 36.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsing halo
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.2f))
                    )
                    // Core icon circle
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                Text(
                    text = formatTime(timeMinutes),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-0.5).sp
                )

                Spacer(Modifier.height(8.dp))

                val priorityColor = when (priority) {
                    Priority.HIGH -> PriorityHighColor
                    Priority.MEDIUM -> PriorityMediumColor
                    Priority.LOW -> PriorityLowColor
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = priorityColor.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = "${priority.name} PRIORITY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = priorityColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        letterSpacing = 1.sp
                    )
                }
            }

            // Task Title (Centerpiece)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    lineHeight = 36.sp
                )
            }

            // Action Buttons (Ergonomic, massive touch targets)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF04438))
                ) {
                    Text(
                        text = "STOP / DISMISS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                }

                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFE2E3EA)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF333846))
                ) {
                    Text(
                        text = "SNOOZE (10 MIN)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}
