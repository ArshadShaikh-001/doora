package com.example.doora.ui.statistics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.doora.data.engine.DailyDateMetrics
import com.example.doora.data.engine.DailyInsightsData
import com.example.doora.data.engine.DailyTaskGraphItem
import com.example.doora.data.engine.DailyTaskStreakSummary
import com.example.doora.data.engine.DailyTrendDay
import com.example.doora.data.engine.MonthlyTrendSummary
import com.example.doora.data.engine.StreakAndStatsEngine
import com.example.doora.data.model.OccurrenceStatus
import com.example.doora.data.repository.TaskRepository
import com.example.doora.ui.components.formatTime
import com.example.doora.ui.theme.GoldBorder
import com.example.doora.ui.theme.GoldBorderSubdued
import com.example.doora.ui.theme.GoldPillBg
import com.example.doora.ui.theme.GoldPrimary
import com.example.doora.ui.theme.GoldSecondary
import com.example.doora.ui.theme.LuxuryBackground
import com.example.doora.ui.theme.LuxuryCardSurface
import com.example.doora.ui.theme.StatusCompletedColor
import com.example.doora.ui.theme.StatusMissedColor
import com.example.doora.ui.theme.StatusSkippedColor
import com.example.doora.ui.theme.TextLuxuryGreeting
import com.example.doora.ui.theme.TextPrimaryDark
import com.example.doora.ui.theme.TextSecondaryDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(repository: TaskRepository) {
    val vm: StatisticsViewModel = viewModel(factory = StatisticsViewModel.Factory(repository))
    val state by vm.state.collectAsState()
    val insights = state.insights

    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = dateStringToUtcMillis(state.selectedDate)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { vm.selectDate(utcMillisToDateString(it)) }
                    showDatePicker = false
                }) {
                    Text("Select", color = GoldPrimary)
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

    Scaffold(
        containerColor = LuxuryBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Screen Header
            InsightsHeader()

            // Date Navigation Bar
            DateNavigationBar(
                selectedDate = state.selectedDate,
                todayDate = state.todayDate,
                onPreviousClick = vm::goToPreviousDay,
                onNextClick = vm::goToNextDay,
                onDateClick = { showDatePicker = true },
                onTodayClick = vm::goToToday
            )

            if (!insights.hasAnyDailyTasks) {
                // Empty state when user has not yet configured any daily tasks
                NoDailyTasksEmptyState()
            } else {
                // 1. Daily Task Graph (for the selected date)
                DailyTaskGraphCard(metrics = insights.selectedDateMetrics)

                // 2. Weekly Completion Trend (Last 7 Days)
                WeeklyTrendCard(
                    weeklyTrend = insights.weeklyTrend,
                    selectedDate = state.selectedDate,
                    onDayClick = vm::selectDate
                )

                // 3. Monthly Completion Trend
                MonthlyTrendCard(monthly = insights.monthlyTrend)

                // 4. Performance Highlights: Consistent Performer & Needs Attention
                PerformanceHighlightsRow(insights = insights)

                // 5. Streaks & All-Time Daily Summary
                DailyStreaksSummaryCard(insights = insights)
            }

            Spacer(Modifier.height(88.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

@Composable
private fun InsightsHeader() {
    Column {
        Text(
            text = "Insights",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextLuxuryGreeting,
            letterSpacing = (-0.5).sp
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = "Daily recurring task analysis and trends",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondaryDark
        )
    }
}

// ---------------------------------------------------------------------------
// Date Navigation
// ---------------------------------------------------------------------------

@Composable
private fun DateNavigationBar(
    selectedDate: String,
    todayDate: String,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onDateClick: () -> Unit,
    onTodayClick: () -> Unit
) {
    val displayDateText = remember(selectedDate, todayDate) {
        StreakAndStatsEngine.formatDisplayDate(selectedDate, todayDate)
    }
    val isNotToday = selectedDate.isNotBlank() && selectedDate != todayDate

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Previous day button
            Surface(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 20.dp),
                        onClick = onPreviousClick
                    ),
                shape = CircleShape,
                color = LuxuryCardSurface,
                border = BorderStroke(1.dp, GoldBorderSubdued)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous Day",
                        tint = GoldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Date Picker trigger pill
            Surface(
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true),
                        onClick = onDateClick
                    ),
                shape = RoundedCornerShape(19.dp),
                color = LuxuryCardSurface,
                border = BorderStroke(1.dp, GoldBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Select Date",
                        tint = GoldPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = displayDateText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimaryDark
                    )
                }
            }

            // Next day button
            Surface(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 20.dp),
                        onClick = onNextClick
                    ),
                shape = CircleShape,
                color = LuxuryCardSurface,
                border = BorderStroke(1.dp, GoldBorderSubdued)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next Day",
                        tint = GoldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Jump to Today button
        AnimatedVisibility(
            visible = isNotToday,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true),
                        onClick = onTodayClick
                    ),
                shape = RoundedCornerShape(17.dp),
                color = GoldPillBg,
                border = BorderStroke(1.dp, GoldBorder)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Today",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 1. Daily Task Graph (Selected Date)
