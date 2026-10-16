package com.example.doora.alarm

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.example.doora.data.db.entity.TaskOccurrence
import com.example.doora.data.model.OccurrenceStatus
import com.example.doora.data.model.Priority
import com.example.doora.ui.components.formatTime
import com.example.doora.ui.theme.DooraTheme
import com.example.doora.ui.theme.GoldPrimary
import com.example.doora.ui.theme.PriorityHighColor
import com.example.doora.ui.theme.PriorityLowColor
import com.example.doora.ui.theme.PriorityMediumColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class AlarmActivity : ComponentActivity() {

    private var occurrenceId: Long = -1L
    private var isActionHandled: Boolean = false

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
        val initialTitle = intent.getStringExtra(AlarmHelper.EXTRA_TASK_TITLE) ?: "Task Reminder"
        val initialColorInt = intent.getIntExtra(AlarmHelper.EXTRA_TASK_COLOR, 0xFF42A5F5.toInt())
        val initialPriorityStr = intent.getStringExtra(AlarmHelper.EXTRA_TASK_PRIORITY) ?: Priority.MEDIUM.name
        val initialPriority = runCatching { Priority.valueOf(initialPriorityStr) }.getOrDefault(Priority.MEDIUM)
        val initialTimeMinutes = intent.getIntExtra(AlarmHelper.EXTRA_TASK_TIME, 0)
        val initialDateStr = intent.getStringExtra(AlarmHelper.EXTRA_TASK_DATE)
            ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val initialRingtoneUri = intent.getStringExtra(AlarmHelper.EXTRA_TASK_RINGTONE_URI)

        val repo = (application as DooraApplication).repository

        // Start ringing and vibrating
        AlarmSoundPlayer.start(this, initialRingtoneUri)

        setContent {
            DooraTheme {
                var occurrence by remember { mutableStateOf<TaskOccurrence?>(null) }
                var showRescheduleDialog by remember { mutableStateOf(false) }

                LaunchedEffect(occurrenceId) {
                    if (occurrenceId != -1L) {
                        val occ = repo.getOccurrenceById(occurrenceId)
                        // If task is no longer pending, stop and close immediately
                        if (occ == null || occ.status != OccurrenceStatus.PENDING) {
                            handleDismiss(silentOnly = true)
                            return@LaunchedEffect
                        }
                        occurrence = occ
                        if (initialRingtoneUri.isNullOrBlank() && !occ.ringtoneUri.isNullOrBlank()) {
                            AlarmSoundPlayer.start(this@AlarmActivity, occ.ringtoneUri)
                        }
                    }
                }

                val activeTitle = occurrence?.title ?: initialTitle
                val activeTime = occurrence?.scheduledTimeMinutes ?: initialTimeMinutes
                val activeDate = occurrence?.scheduledDate ?: initialDateStr
                val activePriority = occurrence?.priority ?: initialPriority
                val activeColor = occurrence?.color?.let { Color(it) } ?: Color(initialColorInt)

                AlarmScreen(
                    title = activeTitle,
                    timeMinutes = activeTime,
                    priority = activePriority,
                    color = activeColor,
                    onDismiss = { handleDismiss() },
                    onSnooze = { minutes -> handleSnooze(minutes) },
                    onRescheduleClick = {
                        // Pause audio while user interacts with time/date selection
                        AlarmSoundPlayer.stop()
                        showRescheduleDialog = true
                    }
                )

                if (showRescheduleDialog) {
                    RescheduleDialog(
                        initialDate = activeDate,
                        initialTimeMinutes = activeTime,
                        taskTitle = activeTitle,
                        taskColor = activeColor,
                        onDismissRequest = {
                            showRescheduleDialog = false
                        },
                        onValidateAndConfirm = { newDate, newTime, onResult ->
                            if (isActionHandled) return@RescheduleDialog
                            CoroutineScope(Dispatchers.IO).launch {
                                val result = repo.rescheduleOccurrence(occurrenceId, newDate, newTime)
                                withContext(Dispatchers.Main) {
                                    result.fold(
                                        onSuccess = {
                                            isActionHandled = true
                                            clearNotification()
                                            AlarmSoundPlayer.stop()
                                            Toast.makeText(
                                                this@AlarmActivity,
                                                "Rescheduled to $newDate at ${formatTime(newTime)}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            finish()
                                        },
                                        onFailure = { err ->
                                            onResult(err.message ?: "Failed to reschedule task")
                                        }
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    private fun handleDismiss(silentOnly: Boolean = false) {
        if (isActionHandled) return
        isActionHandled = true
        AlarmSoundPlayer.stop()
        clearNotification()
        if (!silentOnly && occurrenceId != -1L) {
            val repo = (application as DooraApplication).repository
            CoroutineScope(Dispatchers.IO).launch {
                repo.clearSnooze(occurrenceId)
                repo.startHourlyReminder(occurrenceId)
            }
        }
        finish()
    }

    private fun handleSnooze(minutes: Int) {
        if (isActionHandled) return
        isActionHandled = true
        AlarmSoundPlayer.stop()
        clearNotification()
        if (occurrenceId != -1L) {
            val repo = (application as DooraApplication).repository
            CoroutineScope(Dispatchers.IO).launch {
                repo.snoozeOccurrence(occurrenceId, minutes)
            }
            Toast.makeText(this, "Snoozed for $minutes minutes", Toast.LENGTH_SHORT).show()
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
    onSnooze: (minutes: Int) -> Unit,
    onRescheduleClick: () -> Unit
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
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section with Glowing Icon & Scheduled Time
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
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

                Spacer(Modifier.height(24.dp))

                Text(
                    text = formatTime(timeMinutes),
                    fontSize = 42.sp,
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

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Primary STOP / DISMISS Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(16.dp),
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

                // 2. Snooze Options Section (10m, 15m, 20m)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "SNOOZE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF8A90A2),
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(10, 15, 20).forEach { mins ->
                            OutlinedButton(
                                onClick = { onSnooze(mins) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color(0xFF161922),
                                    contentColor = Color(0xFFE2E3EA)
                                ),
                                border = BorderStroke(1.2.dp, Color(0xFF333846))
                            ) {
                                Text(
                                    text = "$mins min",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // 3. Separate Reschedule Action Button
                OutlinedButton(
                    onClick = onRescheduleClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF141720),
                        contentColor = GoldPrimary
                    ),
                    border = BorderStroke(1.2.dp, GoldPrimary.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = GoldPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Reschedule",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = GoldPrimary,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

/**
 * Clean, modern dialog allowing user to choose a new date and time for this occurrence.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RescheduleDialog(
    initialDate: String,
    initialTimeMinutes: Int,
    taskTitle: String,
    taskColor: Color,
    onDismissRequest: () -> Unit,
    onValidateAndConfirm: (newDate: String, newTimeMinutes: Int, onResult: (String?) -> Unit) -> Unit
) {
    val scope = rememberCoroutineScope()

    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val tomorrowStr = remember {
        val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
    }

    var selectedDate by remember { mutableStateOf(if (initialDate < todayStr) todayStr else initialDate) }
    // Default time is 15 minutes ahead if today, otherwise initial time
    val defaultInitialTime = remember {
        val nowCal = Calendar.getInstance()
        val currentMins = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
        if (selectedDate == todayStr && initialTimeMinutes <= currentMins) {
            (currentMins + 15) % 1440
        } else {
            initialTimeMinutes
        }
    }
    var selectedTimeMinutes by remember { mutableIntStateOf(defaultInitialTime) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Date Picker Dialog
    if (showDatePicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = dateStringToUtcMillis(selectedDate)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let {
                        val picked = utcMillisToDateString(it)
                        selectedDate = picked
                        errorMessage = null
                    }
                    showDatePicker = false
                }) { Text("Confirm", color = GoldPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = dpState)
        }
    }

    // Time Picker Dialog
    if (showTimePicker) {
        val tpState = rememberTimePickerState(
            initialHour = selectedTimeMinutes / 60,
            initialMinute = selectedTimeMinutes % 60,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select New Time", fontWeight = FontWeight.SemiBold) },
            text = { TimePicker(state = tpState) },
            confirmButton = {
                TextButton(onClick = {
                    selectedTimeMinutes = tpState.hour * 60 + tpState.minute
                    errorMessage = null
                    showTimePicker = false
                }) { Text("Confirm", color = GoldPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = Color(0xFF141720),
        shape = RoundedCornerShape(20.dp),
        title = {
            Column {
                Text(
                    text = "Reschedule Task",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(taskColor)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = taskTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB0B6C8),
                        maxLines = 1
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Date Selector Section
                Column {
                    Text(
                        text = "DATE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF8A90A2),
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedDate == todayStr,
                            onClick = {
                                selectedDate = todayStr
                                errorMessage = null
                            },
                            label = { Text("Today") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = GoldPrimary
                            )
                        )
                        FilterChip(
                            selected = selectedDate == tomorrowStr,
                            onClick = {
                                selectedDate = tomorrowStr
                                errorMessage = null
                            },
                            label = { Text("Tomorrow") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = GoldPrimary
                            )
                        )
                        FilterChip(
                            selected = selectedDate != todayStr && selectedDate != tomorrowStr,
                            onClick = { showDatePicker = true },
                            label = { Text("Pick Date") },
                            leadingIcon = {
                                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = GoldPrimary
                            )
                        )
                    }
                    Text(
                        text = formatDisplayDate(selectedDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = GoldPrimary,
                        modifier = Modifier.padding(top = 4.dp, start = 2.dp)
                    )
                }

                // Time Selector Section
                Column {
                    Text(
                        text = "NEW TIME",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF8A90A2),
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTimePicker = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2230)),
                        border = BorderStroke(1.dp, Color(0xFF333846))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(selectedTimeMinutes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Change Time",
                                tint = GoldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Conflict / Error display
                if (errorMessage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0x2BF04438)),
                        border = BorderStroke(1.dp, Color(0xFFF04438).copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFA49D),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onValidateAndConfirm(selectedDate, selectedTimeMinutes) { err ->
                        errorMessage = err
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
            ) {
                Text(
                    text = "Confirm Reschedule",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = "Cancel", color = Color(0xFF8A90A2))
            }
        }
    )
}

private fun dateStringToUtcMillis(dateStr: String): Long {
    return try {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).also {
            it.timeZone = TimeZone.getTimeZone("UTC")
        }.parse(dateStr)?.time ?: System.currentTimeMillis()
    } catch (_: Exception) {
        System.currentTimeMillis()
    }
}

private fun utcMillisToDateString(millis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).also {
        it.timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(millis))
}

private fun formatDisplayDate(dateStr: String): String {
    if (dateStr.isBlank()) return ""
    return try {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr) ?: return dateStr
        SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(parsed)
    } catch (_: Exception) {
        dateStr
    }
}
