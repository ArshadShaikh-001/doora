package com.example.doora.data.repository

import com.example.doora.data.db.dao.TaskOccurrenceDao
import com.example.doora.data.db.dao.TaskTemplateDao
import com.example.doora.data.db.entity.TaskOccurrence
import com.example.doora.data.db.entity.TaskTemplate
import com.example.doora.data.model.OccurrenceStatus
import com.example.doora.data.model.Priority
import com.example.doora.data.model.RecurrenceType
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskRepository(
    private val templateDao: TaskTemplateDao,
    private val occurrenceDao: TaskOccurrenceDao
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    fun todayString(): String = dateFormat.format(Date())

    // ---------------------------------------------------------------------------
    // Queries
    // ---------------------------------------------------------------------------

    /** Live-observable list of occurrences for today, pre-sorted by priority then time. */
    fun getTodayOccurrences(): Flow<List<TaskOccurrence>> =
        occurrenceDao.observeForDate(todayString())

    /**
     * Called on Home screen load. Creates today's occurrence for every active DAILY template
     * that doesn't have one yet (e.g., first open of the day).
     */
    suspend fun ensureTodayOccurrences() {
        val today = todayString()
        templateDao.getActiveTemplates().forEach { template ->
            if (template.recurrenceType == RecurrenceType.DAILY) {
                if (occurrenceDao.findByTemplateAndDate(template.id, today) == null) {
                    runCatching {
                        occurrenceDao.insert(
                            TaskOccurrence(
                                templateId = template.id,
                                title = template.title,
                                priority = template.priority,
                                color = template.color,
                                scheduledDate = today,
                                scheduledTimeMinutes = template.scheduledTimeMinutes
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Returns colours currently used by active templates.
     * [excludeTemplateId] = -1 to include all (add mode);
     * pass a real id to exclude the task being edited (edit mode).
     */
    suspend fun getUsedColors(excludeTemplateId: Long = -1L): List<Int> =
        templateDao.getUsedColors(excludeTemplateId)

    suspend fun getTemplateById(id: Long): TaskTemplate? = templateDao.getById(id)

    suspend fun getOccurrenceById(id: Long): TaskOccurrence? = occurrenceDao.findById(id)

    suspend fun hasTimeConflict(
        date: String,
        timeMinutes: Int,
        excludeOccurrenceId: Long = -1L
    ): Boolean = occurrenceDao.findConflict(date, timeMinutes, excludeOccurrenceId) != null

    // ---------------------------------------------------------------------------
    // Mutations — Add
    // ---------------------------------------------------------------------------

    suspend fun addOneTimeTask(
        title: String,
        date: String,
        timeMinutes: Int,
        priority: Priority,
        color: Int
    ): Result<Unit> = runCatching {
        if (hasTimeConflict(date, timeMinutes)) error("A task already exists at this time")
        val templateId = templateDao.insert(
            TaskTemplate(
                title = title,
                priority = priority,
                color = color,
                recurrenceType = RecurrenceType.ONE_TIME,
                scheduledTimeMinutes = timeMinutes
            )
        )
        occurrenceDao.insert(
            TaskOccurrence(
                templateId = templateId,
                title = title,
                priority = priority,
                color = color,
                scheduledDate = date,
                scheduledTimeMinutes = timeMinutes
            )
        )
        Unit
    }

    suspend fun addDailyTask(
        title: String,
        timeMinutes: Int,
        priority: Priority,
        color: Int
    ): Result<Unit> = runCatching {
        val today = todayString()
        if (hasTimeConflict(today, timeMinutes)) error("A task already exists at this time today")
        val templateId = templateDao.insert(
            TaskTemplate(
                title = title,
                priority = priority,
                color = color,
                recurrenceType = RecurrenceType.DAILY,
                scheduledTimeMinutes = timeMinutes
            )
        )
        occurrenceDao.insert(
            TaskOccurrence(
                templateId = templateId,
                title = title,
                priority = priority,
                color = color,
                scheduledDate = today,
                scheduledTimeMinutes = timeMinutes
            )
        )
        Unit
    }

    // ---------------------------------------------------------------------------
    // Mutations — Status
    // ---------------------------------------------------------------------------

    suspend fun completeOccurrence(id: Long) {
        occurrenceDao.updateStatus(id, OccurrenceStatus.COMPLETED, System.currentTimeMillis())
    }

    suspend fun skipOccurrence(id: Long) {
        occurrenceDao.updateStatus(id, OccurrenceStatus.SKIPPED, null)
    }

    // ---------------------------------------------------------------------------
    // Mutations — Delete
    // ---------------------------------------------------------------------------

    /**
     * Deletes the given occurrence and soft-deletes its template (cancelling recurrence for DAILY).
     */
    suspend fun deleteTask(occurrence: TaskOccurrence) {
        occurrenceDao.deleteById(occurrence.id)
        templateDao.softDelete(occurrence.templateId)
    }

    // ---------------------------------------------------------------------------
    // Mutations — Edit
    // ---------------------------------------------------------------------------

    suspend fun updateTask(
        occurrence: TaskOccurrence,
        title: String,
        date: String,
        timeMinutes: Int,
        priority: Priority,
        color: Int
    ): Result<Unit> = runCatching {
        if (hasTimeConflict(date, timeMinutes, occurrence.id)) {
            error("A task already exists at this time")
        }
        occurrenceDao.update(
            occurrence.copy(
                title = title,
                scheduledDate = date,
                scheduledTimeMinutes = timeMinutes,
                priority = priority,
                color = color
            )
        )
        templateDao.getById(occurrence.templateId)?.let { template ->
            templateDao.update(
                template.copy(
                    title = title,
                    priority = priority,
                    color = color,
                    scheduledTimeMinutes = timeMinutes
                )
            )
        }
        Unit
    }
}
