package com.example.doora.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.doora.data.db.entity.TaskOccurrence
import com.example.doora.data.model.OccurrenceStatus
import com.example.doora.data.repository.TaskRepository
import com.example.doora.ui.components.TaskCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    repository: TaskRepository,
    onEditTask: (Long) -> Unit
) {
    val vm: CalendarViewModel = viewModel(factory = CalendarViewModel.Factory(repository))
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Month navigation header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = vm::prevMonth) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month")
                }
                Text(
                    text = state.monthTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = vm::nextMonth) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month")
                }
            }

            // Days of Week Header (Mon .. Sun)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                    Text(
                        text = day,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Calendar Month Grid (6 rows of 7 days)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                val weeks = state.daysInMonth.chunked(7)
                weeks.forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        week.forEach { day ->
                            CalendarDayCell(
                                day = day,
                                isSelected = day.dateStr == state.selectedDate,
                                onClick = { vm.selectDate(day.dateStr) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Selected date header & tasks
            Text(
                text = "Tasks for ${state.selectedDate}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            if (state.selectedDateTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tasks on this date",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.selectedDateTasks, key = { it.id }) { occ ->
                        TaskCard(
                            occurrence = occ,
                            onComplete = { vm.completeTask(occ) },
                            onSkip = { vm.skipTask(occ) },
                            onEdit = { onEditTask(occ.id) },
                            onDelete = {}
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: CalendarDay,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cellBg = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else Color.Transparent

    val textColor = when {
        !day.isCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        day.isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(cellBg)
            .then(
                if (day.isToday) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                else if (isSelected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = day.dayNumber.toString(),
            fontSize = 12.sp,
            fontWeight = if (day.isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )

        Spacer(Modifier.height(2.dp))

        // Horizontal colored task segments
        // Requirement 6:
        // COMPLETED -> colored segment
        // PENDING/MISSED -> grey segment
        // SKIPPED -> visually distinct subtle segment
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(1.5.dp)
        ) {
            val displayTasks = day.tasks.take(3)
            displayTasks.forEach { task ->
                TaskSegmentBar(occurrence = task)
            }
            if (day.tasks.size > 3) {
                Text(
                    text = "+${day.tasks.size - 3}",
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 8.sp
                )
            }
        }
    }
}

@Composable
private fun TaskSegmentBar(occurrence: TaskOccurrence) {
    val taskColor = Color(occurrence.color)
    when (occurrence.status) {
        OccurrenceStatus.COMPLETED -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(taskColor)
            )
        }
        OccurrenceStatus.PENDING, OccurrenceStatus.MISSED -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(Color(0xFF9E9E9E)) // Grey segment
            )
        }
        OccurrenceStatus.SKIPPED -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(taskColor.copy(alpha = 0.25f))
                    .border(0.5.dp, taskColor.copy(alpha = 0.6f), RoundedCornerShape(1.5.dp))
            )
        }
    }
}
