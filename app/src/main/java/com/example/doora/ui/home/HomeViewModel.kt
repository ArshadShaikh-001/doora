package com.example.doora.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.doora.data.db.entity.TaskOccurrence
import com.example.doora.data.db.entity.TaskTemplate
import com.example.doora.data.model.OccurrenceStatus
import com.example.doora.data.repository.TaskRepository
import com.example.doora.data.repository.TasksPreferencesRepository
import com.example.doora.data.repository.TasksViewMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HomeViewModel(
    private val repository: TaskRepository,
    private val preferencesRepository: TasksPreferencesRepository
) : ViewModel() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // Persistent default view preference (List vs Timeline)
    private val _defaultViewMode = MutableStateFlow(preferencesRepository.getDefaultViewMode())
    val defaultViewMode: StateFlow<TasksViewMode> = _defaultViewMode.asStateFlow()

    // Active view mode for the current session, initialized from persisted default view preference
    private val _currentViewMode = MutableStateFlow(preferencesRepository.getDefaultViewMode())
    val currentViewMode: StateFlow<TasksViewMode> = _currentViewMode.asStateFlow()

    // Currently selected date for the Timeline view (session-preserved, initially today)
    private val _selectedTimelineDate = MutableStateFlow(repository.todayString())
    val selectedTimelineDate: StateFlow<String> = _selectedTimelineDate.asStateFlow()

    val templateMap: StateFlow<Map<Long, TaskTemplate>> = repository.observeAllTemplates()
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyMap())

    /**
     * Today's occurrences for List View, strictly separated into:
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

    /**
     * Timeline occurrences for [selectedTimelineDate], ordered strictly chronologically:
     * scheduledTimeMinutes ASC, id ASC.
     * In Timeline view, all occurrences appear in time order regardless of completion status.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val timelineTasks: StateFlow<List<TaskOccurrence>> = _selectedTimelineDate
        .flatMapLatest { dateStr ->
            repository.getOccurrencesForDate(dateStr)
        }
        .map { list ->
            list.sortedWith(
                compareBy<TaskOccurrence> { it.scheduledTimeMinutes }
                    .thenBy { it.id }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    init {
        // Run day boundary rollover and generate today's occurrences
        viewModelScope.launch {
            repository.ensureTodayOccurrences()
        }
    }

    // -----------------------------------------------------------------------
    // View Mode & Preferences
    // -----------------------------------------------------------------------

    fun setViewMode(mode: TasksViewMode) {
        _currentViewMode.value = mode
    }

    fun setDefaultViewMode(mode: TasksViewMode) {
        preferencesRepository.setDefaultViewMode(mode)
        _defaultViewMode.value = mode
    }

    // -----------------------------------------------------------------------
    // Timeline Date Navigation
    // -----------------------------------------------------------------------

    fun selectTimelineDate(dateStr: String) {
        _selectedTimelineDate.value = dateStr
        val today = repository.todayString()
        if (dateStr >= today) {
            viewModelScope.launch {
                if (dateStr == today) {
                    repository.ensureTodayOccurrences()
                } else {
                    repository.ensureOccurrencesForDate(dateStr)
                }
            }
        }
    }

    fun prevTimelineDay() {
        val cal = Calendar.getInstance()
        val parsed = runCatching { dateFormat.parse(_selectedTimelineDate.value) }.getOrNull() ?: Date()
        cal.time = parsed
        cal.add(Calendar.DAY_OF_MONTH, -1)
        selectTimelineDate(dateFormat.format(cal.time))
    }

    fun nextTimelineDay() {
        val cal = Calendar.getInstance()
        val parsed = runCatching { dateFormat.parse(_selectedTimelineDate.value) }.getOrNull() ?: Date()
        cal.time = parsed
        cal.add(Calendar.DAY_OF_MONTH, 1)
        selectTimelineDate(dateFormat.format(cal.time))
    }

    fun goToToday() {
        selectTimelineDate(repository.todayString())
    }

    // -----------------------------------------------------------------------
    // Task Actions
    // -----------------------------------------------------------------------

    fun completeTask(occurrence: TaskOccurrence) {
        viewModelScope.launch {
            // Brief visual confirmation delay (180ms)
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

    class Factory(
        private val repo: TaskRepository,
        private val prefsRepo: TasksPreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(repo, prefsRepo) as T
    }
}
