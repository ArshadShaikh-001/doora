package com.example.doora.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.doora.data.db.entity.TaskOccurrence
import com.example.doora.data.engine.StreakAndStatsEngine
import com.example.doora.data.engine.StreakData
import com.example.doora.data.repository.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: TaskRepository) : ViewModel() {

    /** Today's occurrences, sorted by priority then time, as a hot StateFlow. */
    val todayTasks: StateFlow<List<TaskOccurrence>> = repository.getTodayOccurrences()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    /** Current streak info for the header pill. */
    val streak: StateFlow<StreakData> = repository.observeAllOccurrences()
        .map { StreakAndStatsEngine.calculateStreak(it, repository.todayString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), StreakData(0, 0))

    init {
        // Run day boundary rollover and generate today's occurrences
        viewModelScope.launch {
            repository.ensureTodayOccurrences()
        }
    }

    fun completeTask(occurrence: TaskOccurrence) {
        viewModelScope.launch { repository.completeOccurrence(occurrence.id) }
    }

    fun skipTask(occurrence: TaskOccurrence) {
        viewModelScope.launch { repository.skipOccurrence(occurrence.id) }
    }

    fun deleteOccurrenceOnly(occurrence: TaskOccurrence) {
        viewModelScope.launch { repository.deleteOccurrence(occurrence) }
    }

    fun deleteRecurring(occurrence: TaskOccurrence) {
        viewModelScope.launch { repository.deleteRecurringTemplate(occurrence.templateId) }
    }

    class Factory(private val repo: TaskRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(repo) as T
    }
}
