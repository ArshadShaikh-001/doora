package com.example.doora.data.repository

import android.content.Context
import com.example.doora.alarm.AlarmHelper
import com.example.doora.data.db.dao.TaskOccurrenceDao
import com.example.doora.data.db.dao.TaskTemplateDao
import com.example.doora.data.db.entity.TaskOccurrence
import com.example.doora.data.db.entity.TaskTemplate
import com.example.doora.data.engine.RecurrenceEngine
import com.example.doora.data.model.OccurrenceStatus
import com.example.doora.data.model.Priority
import com.example.doora.data.model.RecurrenceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskRepository(
    private val context: Context,
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

    fun getOccurrencesForDate(dateStr: String): Flow<List<TaskOccurrence>> =
        occurrenceDao.observeForDate(dateStr)

    fun getOccurrencesForDateRange(startDate: String, endDate: String): Flow<List<TaskOccurrence>> =
        occurrenceDao.observeForDateRange(startDate, endDate)

    fun observeAllOccurrences(): Flow<List<TaskOccurrence>> =
        occurrenceDao.observeAllOccurrences()

    suspend fun getAllOccurrences(): List<TaskOccurrence> =
        occurrenceDao.getAllOccurrences()

    fun observeActiveTemplates(): Flow<List<TaskTemplate>> =
        templateDao.observeActiveTemplates()

    fun observeActiveDailyTemplates(): Flow<List<TaskTemplate>> =
        templateDao.observeActiveTemplates().map { templates ->
            templates.filter { it.recurrenceType == RecurrenceType.DAILY }
        }

    suspend fun getActiveDailyTemplates(): List<TaskTemplate> =
        templateDao.getActiveTemplates().filter { it.recurrenceType == RecurrenceType.DAILY }

    /**
     * Day boundary / Midnight Engine:
     * - Any previous-day PENDING occurrence becomes MISSED.
     * - A snoozed task crossing midnight belongs to its original occurrence/date.
     */
    suspend fun processDayTransition() {
        val today = todayString()
        val pastPending = occurrenceDao.getPastPendingOccurrences(today)
        pastPending.forEach { pastOccurrence ->
            // If snoozed across midnight and snooze is still active in the future, don't miss yet
            val now = System.currentTimeMillis()
            if (pastOccurrence.snoozedUntilMillis != null && pastOccurrence.snoozedUntilMillis > now) {
                // Still in active snooze, keep pending
            } else {
                occurrenceDao.updateStatus(pastOccurrence.id, OccurrenceStatus.MISSED, null)
                AlarmHelper.cancelAlarm(context, pastOccurrence.id)
            }
        }
    }

    /**
     * Called on Home load and day transition:
     * 1. Rolls over past pending tasks to MISSED.
     * 2. Generates today's occurrence for any active templates that match today.
     * 3. Schedules future alarms.
     */
    suspend fun ensureTodayOccurrences() {
        processDayTransition()
        val today = todayString()
        val activeTemplates = templateDao.getActiveTemplates()

        activeTemplates.forEach { template ->
            if (RecurrenceEngine.shouldOccurOnDate(template, today)) {
                if (occurrenceDao.findByTemplateAndDate(template.id, today) == null) {
                    runCatching {
                        val occ = TaskOccurrence(
                            templateId = template.id,
                            title = template.title,
                            priority = template.priority,
                            color = template.color,
                            scheduledDate = today,
                            scheduledTimeMinutes = template.scheduledTimeMinutes
                        )
                        val insertedId = occurrenceDao.insert(occ)
                        val createdOcc = occ.copy(id = insertedId)
                        AlarmHelper.scheduleAlarm(context, createdOcc)
                    }
                }
            }
        }
    }

    /**
     * Pre-generates occurrences for a calendar date if needed.
     */
    suspend fun ensureOccurrencesForDate(dateStr: String) {
        val activeTemplates = templateDao.getActiveTemplates()
        activeTemplates.forEach { template ->
            if (RecurrenceEngine.shouldOccurOnDate(template, dateStr)) {
                if (occurrenceDao.findByTemplateAndDate(template.id, dateStr) == null) {
                    runCatching {
                        val occ = TaskOccurrence(
                            templateId = template.id,
                            title = template.title,
                            priority = template.priority,
                            color = template.color,
                            scheduledDate = dateStr,
                            scheduledTimeMinutes = template.scheduledTimeMinutes
                        )
                        val id = occurrenceDao.insert(occ)
                        if (dateStr == todayString()) {
                            AlarmHelper.scheduleAlarm(context, occ.copy(id = id))
                        }
                    }
                }
            }
        }
    }

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
        if (hasTimeConflict(date, timeMinutes)) error("A task already exists at this time on $date")
        val templateId = templateDao.insert(
            TaskTemplate(
                title = title,
                priority = priority,
                color = color,
                recurrenceType = RecurrenceType.ONE_TIME,
                scheduledTimeMinutes = timeMinutes,
                startDate = date
            )
        )
        val occurrence = TaskOccurrence(
            templateId = templateId,
            title = title,
            priority = priority,
            color = color,
            scheduledDate = date,
            scheduledTimeMinutes = timeMinutes
        )
        val occurrenceId = occurrenceDao.insert(occurrence)
        AlarmHelper.scheduleAlarm(context, occurrence.copy(id = occurrenceId))
    }

    suspend fun addRecurringTask(
        title: String,
        recurrenceType: RecurrenceType,
        startDate: String,
        timeMinutes: Int,
        priority: Priority,
        color: Int,
        weekdays: String = "",
        dayOfMonth: Int = 1,
        specificDates: String = ""
    ): Result<Unit> = runCatching {
        val today = todayString()
        if (startDate == today && hasTimeConflict(today, timeMinutes)) {
            error("A task already exists at this time today")
        }

        val template = TaskTemplate(
            title = title,
            priority = priority,
            color = color,
            recurrenceType = recurrenceType,
            scheduledTimeMinutes = timeMinutes,
            startDate = startDate,
            weekdays = weekdays,
            dayOfMonth = dayOfMonth,
            specificDates = specificDates
        )
        val templateId = templateDao.insert(template)
        val savedTemplate = template.copy(id = templateId)

        // If it applies to today, create today's occurrence & schedule alarm
        if (RecurrenceEngine.shouldOccurOnDate(savedTemplate, today)) {
            val occurrence = TaskOccurrence(
                templateId = templateId,
                title = title,
                priority = priority,
                color = color,
                scheduledDate = today,
                scheduledTimeMinutes = timeMinutes
            )
            val occurrenceId = occurrenceDao.insert(occurrence)
            AlarmHelper.scheduleAlarm(context, occurrence.copy(id = occurrenceId))
        }
    }

    // ---------------------------------------------------------------------------
    // Mutations — Status & Alarm Actions
    // ---------------------------------------------------------------------------

    suspend fun completeOccurrence(id: Long) {
        occurrenceDao.updateStatus(id, OccurrenceStatus.COMPLETED, System.currentTimeMillis())
        occurrenceDao.updateSnooze(id, null)
        AlarmHelper.cancelAlarm(context, id)
    }

    suspend fun skipOccurrence(id: Long) {
        occurrenceDao.updateStatus(id, OccurrenceStatus.SKIPPED, null)
        occurrenceDao.updateSnooze(id, null)
        AlarmHelper.cancelAlarm(context, id)
    }

    suspend fun snoozeOccurrence(id: Long, minutes: Int = 10) {
        val snoozedUntil = System.currentTimeMillis() + (minutes * 60 * 1000L)
        occurrenceDao.updateSnooze(id, snoozedUntil)
        val occ = occurrenceDao.findById(id)
        if (occ != null) {
            AlarmHelper.scheduleAlarm(context, occ.copy(snoozedUntilMillis = snoozedUntil))
        }
    }

    // ---------------------------------------------------------------------------
    // Mutations — Delete
    // ---------------------------------------------------------------------------

    /** Deletes single occurrence */
    suspend fun deleteOccurrence(occurrence: TaskOccurrence) {
        AlarmHelper.cancelAlarm(context, occurrence.id)
        occurrenceDao.deleteById(occurrence.id)
    }

    /**
     * Deletes the recurring template and its future pending occurrences.
     * Historical past occurrences are preserved as required.
     */
    suspend fun deleteRecurringTemplate(templateId: Long) {
        val today = todayString()
        occurrenceDao.findByTemplateAndDate(templateId, today)?.let {
            AlarmHelper.cancelAlarm(context, it.id)
        }
        occurrenceDao.deleteFuturePendingOccurrences(templateId, today)
        templateDao.softDelete(templateId)
    }

    // ---------------------------------------------------------------------------
    // Mutations — Edit
    // ---------------------------------------------------------------------------

    /**
     * Edit "Only Today": modifies only this date's occurrence snapshot.
     */
    suspend fun updateOccurrenceOnly(
        occurrence: TaskOccurrence,
        title: String,
        timeMinutes: Int,
        priority: Priority,
        color: Int
    ): Result<Unit> = runCatching {
        if (hasTimeConflict(occurrence.scheduledDate, timeMinutes, occurrence.id)) {
            error("A task already exists at this time on ${occurrence.scheduledDate}")
        }
        val updated = occurrence.copy(
            title = title,
            scheduledTimeMinutes = timeMinutes,
            priority = priority,
            color = color
        )
        occurrenceDao.update(updated)
        AlarmHelper.cancelAlarm(context, occurrence.id)
        if (updated.status == OccurrenceStatus.PENDING) {
            AlarmHelper.scheduleAlarm(context, updated)
        }
    }

    /**
     * Edit "Repeat": modifies the recurring template and current/future occurrences.
     * Historical past occurrences are preserved as historical snapshots.
     */
    suspend fun updateTemplateAndFuture(
        template: TaskTemplate,
        title: String,
        timeMinutes: Int,
        priority: Priority,
        color: Int,
        recurrenceType: RecurrenceType,
        weekdays: String = "",
        dayOfMonth: Int = 1,
        specificDates: String = ""
    ): Result<Unit> = runCatching {
        val today = todayString()
        val currentOcc = occurrenceDao.findByTemplateAndDate(template.id, today)
        if (currentOcc != null && hasTimeConflict(today, timeMinutes, currentOcc.id)) {
            error("A task already exists at this time today")
        }

        val updatedTemplate = template.copy(
            title = title,
            priority = priority,
            color = color,
            recurrenceType = recurrenceType,
            scheduledTimeMinutes = timeMinutes,
            weekdays = weekdays,
            dayOfMonth = dayOfMonth,
            specificDates = specificDates
        )
        templateDao.update(updatedTemplate)

        // Update today's occurrence if still pending
        if (currentOcc != null && currentOcc.status == OccurrenceStatus.PENDING) {
            val updatedOcc = currentOcc.copy(
                title = title,
                priority = priority,
                color = color,
                scheduledTimeMinutes = timeMinutes
            )
            occurrenceDao.update(updatedOcc)
            AlarmHelper.cancelAlarm(context, currentOcc.id)
            AlarmHelper.scheduleAlarm(context, updatedOcc)
        }
    }

    /**
     * Restores future alarms upon reboot without creating catch-up alarms.
     */
    suspend fun rescheduleAllFutureAlarms() {
        val now = System.currentTimeMillis()
        val pending = occurrenceDao.getAllPendingOccurrences()
        pending.forEach { occ ->
            val triggerMillis = AlarmHelper.computeTriggerMillis(occ)
            // Only schedule if strictly in the future (no catch-up alarm for past)
            if (triggerMillis > now) {
                AlarmHelper.scheduleAlarm(context, occ)
            }
        }
    }
}
