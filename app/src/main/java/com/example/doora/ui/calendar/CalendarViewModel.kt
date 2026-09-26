package com.example.doora.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.doora.data.db.entity.TaskOccurrence
import com.example.doora.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CalendarUiState(
    val monthTitle: String = "",
    val yearMonthString: String = "", // "yyyy-MM"
    val daysInMonth: List<CalendarDay> = emptyList(),
    val selectedDate: String = "",    // "yyyy-MM-dd"
    val selectedDateTasks: List<TaskOccurrence> = emptyList(),
    val occurrencesByDate: Map<String, List<TaskOccurrence>> = emptyMap()
)

data class CalendarDay(
    val dateStr: String, // "yyyy-MM-dd"
    val dayNumber: Int,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val tasks: List<TaskOccurrence> = emptyList()
)

class CalendarViewModel(private val repository: TaskRepository) : ViewModel() {

    private val calendar = Calendar.getInstance()
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val yearMonthFormat = SimpleDateFormat("yyyy-MM", Locale.US)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    init {
        val today = repository.todayString()
        _state.update { it.copy(selectedDate = today) }
        loadMonthData()

        viewModelScope.launch {
            repository.observeAllOccurrences().collect { allOccurrences ->
                val byDate = allOccurrences.groupBy { it.scheduledDate }
                _state.update { current ->
                    val selectedTasks = byDate[current.selectedDate] ?: emptyList()
                    val updatedDays = current.daysInMonth.map { day ->
                        day.copy(tasks = byDate[day.dateStr] ?: emptyList())
                    }
                    current.copy(
                        occurrencesByDate = byDate,
                        selectedDateTasks = selectedTasks,
                        daysInMonth = updatedDays
                    )
                }
            }
        }
    }

    private fun loadMonthData() {
        val monthTitle = monthFormat.format(calendar.time)
        val ym = yearMonthFormat.format(calendar.time)
        val today = repository.todayString()

        // Compute 42 grid cells (6 rows x 7 days)
        val cal = calendar.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)

        // Find leading days from previous month
        // Monday = 2, Sunday = 1 in Calendar
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        // Convert to Mon=0 .. Sun=6
        val daysBefore = (firstDayOfWeek - Calendar.MONDAY + 7) % 7
        cal.add(Calendar.DAY_OF_MONTH, -daysBefore)

        val days = mutableListOf<CalendarDay>()
        val byDate = _state.value.occurrencesByDate

        for (i in 0 until 42) {
            val dateStr = dateFormat.format(cal.time)
            val dayNumber = cal.get(Calendar.DAY_OF_MONTH)
            val isCurrentMonth = yearMonthFormat.format(cal.time) == ym
            val isToday = dateStr == today

            days.add(
                CalendarDay(
                    dateStr = dateStr,
                    dayNumber = dayNumber,
                    isCurrentMonth = isCurrentMonth,
                    isToday = isToday,
                    tasks = byDate[dateStr] ?: emptyList()
                )
            )
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        _state.update {
            it.copy(
                monthTitle = monthTitle,
                yearMonthString = ym,
                daysInMonth = days,
                selectedDateTasks = byDate[it.selectedDate] ?: emptyList()
            )
        }
    }

    fun prevMonth() {
        calendar.add(Calendar.MONTH, -1)
        loadMonthData()
    }

    fun nextMonth() {
        calendar.add(Calendar.MONTH, 1)
        loadMonthData()
    }

    fun selectDate(dateStr: String) {
        viewModelScope.launch {
            repository.ensureOccurrencesForDate(dateStr)
            val tasks = _state.value.occurrencesByDate[dateStr] ?: emptyList()
            _state.update {
                it.copy(
                    selectedDate = dateStr,
                    selectedDateTasks = tasks
                )
            }
        }
    }

    fun completeTask(occurrence: TaskOccurrence) {
        viewModelScope.launch { repository.completeOccurrence(occurrence.id) }
    }

    fun skipTask(occurrence: TaskOccurrence) {
        viewModelScope.launch { repository.skipOccurrence(occurrence.id) }
    }

    class Factory(private val repo: TaskRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CalendarViewModel(repo) as T
    }
}