// ---------------------------------------------------------------------------

@Composable
private fun DailyTaskGraphCard(metrics: DailyDateMetrics) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = LuxuryCardSurface,
        border = BorderStroke(1.dp, GoldBorderSubdued)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Daily Distribution",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = if (metrics.isFuture) "Scheduled occurrences" else "${metrics.completedCount} of ${metrics.completedCount + metrics.missedCount + metrics.pendingCount} completed",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark
                    )
                }

                // Completion percentage badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (metrics.completionRate == 100f && metrics.tasks.isNotEmpty()) {
                        StatusCompletedColor.copy(alpha = 0.2f)
                    } else {
                        GoldPillBg
                    },
                    border = BorderStroke(
                        1.dp,
                        if (metrics.completionRate == 100f && metrics.tasks.isNotEmpty()) StatusCompletedColor else GoldBorder
                    )
                ) {
                    Text(
                        text = if (metrics.isFuture) "Pending" else "${metrics.completionRate.toInt()}% Done",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (metrics.completionRate == 100f && metrics.tasks.isNotEmpty()) StatusCompletedColor else GoldPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            if (metrics.tasks.isEmpty()) {
                // Empty state for this specific date
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No daily tasks scheduled for this date",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondaryDark
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Only tasks configured with Daily recurrence appear in Insights",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryDark.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                // Horizontal Segmented Task Bar
                HorizontalTaskSegmentedBar(tasks = metrics.tasks)

                // Task Status Chart / List Rows
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    metrics.tasks.forEach { task ->
                        DailyTaskChartRow(task = task)
                    }
                }

                // 4-column Status Summary
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    DailyStatusCounter(
                        label = "Completed",
                        count = metrics.completedCount,
                        color = StatusCompletedColor
                    )
                    DailyStatusCounter(
                        label = "Pending",
                        count = metrics.pendingCount,
                        color = GoldPrimary
                    )
                    DailyStatusCounter(
                        label = "Missed",
                        count = metrics.missedCount,
                        color = StatusMissedColor
                    )
                    DailyStatusCounter(
                        label = "Skipped",
                        count = metrics.skippedCount,
                        color = StatusSkippedColor
                    )
                }
            }
        }
    }
}

/**
 * Horizontal Segmented Bar: Each task occurrence gets a proportional segment of the bar
 * rendered in its actual assigned color, reflecting its completion status.
 */
@Composable
private fun HorizontalTaskSegmentedBar(tasks: List<DailyTaskGraphItem>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0xFF232128)),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        tasks.forEach { task ->
            val taskColor = Color(task.color)
            val segmentAlpha = when (task.status) {
                OccurrenceStatus.COMPLETED -> 1.0f
                OccurrenceStatus.PENDING -> 0.35f
                OccurrenceStatus.MISSED -> 0.65f
                OccurrenceStatus.SKIPPED -> 0.20f
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (task.status == OccurrenceStatus.MISSED) {
                            StatusMissedColor.copy(alpha = 0.85f)
                        } else {
                            taskColor.copy(alpha = segmentAlpha)
                        }
                    )
            )
        }
    }
}

/**
 * Clean, readable row for each scheduled daily task on the selected date.
 * Displays the task's assigned color, title, scheduled time, and status badge.
 */
