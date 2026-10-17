package com.example.doora.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.doora.data.engine.DailyInsightsData
import com.example.doora.data.engine.StreakAndStatsEngine
import com.example.doora.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class StatisticsUiState(
    val selectedDate: String = "",
    val todayDate: String = "",
    val insights: DailyInsightsData = DailyInsightsData(),
    val isLoading: Boolean = true
)

class StatisticsViewModel(private val repository: TaskRepository) : ViewModel() {

    private val _selectedDate = MutableStateFlow(repository.todayString())
    private val _state = MutableStateFlow(StatisticsUiState(selectedDate = repository.todayString(), todayDate = repository.todayString()))
    val state: StateFlow<StatisticsUiState> = _state.asStateFlow()

    init {
        val today = repository.todayString()
        viewModelScope.launch {
            repository.ensureOccurrencesForDate(today)
        }

        viewModelScope.launch {
            combine(
                repository.observeAllOccurrences(),
                repository.observeAllTemplates(),
                _selectedDate
            ) { allOccurrences, allTemplates, selectedDate ->
                val currentToday = repository.todayString()
                val insights = StreakAndStatsEngine.calculateDailyInsights(
                    selectedDateStr = selectedDate,
                    todayDateStr = currentToday,
                    allTemplates = allTemplates,
                    allOccurrences = allOccurrences
                )
                StatisticsUiState(
                    selectedDate = selectedDate,
                    todayDate = currentToday,
                    insights = insights,
                    isLoading = false
                )
            }.collect { uiState ->
                _state.value = uiState
            }
        }
    }

    fun selectDate(dateStr: String) {
        if (dateStr.isNotBlank()) {
            _selectedDate.value = dateStr
        }
    }

    fun goToPreviousDay() {
        _selectedDate.value = StreakAndStatsEngine.getPreviousDateString(_selectedDate.value)
    }

    fun goToNextDay() {
        _selectedDate.value = StreakAndStatsEngine.getNextDateString(_selectedDate.value)
    }

    fun goToToday() {
        _selectedDate.value = repository.todayString()
    }

    class Factory(private val repo: TaskRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = StatisticsViewModel(repo) as T
    }
}
