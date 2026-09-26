package com.example.doora.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.doora.data.db.entity.TaskOccurrence
import com.example.doora.data.db.entity.TaskTemplate
import com.example.doora.data.model.OccurrenceStatus
import com.example.doora.data.model.Priority
import com.example.doora.data.repository.TaskRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: TaskRepository) : ViewModel() {

    val templateMap: StateFlow<Map<Long, TaskTemplate>> = repository.observeAllTemplates()
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyMap())

    /**
     * Today's occurrences, strictly separated into:
     * 1. Incomplete tasks (ordered strictly by scheduledTimeMinutes ASC, id ASC)
     *    Priority MUST NOT change ordering — scheduled time always comes first.
     * 2. Completed tasks (ordered by completedAt, scheduled time)
     * Completed tasks ALWAYS remain below all incomplete tasks.
     */
    val todayTasks: StateFlow<List<TaskOccurrence>> = repository.getTodayOccurrences()
        .map { list ->
            val incomplete = list.filter { it.status != OccurrenceStatus.COMPLETED }
                .sortedWith(
                    compareBy<TaskOccurrence> { it.scheduledTimeMinutes }
                        .thenBy { it.id }
                )

            val completed = list.filter { it.status == OccurrenceStatus.COMPLETED }
                .sortedWith(
                    compareBy<TaskOccurrence> { it.completedAt ?: 0L }
                        .thenBy { it.scheduledTimeMinutes }
                        .thenBy { it.id }
                )

            incomplete + completed
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    init {
        // Run day boundary rollover and generate today's occurrences
        viewModelScope.launch {
            repository.ensureTodayOccurrences()
        }
    }

    fun completeTask(occurrence: TaskOccurrence) {
        viewModelScope.launch {
            // Brief visual confirmation delay (180ms) before card moves downward into completed
            delay(180)
            repository.completeOccurrence(occurrence.id)
        }
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

    suspend fun isRecurring(templateId: Long): Boolean {
        return repository.isRecurring(templateId)
    }

    class Factory(private val repo: TaskRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(repo) as T
    }
}
