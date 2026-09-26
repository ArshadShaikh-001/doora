package com.example.doora.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.doora.data.db.entity.TaskOccurrence
import com.example.doora.data.model.OccurrenceStatus
import com.example.doora.data.model.RecurrenceType
import com.example.doora.data.repository.TaskRepository
import com.example.doora.ui.components.TaskCard
import com.example.doora.ui.theme.GoldBorder
import com.example.doora.ui.theme.GoldBorderSubdued
import com.example.doora.ui.theme.GoldPrimary
import com.example.doora.ui.theme.GoldSecondary
import com.example.doora.ui.theme.LuxuryBackground
import com.example.doora.ui.theme.LuxuryCardSurface
import com.example.doora.ui.theme.TextCompletedDark
import com.example.doora.ui.theme.TextLuxuryGreeting
import com.example.doora.ui.theme.TextPrimaryDark
import com.example.doora.ui.theme.TextSecondaryDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CurrentTimeSnapshot(
    val dateStr: String,
    val minutesSinceMidnight: Int
)

@Composable
fun rememberCurrentTimeSnapshot(): CurrentTimeSnapshot {
    var snapshot by remember {
        val cal = Calendar.getInstance()
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        val m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        mutableStateOf(CurrentTimeSnapshot(d, m))
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            delay(5_000L)
            val cal = Calendar.getInstance()
            val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            val m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            if (snapshot.dateStr != d || snapshot.minutesSinceMidnight != m) {
                snapshot = CurrentTimeSnapshot(d, m)
            }
        }
    }
    return snapshot
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: TaskRepository,
    onAddTask: () -> Unit,
    onEditTask: (Long) -> Unit
) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(repository))
    val tasks by vm.todayTasks.collectAsState()
    val templateMap by vm.templateMap.collectAsState()
    val timeSnapshot = rememberCurrentTimeSnapshot()
    val scope = rememberCoroutineScope()

    var taskPendingDelete by remember { mutableStateOf<TaskOccurrence?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Contextual luxury greeting
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Good night"
        }
    }

    val dateFormatted = remember {
        SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
    }

    // Modern Bottom Sheet for Deleting Tasks
    if (taskPendingDelete != null) {
        val target = taskPendingDelete!!
        ModalBottomSheet(
            onDismissRequest = { taskPendingDelete = null },
            sheetState = sheetState,
            containerColor = LuxuryCardSurface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Delete Recurring Task",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark
                )
                Text(
                    text = "Choose whether to remove only today's scheduled task or cancel the entire recurring series.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryDark
                )

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = {
                        vm.deleteOccurrenceOnly(target)
                        taskPendingDelete = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = Color(0xFF14120E)
                    )
                ) {
                    Text("Delete only today", fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = {
                        vm.deleteRecurring(target)
                        taskPendingDelete = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Text("Delete series", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    Scaffold(
        containerColor = LuxuryBackground,
        floatingActionButton = {
            // Floating Luxury New Task Button matching reference screenshot
            Box(
                modifier = Modifier
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = Color(0x33DFC083),
                        spotColor = Color(0x66DFC083)
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFFDFC083),
                                Color(0xFFF3E2BD),
                                Color(0xFFBF9F63)
                            )
                        )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = Color(0xFF14120E)),
                        onClick = onAddTask
                    )
                    .padding(horizontal = 22.dp, vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color(0xFF14120E),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "New Task",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = Color(0xFF14120E)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Top Header: Elegant serif display greeting & formatted date
            HomeHeader(
                greeting = greeting,
                dateString = dateFormatted
            )

            val incompleteTasks = remember(tasks) {
                tasks.filter { it.status != OccurrenceStatus.COMPLETED }
            }
            val completedTasks = remember(tasks) {
                tasks.filter { it.status == OccurrenceStatus.COMPLETED }
            }

            if (tasks.isEmpty()) {
                HomeEmptyState(onAddTask = onAddTask)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Group 1: Incomplete / Active section header
                    if (incompleteTasks.isNotEmpty()) {
                        item(key = "active_section_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 2.dp)
                                    .animateItem(
                                        fadeInSpec = tween(250),
                                        fadeOutSpec = tween(200),
                                        placementSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondaryDark.copy(alpha = 0.85f),
                                    letterSpacing = 1.6.sp,
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "(${incompleteTasks.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondaryDark.copy(alpha = 0.65f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Incomplete Tasks
                    items(
                        items = incompleteTasks,
                        key = { it.id }
                    ) { occurrence ->
                        val template = templateMap[occurrence.templateId]
                        val isRecurring = template != null && template.recurrenceType != RecurrenceType.ONE_TIME
                        val isOverdue = occurrence.status == OccurrenceStatus.PENDING && (
                            occurrence.scheduledDate < timeSnapshot.dateStr ||
                            (occurrence.scheduledDate == timeSnapshot.dateStr && timeSnapshot.minutesSinceMidnight >= occurrence.scheduledTimeMinutes)
                        )
                        TaskCard(
                            occurrence = occurrence,
                            isRecurring = isRecurring,
                            isOverdue = isOverdue,
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(durationMillis = 300),
                                fadeOutSpec = tween(durationMillis = 200),
                                placementSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ),
                            onComplete = { vm.completeTask(occurrence) },
                            onSkip = { vm.skipTask(occurrence) },
                            onEdit = { onEditTask(occurrence.id) },
                            onDelete = {
                                scope.launch {
                                    if (isRecurring || vm.isRecurring(occurrence.templateId)) {
                                        taskPendingDelete = occurrence
                                    } else {
                                        vm.deleteOccurrenceOnly(occurrence)
                                    }
                                }
                            }
                        )
                    }

                    // Group 2: Completed tasks section
                    if (completedTasks.isNotEmpty()) {
                        item(key = "completed_section_separator") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = if (incompleteTasks.isEmpty()) 4.dp else 16.dp, bottom = 2.dp)
                                    .animateItem(
                                        fadeInSpec = tween(250),
                                        fadeOutSpec = tween(200),
                                        placementSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "COMPLETED",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondaryDark.copy(alpha = 0.75f),
                                    letterSpacing = 1.6.sp,
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "(${completedTasks.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondaryDark.copy(alpha = 0.55f),
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.width(12.dp))
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    thickness = 0.8.dp,
                                    color = GoldBorderSubdued
                                )
                            }
                        }

                        items(
                            items = completedTasks,
                            key = { it.id }
                        ) { occurrence ->
                            val template = templateMap[occurrence.templateId]
                            val isRecurring = template != null && template.recurrenceType != RecurrenceType.ONE_TIME
                            TaskCard(
                                occurrence = occurrence,
                                isRecurring = isRecurring,
                                isOverdue = false,
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(durationMillis = 300),
                                    fadeOutSpec = tween(durationMillis = 200),
                                    placementSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ),
                                onComplete = {},
                                onSkip = { vm.skipTask(occurrence) },
                                onEdit = { onEditTask(occurrence.id) },
                                onDelete = {
                                    scope.launch {
                                        if (isRecurring || vm.isRecurring(occurrence.templateId)) {
                                            taskPendingDelete = occurrence
                                        } else {
                                            vm.deleteOccurrenceOnly(occurrence)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    greeting: String,
    dateString: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 16.dp)
    ) {
        Text(
            text = greeting,
            style = TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = 34.sp,
                fontWeight = FontWeight.Normal,
                color = TextLuxuryGreeting,
                letterSpacing = 0.2.sp
            )
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = dateString,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 15.sp,
            color = TextSecondaryDark
        )
    }
}

@Composable
private fun HomeEmptyState(onAddTask: () -> Unit) {
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
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0x18DFC083)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = GoldPrimary,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "All clear for today",
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextLuxuryGreeting
                )
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Your day is free. Take a breath or schedule something new.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryDark,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            OutlinedButton(
                onClick = onAddTask,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, GoldBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = GoldPrimary
                )
            ) {
                Text("Schedule a Task", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
