package com.example.doora.ui.streaks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.doora.R
import com.example.doora.data.engine.DailyTaskStreak
import com.example.doora.data.engine.DayStreakStatus
import com.example.doora.data.model.OccurrenceStatus
import com.example.doora.data.repository.TaskRepository
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun StreaksScreen(
    repository: TaskRepository,
    onNavigateToAddTask: () -> Unit = {}
) {
    val vm: StreaksViewModel = viewModel(factory = StreaksViewModel.Factory(repository))
    val state by vm.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )
            }
        } else if (state.dailyStreaks.isEmpty()) {
            StreaksEmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onAddTask = onNavigateToAddTask
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Section A: Header
                item {
                    StreaksHeader(activeDailyCount = state.dailyStreaks.size)
                }

                // Section B: Current Streaks
                item {
                    SectionTitle(
                        title = "CURRENT STREAKS",
                        subtitle = "Active daily consistency"
                    )
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.dailyStreaks.forEach { streak ->
                            val isSelected = streak.templateId == state.selectedTaskTemplateId
                            CurrentStreakCard(
                                streak = streak,
                                isSelected = isSelected,
                                onClick = { vm.selectTask(streak.templateId) }
                            )
                        }
                    }
                }

                // Section C: Personal Records / Longest Streak
                item {
                    SectionTitle(
                        title = "PERSONAL RECORDS",
                        subtitle = "All-time longest streaks"
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.dailyStreaks.sortedByDescending { it.longestStreak }) { streak ->
                            PersonalRecordCard(streak = streak)
                        }
                    }
                }

                // Section D: Streak History
                state.selectedTaskStreak?.let { selected ->
                    item {
                        SectionTitle(
                            title = "STREAK HISTORY",
                            subtitle = "Day-by-day consistency"
                        )
                        Spacer(Modifier.height(10.dp))

                        // Task Selector Chips
                        if (state.dailyStreaks.size > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                state.dailyStreaks.forEach { streak ->
                                    val isSelected = streak.templateId == state.selectedTaskTemplateId
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { vm.selectTask(streak.templateId) },
                                        label = {
                                            Text(
                                                text = streak.title,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(streak.color))
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }

                        // Selected Task History Card
                        TaskHistoryCard(streak = selected)
                    }
                }

                item {
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun StreaksHeader(activeDailyCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Your Streaks",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "Keep showing up.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Habit count pill
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_fire),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color(0xFFF79009)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "$activeDailyCount Daily",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.2.sp
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun CurrentStreakCard(
    streak: DailyTaskStreak,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(200),
        label = "border"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Task color bar + Title & Subtitle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(streak.color))
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = streak.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Best: ${streak.longestStreak} ${if (streak.longestStreak == 1) "day" else "days"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (streak.isCompletedToday) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Done today",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF12B76A)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // Right: Flame badge with current streak count
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (streak.currentStreak > 0) Color(0x1DF79009) else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                border = BorderStroke(
                    1.dp,
                    if (streak.currentStreak > 0) Color(0x3DF79009) else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔥",
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${streak.currentStreak} ${if (streak.currentStreak == 1) "day" else "days"}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (streak.currentStreak > 0) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonalRecordCard(streak: DailyTaskStreak) {
    Card(
        modifier = Modifier
            .width(170.dp)
            .height(120.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🏆",
                    fontSize = 18.sp
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(streak.color))
                )
            }

            Column {
                Text(
                    text = streak.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Best: ${streak.longestStreak} ${if (streak.longestStreak == 1) "day" else "days"}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun TaskHistoryCard(streak: DailyTaskStreak) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Task info header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(streak.color))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = streak.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                val formattedStart = remember(streak.streakStartDate) {
                    val parsed = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(streak.streakStartDate) }.getOrNull()
                    if (parsed != null) {
                        SimpleDateFormat("MMM d, yyyy", Locale.US).format(parsed)
                    } else streak.streakStartDate
                }
                Text(
                    text = "Started $formattedStart",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))

            // Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn(
                    label = "Current",
                    value = "🔥 ${streak.currentStreak}d"
                )
                MetricColumn(
                    label = "Best",
                    value = "🏆 ${streak.longestStreak}d"
                )
                MetricColumn(
                    label = "Completed",
                    value = "✓ ${streak.totalCompleted}"
                )
                val rate = if (streak.totalDaysEligible > 0) {
                    (streak.totalCompleted.toFloat() / streak.totalDaysEligible * 100f).toInt()
                } else 0
                MetricColumn(
                    label = "Rate",
                    value = "$rate%"
                )
            }

            Spacer(Modifier.height(20.dp))

            // Recent Days Timeline
            Text(
                text = "Recent Consistency (Past 14 Days)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            if (streak.recentDays.isEmpty()) {
                Text(
                    text = "No history recorded yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    streak.recentDays.forEach { day ->
                        DayHistoryItem(day = day)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem(color = Color(0xFF12B76A), label = "Done")
                LegendItem(color = Color(0xFFF79009), label = "Skipped")
                LegendItem(color = Color(0xFFF04438), label = "Missed")
                LegendItem(color = MaterialTheme.colorScheme.outlineVariant, label = "Pending")
            }
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DayHistoryItem(day: DayStreakStatus) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(34.dp)
    ) {
        Text(
            text = day.dayOfWeek.take(1),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${day.dayOfMonth}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(6.dp))

        // Status indicator dot / icon
        when (day.status) {
            OccurrenceStatus.COMPLETED -> {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF12B76A)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            OccurrenceStatus.SKIPPED -> {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0x33F79009)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "–",
                        color = Color(0xFFD97706),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
            OccurrenceStatus.MISSED -> {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0x26F04438)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF04438))
                    )
                }
            }
            OccurrenceStatus.PENDING, null -> {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun StreaksEmptyState(
    modifier: Modifier = Modifier,
    onAddTask: () -> Unit
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 36.dp)
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = Color(0x1DF79009),
                border = BorderStroke(1.dp, Color(0x3DF79009))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "🔥", fontSize = 32.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "No Daily Tasks Yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Streaks track your consistency on daily recurring tasks. Create a task with 'Every Day' recurrence to start building your streak.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onAddTask,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Create Daily Task", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
