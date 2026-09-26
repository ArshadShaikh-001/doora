package com.example.doora.ui.addtask

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.doora.data.model.Priority
import com.example.doora.data.model.RecurrenceType
import com.example.doora.data.repository.TaskRepository
import com.example.doora.ui.components.ColorPicker
import com.example.doora.ui.components.formatTime
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

    // Navigate back on successful save
    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) onBack()
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showSpecificDatePicker by remember { mutableStateOf(false) }

    // ---- Date Picker dialog ----
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
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = dpState)
        }
    }

    // Specific Date Picker dialog for SPECIFIC_DATES
    if (showSpecificDatePicker) {
        val dpState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showSpecificDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { vm.addSpecificDate(utcMillisToDateString(it)) }
                    showSpecificDatePicker = false
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showSpecificDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = dpState)
        }
    }

    // ---- Time Picker dialog ----
    if (showTimePicker) {
        val tpState = rememberTimePickerState(
            initialHour = state.timeMinutes / 60,
            initialMinute = state.timeMinutes % 60,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select time") },
            text = { TimePicker(state = tpState) },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateTime(tpState.hour * 60 + tpState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditMode) "Edit Task" else "Add Task") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ---- Recurring Edit Scope Choice ("Only today" vs "Repeat") ----
            if (state.isEditMode && state.isRecurringTask) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Apply Changes To:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.editScope == EditScope.ONLY_TODAY,
                                onClick = { vm.updateEditScope(EditScope.ONLY_TODAY) },
                                label = { Text("Only today") }
                            )
                            FilterChip(
                                selected = state.editScope == EditScope.REPEAT,
                                onClick = { vm.updateEditScope(EditScope.REPEAT) },
                                label = { Text("Repeat") }
                            )
                        }
                    }
                }
            }

            // ---- Title ----
            OutlinedTextField(
                value = state.title,
                onValueChange = vm::updateTitle,
                label = { Text("Task title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = state.error?.contains("Title", ignoreCase = true) == true
            )

            // ---- Date (Only shown in Add mode or when editing One-time) ----
            if (!state.isEditMode || (!state.isRecurringTask)) {
                ClickableTextField(
                    value = formatDisplayDate(state.date),
                    label = "Date",
                    icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    onClick = { showDatePicker = true }
                )
            }

            // ---- Time ----
            ClickableTextField(
                value = formatTime(state.timeMinutes),
                label = "Time",
                icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                onClick = { showTimePicker = true }
            )

            // ---- Priority ----
            Column {
                Text("Priority", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Priority.entries.forEach { p ->
                        FilterChip(
                            selected = state.priority == p,
                            onClick = { vm.updatePriority(p) },
                            label = {
                                Text(p.name.lowercase().replaceFirstChar { it.uppercaseChar() })
                            }
                        )
                    }
                }
            }

            // ---- Recurrence Options ----
            if (!state.isEditMode || (state.isRecurringTask && state.editScope == EditScope.REPEAT)) {
                Column {
                    Text("Repeat", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.recurrenceType == RecurrenceType.ONE_TIME,
                            onClick = { vm.updateRecurrence(RecurrenceType.ONE_TIME) },
                            label = { Text("One-time") }
                        )
                        FilterChip(
                            selected = state.recurrenceType == RecurrenceType.DAILY,
                            onClick = { vm.updateRecurrence(RecurrenceType.DAILY) },
                            label = { Text("Every day") }
                        )
                        FilterChip(
                            selected = state.recurrenceType == RecurrenceType.WEEKDAYS,
                            onClick = { vm.updateRecurrence(RecurrenceType.WEEKDAYS) },
                            label = { Text("Mon–Fri") }
                        )
                        FilterChip(
                            selected = state.recurrenceType == RecurrenceType.SPECIFIC_WEEKDAYS,
                            onClick = { vm.updateRecurrence(RecurrenceType.SPECIFIC_WEEKDAYS) },
                            label = { Text("Specific days") }
                        )
                        FilterChip(
                            selected = state.recurrenceType == RecurrenceType.WEEKLY,
                            onClick = { vm.updateRecurrence(RecurrenceType.WEEKLY) },
                            label = { Text("Weekly") }
                        )
                        FilterChip(
                            selected = state.recurrenceType == RecurrenceType.MONTHLY,
                            onClick = { vm.updateRecurrence(RecurrenceType.MONTHLY) },
                            label = { Text("Monthly") }
                        )
                        FilterChip(
                            selected = state.recurrenceType == RecurrenceType.SPECIFIC_DATES,
                            onClick = { vm.updateRecurrence(RecurrenceType.SPECIFIC_DATES) },
                            label = { Text("Specific dates") }
                        )
                    }

                    // Extra controls for SPECIFIC_WEEKDAYS
                    if (state.recurrenceType == RecurrenceType.SPECIFIC_WEEKDAYS) {
                        Spacer(Modifier.height(12.dp))
                        Text("Select Days:", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        val days = listOf(
                            Calendar.MONDAY to "Mon",
                            Calendar.TUESDAY to "Tue",
                            Calendar.WEDNESDAY to "Wed",
                            Calendar.THURSDAY to "Thu",
                            Calendar.FRIDAY to "Fri",
                            Calendar.SATURDAY to "Sat",
                            Calendar.SUNDAY to "Sun"
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            days.forEach { (calDay, label) ->
                                FilterChip(
                                    selected = calDay in state.selectedWeekdays,
                                    onClick = { vm.toggleWeekday(calDay) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    // Extra controls for MONTHLY
                    if (state.recurrenceType == RecurrenceType.MONTHLY) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.dayOfMonth.toString(),
                            onValueChange = { str ->
                                str.toIntOrNull()?.let { vm.updateDayOfMonth(it) }
                            },
                            label = { Text("Day of Month (1–31)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Extra controls for SPECIFIC_DATES
                    if (state.recurrenceType == RecurrenceType.SPECIFIC_DATES) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Dates (${state.specificDates.size}):", style = MaterialTheme.typography.bodySmall)
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
                                    label = { Text("$d ✕") }
                                )
                            }
                        }
                    }
                }
            }

            // ---- Colour ----
            Column {
                Text("Color", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                ColorPicker(
                    selectedColor = state.color,
                    usedColors = state.usedColors,
                    onColorSelected = vm::updateColor,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ---- Error ----
            if (state.error != null) {
                Text(
                    text = state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // ---- Save button ----
            Button(
                onClick = vm::save,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = if (state.isEditMode) {
                            if (state.isRecurringTask && state.editScope == EditScope.ONLY_TODAY) "Save for Today"
                            else "Save Changes"
                        } else "Add Task",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ClickableTextField(
    value: String,
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            trailingIcon = icon
        )
        Spacer(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
        )
    }
}

private fun dateStringToUtcMillis(dateStr: String): Long {
    return try {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).also {
            it.timeZone = TimeZone.getTimeZone("UTC")
        }.parse(dateStr)?.time ?: System.currentTimeMillis()
    } catch (e: Exception) {
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
        SimpleDateFormat("EEE, MMM d yyyy", Locale.getDefault()).format(parsed)
    } catch (e: Exception) {
        dateStr
    }
}
