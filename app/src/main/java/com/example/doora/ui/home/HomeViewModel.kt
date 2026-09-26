package com.example.doora.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.doora.data.db.entity.TaskOccurrence
import com.example.doora.data.repository.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: TaskRepository) : ViewModel() {

    /** Today's occurrences, sorted by priority then time, as a hot StateFlow. */
    val todayTasks = repository.getTodayOccurrences()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    init {
        // Generate today's occurrence for any active DAILY templates that lack one.
        viewModelScope.launch { repository.ensureTodayOccurrences() }
    }

    fun completeTask(occurrence: TaskOccurrence) {
        viewModelScope.launch { repository.completeOccurrence(occurrence.id) }
    }

    fun skipTask(occurrence: TaskOccurrence) {
        viewModelScope.launch { repository.skipOccurrence(occurrence.id) }
    }

    fun deleteTask(occurrence: TaskOccurrence) {
        viewModelScope.launch { repository.deleteTask(occurrence) }
    }

    class Factory(private val repo: TaskRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(repo) as T
    }
}
