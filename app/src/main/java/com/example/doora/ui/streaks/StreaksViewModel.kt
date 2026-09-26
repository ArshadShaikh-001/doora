package com.example.doora.ui.streaks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.doora.data.engine.DailyTaskStreak
import com.example.doora.data.engine.StreakAndStatsEngine
import com.example.doora.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StreaksUiState(
    val dailyStreaks: List<DailyTaskStreak> = emptyList(),
    val selectedTaskTemplateId: Long? = null,
    val isLoading: Boolean = false
) {
    val selectedTaskStreak: DailyTaskStreak?
        get() = dailyStreaks.find { it.templateId == selectedTaskTemplateId }
            ?: dailyStreaks.firstOrNull()
}

class StreaksViewModel(private val repository: TaskRepository) : ViewModel() {

    private val _selectedTemplateId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<StreaksUiState> = combine(
        repository.observeActiveDailyTemplates(),
        repository.observeAllOccurrences(),
        _selectedTemplateId
    ) { dailyTemplates, allOccurrences, selectedId ->
        val today = repository.todayString()
        val calculated = StreakAndStatsEngine.calculateAllDailyStreaks(
            dailyTemplates = dailyTemplates,
            allOccurrences = allOccurrences,
            todayDateStr = today
        )
        val effectiveSelected = if (selectedId != null && calculated.any { it.templateId == selectedId }) {
            selectedId
        } else {
            calculated.firstOrNull()?.templateId
        }
        StreaksUiState(
            dailyStreaks = calculated,
            selectedTaskTemplateId = effectiveSelected,
            isLoading = false
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        StreaksUiState(isLoading = true)
    )

    fun selectTask(templateId: Long) {
        _selectedTemplateId.value = templateId
    }

    class Factory(private val repo: TaskRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = StreaksViewModel(repo) as T
    }
}
