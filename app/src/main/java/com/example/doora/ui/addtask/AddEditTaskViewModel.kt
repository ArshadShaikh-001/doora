package com.example.doora.ui.addtask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.doora.data.db.entity.TaskOccurrence
import com.example.doora.data.db.entity.TaskTemplate
import com.example.doora.data.model.Priority
import com.example.doora.data.model.RecurrenceType
import com.example.doora.data.repository.TaskRepository
import com.example.doora.ui.components.TASK_COLOR_PALETTE
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class EditScope {
    ONLY_TODAY,
    REPEAT
}

data class AddEditTaskUiState(
    val title: String = "",
    val date: String = "",          // "yyyy-MM-dd"
    val timeMinutes: Int = 9 * 60, // Default 9:00 AM
    val priority: Priority = Priority.MEDIUM,
    val color: Int = TASK_COLOR_PALETTE.first(),
    val recurrenceType: RecurrenceType = RecurrenceType.ONE_TIME,
    val selectedWeekdays: Set<Int> = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY),
    val dayOfMonth: Int = 1,
    val specificDates: List<String> = emptyList(),
    val usedColors: List<Int> = emptyList(),
    val isEditMode: Boolean = false,
    val isRecurringTask: Boolean = false,
    val editScope: EditScope = EditScope.ONLY_TODAY,
    val originalOccurrence: TaskOccurrence? = null,
    val originalTemplate: TaskTemplate? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val saveSuccess: Boolean = false
)

class AddEditTaskViewModel(
    private val repository: TaskRepository,
    private val occurrenceId: Long?
) : ViewModel() {

    private val _state = MutableStateFlow(AddEditTaskUiState())
    val state: StateFlow<AddEditTaskUiState> = _state.asStateFlow()

    init {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        viewModelScope.launch {
            if (occurrenceId != null) {
                // Edit mode — load existing occurrence & template
                val occurrence = repository.getOccurrenceById(occurrenceId)
                if (occurrence != null) {
                    val template = repository.getTemplateById(occurrence.templateId)
                    val isRecurring = template != null && template.recurrenceType != RecurrenceType.ONE_TIME
                    val usedColors = repository.getUsedColors(excludeTemplateId = occurrence.templateId)

                    val weekdaysSet = template?.weekdays
                        ?.split(",")
                        ?.mapNotNull { it.trim().toIntOrNull() }
                        ?.toSet()
                        ?: setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)

                    val specificDatesList = template?.specificDates
                        ?.split(",")
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() }
                        ?: emptyList()

                    _state.update {
                        it.copy(
                            title = occurrence.title,
                            date = occurrence.scheduledDate,
                            timeMinutes = occurrence.scheduledTimeMinutes,
                            priority = occurrence.priority,
                            color = occurrence.color,
                            recurrenceType = template?.recurrenceType ?: RecurrenceType.ONE_TIME,
                            selectedWeekdays = weekdaysSet,
                            dayOfMonth = template?.dayOfMonth ?: 1,
                            specificDates = specificDatesList,
                            usedColors = usedColors,
                            isEditMode = true,
                            isRecurringTask = isRecurring,
                            editScope = if (isRecurring) EditScope.ONLY_TODAY else EditScope.REPEAT,
                            originalOccurrence = occurrence,
                            originalTemplate = template
                        )
                    }
                }
            } else {
                // Add mode — pick first free color
                val usedColors = repository.getUsedColors()
                val firstFreeColor = TASK_COLOR_PALETTE.firstOrNull { it !in usedColors }
                    ?: TASK_COLOR_PALETTE.first()
                _state.update {
                    it.copy(
                        date = today,
                        usedColors = usedColors,
                        color = firstFreeColor
                    )
                }
            }
        }
    }

    fun updateTitle(v: String) { _state.update { it.copy(title = v, error = null) } }
    fun updateDate(v: String) { _state.update { it.copy(date = v, error = null) } }
    fun updateTime(minutes: Int) { _state.update { it.copy(timeMinutes = minutes, error = null) } }
    fun updatePriority(v: Priority) { _state.update { it.copy(priority = v) } }
    fun updateColor(v: Int) { _state.update { it.copy(color = v) } }
    fun updateRecurrence(v: RecurrenceType) { _state.update { it.copy(recurrenceType = v) } }
    fun updateEditScope(scope: EditScope) { _state.update { it.copy(editScope = scope) } }

    fun toggleWeekday(dayOfWeek: Int) {
        _state.update {
            val current = it.selectedWeekdays.toMutableSet()
            if (dayOfWeek in current) {
                if (current.size > 1) current.remove(dayOfWeek)
            } else {
                current.add(dayOfWeek)
            }
            it.copy(selectedWeekdays = current)
        }
    }

    fun updateDayOfMonth(day: Int) {
        _state.update { it.copy(dayOfMonth = day.coerceIn(1, 31)) }
    }

    fun addSpecificDate(dateStr: String) {
        _state.update {
            if (dateStr !in it.specificDates) {
                it.copy(specificDates = it.specificDates + dateStr)
            } else it
        }
    }

    fun removeSpecificDate(dateStr: String) {
        _state.update {
            it.copy(specificDates = it.specificDates - dateStr)
        }
    }

    fun save() {
        val s = _state.value
        if (s.title.isBlank()) {
            _state.update { it.copy(error = "Title cannot be empty") }
            return
        }

        val weekdaysString = s.selectedWeekdays.sorted().joinToString(",")
        val specificDatesString = s.specificDates.joinToString(",")

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = if (s.isEditMode && s.originalOccurrence != null) {
                if (s.isRecurringTask && s.editScope == EditScope.ONLY_TODAY) {
                    // Modify ONLY today's occurrence
                    repository.updateOccurrenceOnly(
                        occurrence = s.originalOccurrence,
                        title = s.title.trim(),
                        timeMinutes = s.timeMinutes,
                        priority = s.priority,
                        color = s.color
                    )
                } else if (s.originalTemplate != null) {
                    // Modify recurring template & future occurrences
                    repository.updateTemplateAndFuture(
                        template = s.originalTemplate,
                        title = s.title.trim(),
                        timeMinutes = s.timeMinutes,
                        priority = s.priority,
                        color = s.color,
                        recurrenceType = s.recurrenceType,
                        weekdays = weekdaysString,
                        dayOfMonth = s.dayOfMonth,
                        specificDates = specificDatesString
                    )
                } else {
                    repository.updateOccurrenceOnly(
                        occurrence = s.originalOccurrence,
                        title = s.title.trim(),
                        timeMinutes = s.timeMinutes,
                        priority = s.priority,
                        color = s.color
                    )
                }
            } else {
                // Add Mode
                if (s.recurrenceType == RecurrenceType.ONE_TIME) {
                    repository.addOneTimeTask(
                        title = s.title.trim(),
                        date = s.date,
                        timeMinutes = s.timeMinutes,
                        priority = s.priority,
                        color = s.color
                    )
                } else {
                    repository.addRecurringTask(
                        title = s.title.trim(),
                        recurrenceType = s.recurrenceType,
                        startDate = s.date,
                        timeMinutes = s.timeMinutes,
                        priority = s.priority,
                        color = s.color,
                        weekdays = weekdaysString,
                        dayOfMonth = s.dayOfMonth,
                        specificDates = specificDatesString
                    )
                }
            }

            result.fold(
                onSuccess = { _state.update { it.copy(isLoading = false, saveSuccess = true) } },
                onFailure = { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
            )
        }
    }

    class Factory(
        private val repo: TaskRepository,
        private val occurrenceId: Long?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AddEditTaskViewModel(repo, occurrenceId) as T
    }
}
