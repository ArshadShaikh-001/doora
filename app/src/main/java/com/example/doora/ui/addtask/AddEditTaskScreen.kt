package com.example.doora.ui.addtask

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.doora.ui.util.UiFeedbackHelper
import com.example.doora.data.model.Priority
import com.example.doora.data.model.RecurrenceType
import com.example.doora.data.repository.TaskRepository
import com.example.doora.ui.components.ColorPicker
import com.example.doora.ui.components.formatTime
import com.example.doora.ui.theme.PriorityHighBg
import com.example.doora.ui.theme.PriorityHighColor
import com.example.doora.ui.theme.PriorityLowBg
import com.example.doora.ui.theme.PriorityLowColor
import com.example.doora.ui.theme.PriorityMediumBg
import com.example.doora.ui.theme.PriorityMediumColor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditTaskScreen(
    repository: TaskRepository,
    occurrenceId: Long?,
    onBack: () -> Unit
) {
    val vm: AddEditTaskViewModel = viewModel(
        key = occurrenceId?.toString() ?: "add",
        factory = AddEditTaskViewModel.Factory(repository, occurrenceId)
    )
    val state by vm.state.collectAsState()

    val context = LocalContext.current

    // Navigate back on successful save
    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            if (!state.isEditMode) {
                UiFeedbackHelper.playTaskCreatedFeedback(context)
            }
            onBack()
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showSpecificDatePicker by remember { mutableStateOf(false) }

    // Date Picker dialog
    if (showDatePicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = dateStringToUtcMillis(state.date)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { vm.updateDate(utcMillisToDateString(it)) }
                    showDatePicker = false
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = dpState)
        }
    }

    // Specific Date Picker dialog
    if (showSpecificDatePicker) {
        val dpState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showSpecificDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { vm.addSpecificDate(utcMillisToDateString(it)) }
                    showSpecificDatePicker = false
                }) { Text("Add Date") }
            },
            dismissButton = {
                TextButton(onClick = { showSpecificDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = dpState)
        }
    }

    // Time Picker dialog
    if (showTimePicker) {
        val tpState = rememberTimePickerState(
            initialHour = state.timeMinutes / 60,
            initialMinute = state.timeMinutes % 60,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Scheduled Time", fontWeight = FontWeight.SemiBold) },
            text = { TimePicker(state = tpState) },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateTime(tpState.hour * 60 + tpState.minute)
                    showTimePicker = false
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.isEditMode) "Edit Task" else "New Task",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // Recurring Edit Choice ("Only today" vs "Repeat")
            if (state.isEditMode && state.isRecurringTask) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Apply Changes To:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilterChip(
                                selected = state.editScope == EditScope.ONLY_TODAY,
                                onClick = { vm.updateEditScope(EditScope.ONLY_TODAY) },
                                label = { Text("Only today's task") },
                                shape = RoundedCornerShape(10.dp)
                            )
                            FilterChip(
                                selected = state.editScope == EditScope.REPEAT,
                                onClick = { vm.updateEditScope(EditScope.REPEAT) },
                                label = { Text("Entire series") },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            }

            // Task Name
            Column {
                Text(
                    text = "TASK NAME",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.title,
                    onValueChange = vm::updateTitle,
                    placeholder = { Text("What needs to be done?", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    isError = state.error?.contains("Title", ignoreCase = true) == true
                )
            }

            // Schedule Section (Date & Time)
            Column {
                Text(
                    text = "SCHEDULE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!state.isEditMode || !state.isRecurringTask) {
                        SchedulePill(
                            label = formatDisplayDate(state.date).ifBlank { "Select Date" },
                            onClick = { showDatePicker = true },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    SchedulePill(
                        label = formatTime(state.timeMinutes),
                        onClick = { showTimePicker = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Priority Section
            Column {
                Text(
                    text = "PRIORITY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Priority.entries.forEach { p ->
                        val isSelected = state.priority == p
                        val (pColor, pBg) = when (p) {
                            Priority.HIGH -> PriorityHighColor to PriorityHighBg
                            Priority.MEDIUM -> PriorityMediumColor to PriorityMediumBg
                            Priority.LOW -> PriorityLowColor to PriorityLowBg
                        }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) pBg else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) pColor else MaterialTheme.colorScheme.outlineVariant
                            ),
                            onClick = { vm.updatePriority(p) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = p.name.lowercase().replaceFirstChar { it.uppercaseChar() },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) pColor else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Recurrence Section
            if (!state.isEditMode || (state.isRecurringTask && state.editScope == EditScope.REPEAT)) {
                Column {
                    Text(
                        text = "REPEAT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val recurrenceLabels = listOf(
                            RecurrenceType.ONE_TIME to "One-time",
                            RecurrenceType.DAILY to "Every day",
                            RecurrenceType.WEEKDAYS to "Mon–Fri",
                            RecurrenceType.SPECIFIC_WEEKDAYS to "Specific days",
                            RecurrenceType.WEEKLY to "Weekly",
                            RecurrenceType.MONTHLY to "Monthly",
                            RecurrenceType.SPECIFIC_DATES to "Specific dates"
                        )
                        recurrenceLabels.forEach { (type, label) ->
                            FilterChip(
                                selected = state.recurrenceType == type,
                                onClick = { vm.updateRecurrence(type) },
                                label = { Text(label, fontSize = 13.sp) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }

                    // Weekdays selection
                    AnimatedVisibility(
                        visible = state.recurrenceType == RecurrenceType.SPECIFIC_WEEKDAYS,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Text("Active Days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            val days = listOf(
                                Calendar.MONDAY to "M",
                                Calendar.TUESDAY to "T",
                                Calendar.WEDNESDAY to "W",
                                Calendar.THURSDAY to "T",
                                Calendar.FRIDAY to "F",
                                Calendar.SATURDAY to "S",
                                Calendar.SUNDAY to "S"
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                days.forEach { (calDay, label) ->
                                    val isDaySelected = calDay in state.selectedWeekdays
                                    Surface(
                                        modifier = Modifier.size(38.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isDaySelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isDaySelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        onClick = { vm.toggleWeekday(calDay) }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = label,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isDaySelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Monthly day of month
                    AnimatedVisibility(
                        visible = state.recurrenceType == RecurrenceType.MONTHLY,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            OutlinedTextField(
                                value = state.dayOfMonth.toString(),
                                onValueChange = { str ->
                                    str.toIntOrNull()?.let { vm.updateDayOfMonth(it) }
                                },
                                label = { Text("Day of Month (1–31)") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Specific Dates list
                    AnimatedVisibility(
                        visible = state.recurrenceType == RecurrenceType.SPECIFIC_DATES,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Selected (${state.specificDates.size})", style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { showSpecificDatePicker = true }) {
                                    Text("+ Add Date")
                                }
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                state.specificDates.forEach { d ->
                                    FilterChip(
                                        selected = true,
                                        onClick = { vm.removeSpecificDate(d) },
                                        label = { Text("$d ✕") },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Color Palette
            Column {
                Text(
                    text = "TASK COLOR",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(10.dp))
                ColorPicker(
                    selectedColor = state.color,
                    usedColors = state.usedColors,
                    onColorSelected = vm::updateColor,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Error display
            if (state.error != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.error!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Primary Action Button
            Button(
                onClick = vm::save,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = if (state.isEditMode) {
                            if (state.isRecurringTask && state.editScope == EditScope.ONLY_TODAY) "Save for Today"
                            else "Save Changes"
                        } else "Create Task",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SchedulePill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(52.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
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
        SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(parsed)
    } catch (_: Exception) {
        dateStr
    }
}
