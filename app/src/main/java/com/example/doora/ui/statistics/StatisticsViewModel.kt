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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatisticsUiState(
    val selectedRange: StatsTimeRange = StatsTimeRange.LAST_7_DAYS,
    val stats: StatisticsData = StatisticsData(),
    val isLoading: Boolean = true
)

class StatisticsViewModel(private val repository: TaskRepository) : ViewModel() {

    private val _state = MutableStateFlow(StatisticsUiState())
    val state: StateFlow<StatisticsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAllOccurrences().collect { allOccurrences ->
                val today = repository.todayString()
                val stats = StreakAndStatsEngine.calculateStatistics(
                    allOccurrences = allOccurrences,
                    selectedRange = _state.value.selectedRange,
                    todayDateStr = today
                )
                _state.update {
                    it.copy(stats = stats, isLoading = false)
                }
            }
        }
    }

    fun selectRange(range: StatsTimeRange) {
        viewModelScope.launch {
            _state.update { it.copy(selectedRange = range) }
            val allOccurrences = repository.getAllOccurrences()
            val today = repository.todayString()
            val stats = StreakAndStatsEngine.calculateStatistics(
                allOccurrences = allOccurrences,
                selectedRange = range,
                todayDateStr = today
            )
            _state.update { it.copy(stats = stats) }
        }
    }

    class Factory(private val repo: TaskRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = StatisticsViewModel(repo) as T
    }
}