@Composable
private fun DailyTaskChartRow(task: DailyTaskGraphItem) {
    val taskColor = Color(task.color)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF16151B),
        border = BorderStroke(1.dp, GoldBorderSubdued.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                // Task color swatch
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(taskColor)
                )

                Column {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimaryDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatTime(task.scheduledTimeMinutes),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryDark
                    )
                }
            }

            // Status chip badge
            TaskStatusBadge(status = task.status)
        }
    }
}

@Composable
private fun TaskStatusBadge(status: OccurrenceStatus) {
    val (badgeText, badgeColor, iconVector) = when (status) {
        OccurrenceStatus.COMPLETED -> Triple("Done", StatusCompletedColor, Icons.Default.Check)
        OccurrenceStatus.PENDING -> Triple("Pending", GoldPrimary, Icons.Default.Notifications)
        OccurrenceStatus.MISSED -> Triple("Missed", StatusMissedColor, Icons.Default.Close)
        OccurrenceStatus.SKIPPED -> Triple("Skipped", StatusSkippedColor, Icons.AutoMirrored.Filled.KeyboardArrowRight)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = badgeColor.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = badgeText,
                tint = badgeColor,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = badgeColor
            )
        }
    }
}

@Composable
private fun DailyStatusCounter(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondaryDark,
            fontSize = 11.sp
        )
    }
}

// ---------------------------------------------------------------------------
// 2. Weekly Trend Card (Last 7 Days)
// ---------------------------------------------------------------------------

@Composable
private fun WeeklyTrendCard(
    weeklyTrend: List<DailyTrendDay>,
    selectedDate: String,
    onDayClick: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = LuxuryCardSurface,
        border = BorderStroke(1.dp, GoldBorderSubdued)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(
                    text = "Weekly Trend",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark
                )
                Text(
                    text = "Daily completion rate over the last 7 days",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryDark
                )
            }

            // 7 vertical bars
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                weeklyTrend.forEach { day ->
                    val isDaySelected = day.dateStr == selectedDate
                    val barHeightFraction = (day.completionRate / 100f).coerceIn(0f, 1f)
                    val animatedFraction by animateFloatAsState(
                        targetValue = barHeightFraction,
                        animationSpec = tween(600),
                        label = "barHeight"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onDayClick(day.dateStr) }
                            .padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        // Percentage text on top
                        Text(
                            text = if (day.totalCount > 0) "${day.completionRate.toInt()}%" else "—",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = if (isDaySelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isDaySelected) GoldPrimary else TextSecondaryDark.copy(alpha = 0.8f)
                        )

                        Spacer(Modifier.height(6.dp))

                        // Vertical Bar Container
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(72.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color(0xFF232128))
                                .then(
                                    if (isDaySelected) Modifier.border(1.5.dp, GoldPrimary, RoundedCornerShape(9.dp))
                                    else Modifier
                                ),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            val fillHeight = (72 * animatedFraction).coerceAtLeast(if (day.totalCount > 0 && day.completedCount > 0) 6f else 0f)

                            Box(
                                modifier = Modifier
                                    .width(18.dp)
                                    .height(fillHeight.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                GoldPrimary,
                                                GoldSecondary
                                            )
                                        )
                                    )
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // Day label & date number below
                        Text(
                            text = day.dayLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (day.isToday) GoldPrimary else TextSecondaryDark,
                            fontSize = 11.sp
                        )
                        Text(
                            text = day.dayNumber,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = if (isDaySelected) GoldPrimary else TextSecondaryDark.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 3. Monthly Trend Card
// ---------------------------------------------------------------------------

@Composable
private fun MonthlyTrendCard(monthly: MonthlyTrendSummary) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = LuxuryCardSurface,
        border = BorderStroke(1.dp, GoldBorderSubdued)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Monthly Consistency",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = monthly.monthLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark
                    )
                }

                // Month completion rate
                Text(
                    text = "${monthly.completionRate.toInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary
                )
            }

            // Summary text
            Text(
                text = "${monthly.totalCompleted} of ${monthly.totalEligible} eligible daily occurrences completed this month",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryDark
            )

            // 4 Weekly progression buckets
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                monthly.weeklyBuckets.forEach { bucket ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = bucket.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryDark,
                            modifier = Modifier.width(60.dp)
                        )

                        val barRatio = (bucket.rate / 100f).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF232128))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(barRatio)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (bucket.rate >= 80f) StatusCompletedColor else GoldPrimary
                                    )
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Text(
                            text = if (bucket.total > 0) "${bucket.rate.toInt()}%" else "—",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark,
                            modifier = Modifier.width(36.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 4. Performance Highlights: Consistent Performer & Needs Attention
// ---------------------------------------------------------------------------

@Composable
private fun PerformanceHighlightsRow(insights: DailyInsightsData) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Most Consistent Card
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp),
            color = LuxuryCardSurface,
            border = BorderStroke(1.dp, GoldBorderSubdued)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "CONSISTENT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = StatusCompletedColor,
                    letterSpacing = 0.5.sp
                )

                Text(
                    text = insights.mostConsistentTaskTitle ?: "Keep tracking",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (insights.mostConsistentTaskRate != null) {
                    Text(
                        text = "${insights.mostConsistentTaskRate.toInt()}% completion rate",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryDark
                    )
                } else {
                    Text(
                        text = "Complete daily tasks to unlock",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryDark.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // Needs Attention Card
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp),
            color = LuxuryCardSurface,
            border = BorderStroke(1.dp, GoldBorderSubdued)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "NEEDS ATTENTION",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (insights.mostFrequentMissedTaskTitle != null) StatusMissedColor else TextSecondaryDark,
                    letterSpacing = 0.5.sp
                )

                Text(
                    text = insights.mostFrequentMissedTaskTitle ?: "All caught up",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (insights.mostFrequentMissedCount != null) {
                    Text(
                        text = "Missed ${insights.mostFrequentMissedCount} times",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryDark
                    )
                } else {
                    Text(
                        text = "No frequently missed tasks",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryDark.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 5. Streaks & All-Time Daily Summary
// ---------------------------------------------------------------------------

@Composable
private fun DailyStreaksSummaryCard(insights: DailyInsightsData) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = LuxuryCardSurface,
        border = BorderStroke(1.dp, GoldBorderSubdued)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(
                    text = "Daily Streaks & Consistency",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark
                )
                Text(
                    text = "Historical streaks across your daily tasks",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryDark
                )
            }

            // Streak overview 3-pill row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StreakMetricPill(
                    icon = "🔥",
                    value = "${insights.bestCurrentStreak}d",
                    label = "Active Streak",
                    detail = insights.bestCurrentStreakTaskTitle ?: "None active"
                )
                StreakMetricPill(
                    icon = "🏆",
                    value = "${insights.longestStreakAllTime}d",
                    label = "Longest Ever",
                    detail = insights.longestStreakTaskTitle ?: "None"
                )
                StreakMetricPill(
                    icon = "✓",
                    value = "${insights.totalCompletedAllTime}",
                    label = "All-Time Done",
                    detail = "daily occurrences"
                )
            }

            // List of active daily tasks summary
            if (insights.dailyStreaks.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    insights.dailyStreaks.forEach { streak ->
                        DailyTaskStreakRow(streak = streak)
                    }
                }
            }
        }
    }
}

