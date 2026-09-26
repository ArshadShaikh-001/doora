package com.example.doora.ui.addtask

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
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
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
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

            // ---- Title ----
            OutlinedTextField(
                value = state.title,
                onValueChange = vm::updateTitle,
                label = { Text("Task title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = state.error?.contains("Title", ignoreCase = true) == true
            )

            // ---- Date field (tap to open picker) ----
            ClickableTextField(
                value = formatDisplayDate(state.date),
                label = "Date",
                icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                onClick = { showDatePicker = true }
            )

            // ---- Time field (tap to open picker) ----
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

            // ---- Recurrence (add mode only) ----
            if (!state.isEditMode) {
                Column {
                    Text("Repeat", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.recurrenceType == RecurrenceType.ONE_TIME,
                            onClick = { vm.updateRecurrence(RecurrenceType.ONE_TIME) },
                            label = { Text("One-time") }
                        )
                        FilterChip(
                            selected = state.recurrenceType == RecurrenceType.DAILY,
                            onClick = { vm.updateRecurrence(RecurrenceType.DAILY) },
                            label = { Text("Daily") }
                        )
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
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (state.isEditMode) "Save Changes" else "Add Task")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * A read-only OutlinedTextField that looks normal but fires [onClick] on tap.
 * An invisible overlay captures the click so the soft keyboard never opens.
 */
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
        // Transparent overlay to capture the tap (TextField absorbs touch by default)
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

// ---------------------------------------------------------------------------
// Date helpers (UTC-based to match DatePicker's internal epoch representation)
// ---------------------------------------------------------------------------

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
