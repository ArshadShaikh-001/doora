package com.example.doora.ui.addtask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.doora.data.db.entity.TaskOccurrence
import com.example.doora.data.model.OccurrenceStatus
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
import java.util.Date
import java.util.Locale

data class AddEditTaskUiState(
    val title: String = "",
    val date: String = "",          // "yyyy-MM-dd"
    val timeMinutes: Int = 9 * 60, // Default 9:00 AM
    val priority: Priority = Priority.MEDIUM,
    val color: Int = TASK_COLOR_PALETTE.first(),
    val recurrenceType: RecurrenceType = RecurrenceType.ONE_TIME,
    val usedColors: List<Int> = emptyList(),
    val isEditMode: Boolean = false,
    val originalOccurrence: TaskOccurrence? = null,
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
                // Edit mode — load the existing occurrence
                val occurrence = repository.getOccurrenceById(occurrenceId)
                if (occurrence != null) {
                    val usedColors = repository.getUsedColors(excludeTemplateId = occurrence.templateId)
                    _state.update {
                        it.copy(
                            title = occurrence.title,
                            date = occurrence.scheduledDate,
                            timeMinutes = occurrence.scheduledTimeMinutes,
                            priority = occurrence.priority,
                            color = occurrence.color,
                            usedColors = usedColors,
                            isEditMode = true,
                            originalOccurrence = occurrence
                        )
                    }
                }
            } else {
                // Add mode — pick the first colour not already used
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

    fun updateTitle(v: String)           { _state.update { it.copy(title = v, error = null) } }
    fun updateDate(v: String)            { _state.update { it.copy(date = v, error = null) } }
    fun updateTime(minutes: Int)         { _state.update { it.copy(timeMinutes = minutes, error = null) } }
    fun updatePriority(v: Priority)      { _state.update { it.copy(priority = v) } }
    fun updateColor(v: Int)              { _state.update { it.copy(color = v) } }
    fun updateRecurrence(v: RecurrenceType) { _state.update { it.copy(recurrenceType = v) } }

    fun save() {
        val s = _state.value
        if (s.title.isBlank()) {
            _state.update { it.copy(error = "Title cannot be empty") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = if (s.isEditMode && s.originalOccurrence != null) {
                repository.updateTask(
                    occurrence = s.originalOccurrence,
                    title = s.title.trim(),
                    date = s.date,
                    timeMinutes = s.timeMinutes,
                    priority = s.priority,
                    color = s.color
                )
            } else {
                when (s.recurrenceType) {
                    RecurrenceType.ONE_TIME -> repository.addOneTimeTask(
                        title = s.title.trim(),
                        date = s.date,
                        timeMinutes = s.timeMinutes,
                        priority = s.priority,
                        color = s.color
                    )
                    RecurrenceType.DAILY -> repository.addDailyTask(
                        title = s.title.trim(),
                        timeMinutes = s.timeMinutes,
                        priority = s.priority,
                        color = s.color
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