@Composable
private fun StreakMetricPill(
    icon: String,
    value: String,
    label: String,
    detail: String
) {
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier.width(96.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = icon, fontSize = 12.sp)
            Spacer(Modifier.width(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = GoldPrimary
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimaryDark,
            fontSize = 11.sp
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondaryDark,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DailyTaskStreakRow(streak: DailyTaskStreakSummary) {
    val taskColor = Color(streak.color)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF16151B),
        border = BorderStroke(1.dp, GoldBorderSubdued.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(taskColor)
                )
                Text(
                    text = streak.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimaryDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "🔥 ${streak.currentStreak}d",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (streak.currentStreak > 0) GoldPrimary else TextSecondaryDark
                )
                Text(
                    text = "Best: ${streak.longestStreak}d",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryDark
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Empty State
// ---------------------------------------------------------------------------

@Composable
private fun NoDailyTasksEmptyState() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        shape = RoundedCornerShape(20.dp),
        color = LuxuryCardSurface,
        border = BorderStroke(1.dp, GoldBorderSubdued)
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(GoldPillBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = GoldPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "No Daily Tasks Configured",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryDark
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Insights exclusively tracks daily recurring tasks. Create a task with the 'Every day / Daily' recurrence to see your performance and daily distribution graphs.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryDark,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Date Converters
// ---------------------------------------------------------------------------

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
