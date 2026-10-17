package com.example.doora.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.doora.data.db.entity.TaskOccurrence
import com.example.doora.data.db.entity.TaskTemplate
import com.example.doora.data.model.OccurrenceStatus
import com.example.doora.data.model.RecurrenceType
import com.example.doora.ui.components.TaskCard
import com.example.doora.ui.components.formatTime
import com.example.doora.ui.theme.GoldBorder
import com.example.doora.ui.theme.GoldBorderSubdued
import com.example.doora.ui.theme.GoldPrimary
import com.example.doora.ui.theme.LuxuryBackground
import com.example.doora.ui.theme.LuxuryCardSurface
import com.example.doora.ui.theme.StatusCompletedColor
import com.example.doora.ui.theme.StatusMissedColor
import com.example.doora.ui.theme.StatusSkippedColor
import com.example.doora.ui.theme.TextCompletedDark
import com.example.doora.ui.theme.TextLuxuryGreeting
import com.example.doora.ui.theme.TextPrimaryDark
import com.example.doora.ui.theme.TextSecondaryDark
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyTimelineView(
    selectedDate: String,
    todayDate: String,
    tasks: List<TaskOccurrence>,
    templateMap: Map<Long, TaskTemplate>,
    timeSnapshot: CurrentTimeSnapshot,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onSelectDate: (String) -> Unit,
    onGoToToday: () -> Unit,
    onCompleteTask: (TaskOccurrence) -> Unit,
    onSkipTask: (TaskOccurrence) -> Unit,
    onEditTask: (Long) -> Unit,
    onDeleteTask: (TaskOccurrence) -> Unit,
    onAddTask: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val isToday = selectedDate == todayDate

    if (showDatePicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = dateStringToUtcMillis(selectedDate)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { onSelectDate(utcMillisToDateString(it)) }
                    showDatePicker = false
                }) {
                    Text("Select", color = GoldPrimary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = TextSecondaryDark)
                }
            }
        ) {
            DatePicker(state = dpState)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Date Navigation Bar
        TimelineDateNavigator(
            selectedDate = selectedDate,
            isToday = isToday,
            onPrevDay = onPrevDay,
            onNextDay = onNextDay,
            onOpenDatePicker = { showDatePicker = true },
            onGoToToday = onGoToToday
        )

        Spacer(Modifier.height(8.dp))

        if (tasks.isEmpty()) {
            TimelineEmptyState(
                isToday = isToday,
                isPast = selectedDate < todayDate,
                onAddTask = onAddTask
            )
        } else {
            // Compute NOW insertion index only when viewing today
            val currentMinutes = timeSnapshot.minutesSinceMidnight
            val nowInsertionIndex = if (isToday) {
                val idx = tasks.indexOfFirst { it.scheduledTimeMinutes > currentMinutes }
                if (idx == -1) tasks.size else idx
            } else {
                -1
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp)
            ) {
                // If NOW belongs at index 0 (before all tasks)
                if (isToday && nowInsertionIndex == 0) {
                    item(key = "timeline_now_indicator") {
                        TimelineNowIndicator(
                            currentMinutes = currentMinutes,
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(250),
                                fadeOutSpec = tween(200),
                                placementSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        )
                    }
                }

                itemsIndexed(
                    items = tasks,
                    key = { _, item -> item.id }
                ) { index, occurrence ->
                    val template = templateMap[occurrence.templateId]
                    val isRecurring = template != null && template.recurrenceType != RecurrenceType.ONE_TIME
                    val isOverdue = occurrence.status == OccurrenceStatus.PENDING && (
                        occurrence.scheduledDate < timeSnapshot.dateStr ||
                        (occurrence.scheduledDate == timeSnapshot.dateStr && timeSnapshot.minutesSinceMidnight >= occurrence.scheduledTimeMinutes)
                    )

                    TimelineItemRow(
                        occurrence = occurrence,
                        isRecurring = isRecurring,
                        isOverdue = isOverdue,
                        onComplete = { onCompleteTask(occurrence) },
                        onSkip = { onSkipTask(occurrence) },
                        onEdit = { onEditTask(occurrence.id) },
                        onDelete = { onDeleteTask(occurrence) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(durationMillis = 300),
                            fadeOutSpec = tween(durationMillis = 200),
                            placementSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    )

                    // Insert NOW indicator between items or after the last item
                    if (isToday && nowInsertionIndex == index + 1) {
                        TimelineNowIndicator(
                            currentMinutes = currentMinutes,
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(250),
                                fadeOutSpec = tween(200),
                                placementSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineDateNavigator(
    selectedDate: String,
    isToday: Boolean,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onOpenDatePicker: () -> Unit,
    onGoToToday: () -> Unit
) {
    val formattedDate = remember(selectedDate) {
        formatTimelineDateHeader(selectedDate)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Previous Day Button
            IconButton(
                onClick = onPrevDay,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(LuxuryCardSurface)
                    .border(1.dp, GoldBorderSubdued, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous Day",
                    tint = GoldPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Interactive Center Date Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(LuxuryCardSurface)
                    .border(
                        1.dp,
                        if (isToday) GoldPrimary.copy(alpha = 0.5f) else GoldBorderSubdued,
                        RoundedCornerShape(16.dp)
                    )
                    .clickable(onClick = onOpenDatePicker)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "Calendar Picker",
                    tint = GoldPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isToday) GoldPrimary else TextPrimaryDark,
                    fontSize = 14.sp
                )
                if (isToday) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = GoldPrimary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldPrimary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Next Day Button
            IconButton(
                onClick = onNextDay,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(LuxuryCardSurface)
                    .border(1.dp, GoldBorderSubdued, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next Day",
                    tint = GoldPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Animated "Go to Today" button when not viewing today
        AnimatedVisibility(
            visible = !isToday,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                OutlinedButton(
                    onClick = onGoToToday,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GoldBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Go to Today",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineItemRow(
    occurrence: TaskOccurrence,
    isRecurring: Boolean,
    isOverdue: Boolean,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCompleted = occurrence.status == OccurrenceStatus.COMPLETED
    val taskColor = Color(occurrence.color)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top
    ) {
        // 1. Time Column along the left side
        Column(
            modifier = Modifier
                .width(54.dp)
                .padding(top = 18.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = formatTime(occurrence.scheduledTimeMinutes),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isCompleted) TextCompletedDark.copy(alpha = 0.7f) else TextSecondaryDark,
                fontSize = 11.5.sp,
                textAlign = TextAlign.End
            )
        }

        Spacer(Modifier.width(10.dp))

        // 2. Vertical Spine & Node Marker Column
        Box(
            modifier = Modifier
                .width(20.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            // Continuous vertical spine track
            Box(
                modifier = Modifier
                    .width(1.5.dp)
                    .fillMaxHeight()
                    .background(GoldBorderSubdued.copy(alpha = 0.45f))
            )

            // Color-coded node marker aligned with card header
            val nodeColor = when {
                isCompleted -> StatusCompletedColor
                occurrence.status == OccurrenceStatus.MISSED -> StatusMissedColor
                occurrence.status == OccurrenceStatus.SKIPPED -> StatusSkippedColor
                else -> taskColor
            }

            Box(
                modifier = Modifier
                    .padding(top = 18.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(nodeColor)
                    .border(2.dp, LuxuryBackground, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(8.dp)
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        // 3. Task Card
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 12.dp)
        ) {
            TaskCard(
                occurrence = occurrence,
                isRecurring = isRecurring,
                isOverdue = isOverdue,
                onComplete = onComplete,
                onSkip = onSkip,
                onEdit = onEdit,
                onDelete = onDelete
            )
        }
    }
}

@Composable
fun TimelineNowIndicator(
    currentMinutes: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Time column showing "NOW" badge + current time
        Column(
            modifier = Modifier.width(54.dp),
            horizontalAlignment = Alignment.End
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = GoldPrimary.copy(alpha = 0.16f)
            ) {
                Text(
                    text = "NOW",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatTime(currentMinutes),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = GoldPrimary
            )
        }

        Spacer(Modifier.width(10.dp))

        // Center spine with radiant gold node marker
        Box(
            modifier = Modifier
                .width(20.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(1.5.dp)
                    .fillMaxHeight()
                    .background(GoldBorderSubdued.copy(alpha = 0.45f))
            )
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(GoldPrimary)
                    .border(2.dp, LuxuryBackground, CircleShape)
            )
        }

        Spacer(Modifier.width(8.dp))

        // Right side radiant horizontal indicator line
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.5.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            GoldPrimary,
                            GoldPrimary.copy(alpha = 0.45f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
private fun TimelineEmptyState(
    isToday: Boolean,
    isPast: Boolean,
    onAddTask: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 60.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color(0x18DFC083)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = GoldPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.height(18.dp))

            val title = when {
                isToday -> "No tasks scheduled for today"
                isPast -> "No tasks recorded for this date"
                else -> "No tasks scheduled for this date"
            }

            val subtitle = when {
                isToday -> "Your timeline is clear. Take a breath or schedule something new."
                isPast -> "There are no historical tasks recorded for this date."
                else -> "Plan ahead and schedule tasks for this upcoming day."
            }

            Text(
                text = title,
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextLuxuryGreeting
                ),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryDark,
                textAlign = TextAlign.Center
            )

            if (!isPast) {
                Spacer(Modifier.height(22.dp))

                OutlinedButton(
                    onClick = onAddTask,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, GoldBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary)
                ) {
                    Text("Schedule a Task", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun formatTimelineDateHeader(dateStr: String): String {
    val date = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr) }.getOrNull() ?: Date()
    return SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(date)
}

private fun dateStringToUtcMillis(dateStr: String): Long {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return runCatching { sdf.parse(dateStr)?.time }.getOrNull() ?: System.currentTimeMillis()
}

private fun utcMillisToDateString(millis: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return sdf.format(Date(millis))
}
