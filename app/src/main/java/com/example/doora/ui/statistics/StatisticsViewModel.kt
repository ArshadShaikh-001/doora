package com.example.doora.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.doora.data.engine.StatisticsData
import com.example.doora.data.engine.StatsTimeRange
import com.example.doora.data.engine.StreakAndStatsEngine
import com.example.doora.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class StatisticsUiState(
    val selectedRange: StatsTimeRange = StatsTimeRange.LAST_7_DAYS,
    val stats: StatisticsData = StatisticsData(),
    val isLoading: Boolean = true
)

class StatisticsViewModel(private val repository: TaskRepository) : ViewModel() {

    private val _selectedRange = MutableStateFlow(StatsTimeRange.LAST_7_DAYS)
    private val _state = MutableStateFlow(StatisticsUiState())
    val state: StateFlow<StatisticsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.observeAllOccurrences(),
                repository.observeAllTemplates(),
                _selectedRange
            ) { allOccurrences, allTemplates, selectedRange ->
                val today = repository.todayString()
                val stats = StreakAndStatsEngine.calculateStatistics(
                    allOccurrences = allOccurrences,
                    templates = allTemplates,
                    selectedRange = selectedRange,
                    todayDateStr = today
                )
                StatisticsUiState(
                    selectedRange = selectedRange,
                    stats = stats,
                    isLoading = false
                )
            }.collect { uiState ->
                _state.value = uiState
            }
        }
    }

    fun selectRange(range: StatsTimeRange) {
        _selectedRange.value = range
    }

    class Factory(private val repo: TaskRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = StatisticsViewModel(repo) as T
    }
}
