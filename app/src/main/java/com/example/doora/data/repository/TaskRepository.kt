package com.example.doora.data.repository

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.doora.alarm.AlarmActionReceiver
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
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

    fun observeAllTemplates(): Flow<List<TaskTemplate>> =
        templateDao.observeAllTemplates()

    suspend fun getAllTemplates(): List<TaskTemplate> =
        templateDao.getAllTemplates()

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
                occurrenceDao.updateHourlyReminder(pastOccurrence.id, active = false, nextReminderMillis = null)
                AlarmHelper.cancelAlarm(context, pastOccurrence.id)
                AlarmHelper.cancelHourlyReminderAlarm(context, pastOccurrence.id)
                AlarmHelper.cancelHourlyReminderNotification(context, pastOccurrence.id)
            }
        }
    }

    /**
     * Called on Home load and day transition:
     * 1. Rolls over past pending tasks to MISSED.
     * 2. Reconciles active hourly follow-up reminders.
     * 3. Generates today's occurrence for any active templates that match today.
     * 4. Schedules future alarms.
     */
    suspend fun ensureTodayOccurrences() {
        processDayTransition()
        reconcileActiveReminders()
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
                            scheduledTimeMinutes = template.scheduledTimeMinutes,
                            ringtoneUri = template.ringtoneUri
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
                            scheduledTimeMinutes = template.scheduledTimeMinutes,
                            ringtoneUri = template.ringtoneUri
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
        getReservedColorsForDate(todayString(), excludeTemplateId = excludeTemplateId)

    /**
     * Returns the list of colors currently reserved on [dateStr].
     *
     * A color is reserved on [dateStr] if:
     * 1. A PENDING task occurrence is scheduled for [dateStr] with that color.
     * 2. An active recurring template is scheduled to occur on [dateStr] with that color,
     *    and its occurrence on [dateStr] is not completed, skipped, or missed.
     */
    suspend fun getReservedColorsForDate(
        dateStr: String,
        excludeTemplateId: Long = -1L,
        excludeOccurrenceId: Long = -1L
    ): List<Int> {
        val reservedColors = mutableSetOf<Int>()
        val occurrencesOnDate = occurrenceDao.getForDate(dateStr)
        val existingTemplateIdsOnDate = mutableSetOf<Long>()

        for (occ in occurrencesOnDate) {
            existingTemplateIdsOnDate.add(occ.templateId)
            if (occ.id == excludeOccurrenceId || occ.templateId == excludeTemplateId) {
                continue
            }
            if (occ.status == OccurrenceStatus.PENDING) {
                reservedColors.add(occ.color)
            }
        }

        val activeTemplates = templateDao.getActiveTemplates()
        for (tmpl in activeTemplates) {
            if (tmpl.id == excludeTemplateId || tmpl.recurrenceType == RecurrenceType.ONE_TIME) {
                continue
            }
            if (tmpl.id in existingTemplateIdsOnDate) {
                continue
            }
            if (RecurrenceEngine.shouldOccurOnDate(tmpl, dateStr)) {
                reservedColors.add(tmpl.color)
            }
        }

        return reservedColors.toList()
    }

    /**
     * Real-time reactive Flow of reserved colors on [dateStr].
     * Automatically updates when tasks are completed, skipped, missed, rescheduled, or deleted.
     */
    fun observeReservedColorsForDate(
        dateStr: String,
        excludeTemplateId: Long = -1L,
        excludeOccurrenceId: Long = -1L
    ): Flow<List<Int>> = combine(
        occurrenceDao.observeForDate(dateStr),
        templateDao.observeActiveTemplates()
    ) { occurrencesOnDate, activeTemplates ->
        val reservedColors = mutableSetOf<Int>()
        val existingTemplateIdsOnDate = mutableSetOf<Long>()

        for (occ in occurrencesOnDate) {
            existingTemplateIdsOnDate.add(occ.templateId)
            if (occ.id == excludeOccurrenceId || occ.templateId == excludeTemplateId) {
                continue
            }
            if (occ.status == OccurrenceStatus.PENDING) {
                reservedColors.add(occ.color)
            }
        }

        for (tmpl in activeTemplates) {
            if (tmpl.id == excludeTemplateId || tmpl.recurrenceType == RecurrenceType.ONE_TIME) {
                continue
            }
            if (tmpl.id in existingTemplateIdsOnDate) {
                continue
            }
            if (RecurrenceEngine.shouldOccurOnDate(tmpl, dateStr)) {
                reservedColors.add(tmpl.color)
            }
        }

        reservedColors.toList()
    }

    /**
     * Checks if a recurring task with [color] conflicts with any other task on any of its scheduled dates.
     * Returns the conflicting date string, or null if no conflict exists.
     */
    suspend fun findRecurringColorConflict(
        color: Int,
        recurrenceType: RecurrenceType,
        startDate: String,
        weekdays: String = "",
        dayOfMonth: Int = 1,
        specificDates: String = "",
        excludeTemplateId: Long = -1L
    ): String? {
        val candidate = TaskTemplate(
            title = "",
            priority = Priority.MEDIUM,
            color = color,
            recurrenceType = recurrenceType,
            scheduledTimeMinutes = 0,
            startDate = startDate,
            weekdays = weekdays,
            dayOfMonth = dayOfMonth,
            specificDates = specificDates,
            isActive = true
        )

        // 1. For SPECIFIC_DATES, check each specified date
        if (recurrenceType == RecurrenceType.SPECIFIC_DATES) {
            val dates = specificDates.split(",").map { it.trim() }.filter { it.isNotBlank() }
            for (d in dates) {
                if (color in getReservedColorsForDate(d, excludeTemplateId = excludeTemplateId)) {
                    return d
                }
            }
            return null
        }

        // 2. Check startDate first
        if (color in getReservedColorsForDate(startDate, excludeTemplateId = excludeTemplateId)) {
            return startDate
        }

        if (recurrenceType == RecurrenceType.ONE_TIME) {
            return null
        }

        // 3. Check against existing pending occurrences with the same color
        val pendingWithColor = occurrenceDao.getPendingOccurrencesWithColor(color, startDate)
        for (occ in pendingWithColor) {
            if (occ.templateId == excludeTemplateId) continue
            if (RecurrenceEngine.shouldOccurOnDate(candidate, occ.scheduledDate)) {
                return occ.scheduledDate
            }
        }

        // 4. Check against other active recurring templates with the same color
        val otherActiveWithColor = templateDao.getActiveTemplatesWithColor(color)
            .filter { it.id != excludeTemplateId && it.recurrenceType != RecurrenceType.ONE_TIME }

        if (otherActiveWithColor.isNotEmpty()) {
            val cal = Calendar.getInstance().apply {
                time = RecurrenceEngine.parseDate(startDate) ?: Date()
            }
            for (i in 0 until 365) {
                val d = RecurrenceEngine.formatDate(cal.time)
                if (RecurrenceEngine.shouldOccurOnDate(candidate, d)) {
                    for (other in otherActiveWithColor) {
                        if (RecurrenceEngine.shouldOccurOnDate(other, d)) {
                            val existingOcc = occurrenceDao.findByTemplateAndDate(other.id, d)
                            if (existingOcc == null || existingOcc.status == OccurrenceStatus.PENDING) {
                                return d
                            }
                        }
                    }
                }
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        return null
    }

    suspend fun getTemplateById(id: Long): TaskTemplate? = templateDao.getById(id)

    suspend fun getOccurrenceById(id: Long): TaskOccurrence? = occurrenceDao.findById(id)

    suspend fun hasTimeConflict(
        date: String,
        timeMinutes: Int,
        excludeOccurrenceId: Long = -1L
    ): Boolean = occurrenceDao.findConflict(date, timeMinutes, excludeOccurrenceId) != null

    suspend fun findConflict(
        date: String,
        timeMinutes: Int,
        excludeOccurrenceId: Long = -1L
    ): TaskOccurrence? = occurrenceDao.findConflict(date, timeMinutes, excludeOccurrenceId)

    // ---------------------------------------------------------------------------
    // Mutations — Add
    // ---------------------------------------------------------------------------

    suspend fun addOneTimeTask(
        title: String,
        date: String,
        timeMinutes: Int,
        priority: Priority,
        color: Int,
        ringtoneUri: String? = null
    ): Result<Unit> = runCatching {
        if (hasTimeConflict(date, timeMinutes)) error("A task already exists at this time on $date")
        if (color in getReservedColorsForDate(date)) {
            error("This color is already assigned for that date")
        }
        val templateId = templateDao.insert(
            TaskTemplate(
                title = title,
                priority = priority,
                color = color,
                recurrenceType = RecurrenceType.ONE_TIME,
                scheduledTimeMinutes = timeMinutes,
                startDate = date,
                ringtoneUri = ringtoneUri
            )
        )
        val occurrence = TaskOccurrence(
            templateId = templateId,
            title = title,
            priority = priority,
            color = color,
            scheduledDate = date,
            scheduledTimeMinutes = timeMinutes,
            ringtoneUri = ringtoneUri
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
        specificDates: String = "",
        ringtoneUri: String? = null
    ): Result<Unit> = runCatching {
        val today = todayString()
        if (startDate == today && hasTimeConflict(today, timeMinutes)) {
            error("A task already exists at this time today")
        }
        val conflictDate = findRecurringColorConflict(
            color = color,
            recurrenceType = recurrenceType,
            startDate = startDate,
            weekdays = weekdays,
            dayOfMonth = dayOfMonth,
            specificDates = specificDates,
            excludeTemplateId = -1L
        )
        if (conflictDate != null) {
            error("This color is already assigned for that date")
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
            specificDates = specificDates,
            ringtoneUri = ringtoneUri
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
                scheduledTimeMinutes = timeMinutes,
                ringtoneUri = ringtoneUri
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
        occurrenceDao.updateHourlyReminder(id, active = false, nextReminderMillis = null)
        AlarmHelper.cancelAlarm(context, id)
        AlarmHelper.cancelHourlyReminderAlarm(context, id)
        AlarmHelper.cancelHourlyReminderNotification(context, id)
    }

    suspend fun skipOccurrence(id: Long) {
        occurrenceDao.updateStatus(id, OccurrenceStatus.SKIPPED, null)
        occurrenceDao.updateSnooze(id, null)
        occurrenceDao.updateHourlyReminder(id, active = false, nextReminderMillis = null)
        AlarmHelper.cancelAlarm(context, id)
        AlarmHelper.cancelHourlyReminderAlarm(context, id)
        AlarmHelper.cancelHourlyReminderNotification(context, id)
    }

    suspend fun snoozeOccurrence(id: Long, minutes: Int = 10) {
        val snoozedUntil = System.currentTimeMillis() + (minutes * 60 * 1000L)
        occurrenceDao.updateSnooze(id, snoozedUntil)
        // Snoozing must not start reminders and cancels any pending reminder sequence
        occurrenceDao.updateHourlyReminder(id, active = false, nextReminderMillis = null)
        AlarmHelper.cancelHourlyReminderAlarm(context, id)
        AlarmHelper.cancelHourlyReminderNotification(context, id)
        val occ = occurrenceDao.findById(id)
        if (occ != null) {
            AlarmHelper.scheduleAlarm(context, occ.copy(snoozedUntilMillis = snoozedUntil, hourlyReminderActive = false, nextReminderMillis = null))
        }
    }

    suspend fun clearSnooze(id: Long) {
        occurrenceDao.updateSnooze(id, null)
    }

    suspend fun markMissed(id: Long) {
        occurrenceDao.updateStatus(id, OccurrenceStatus.MISSED, null)
        occurrenceDao.updateSnooze(id, null)
        occurrenceDao.updateHourlyReminder(id, active = false, nextReminderMillis = null)
        AlarmHelper.cancelAlarm(context, id)
        AlarmHelper.cancelHourlyReminderAlarm(context, id)
        AlarmHelper.cancelHourlyReminderNotification(context, id)
    }

    /**
     * Reschedules a single task occurrence to a new date and time.
     * Validates that the target date/time is in the future and has no conflicts.
     * Updates only this occurrence snapshot; recurring series templates remain unmodified.
     */
    suspend fun rescheduleOccurrence(
        occurrenceId: Long,
        newDate: String,
        newTimeMinutes: Int
    ): Result<Unit> = runCatching {
        if (newTimeMinutes !in 0..1439) {
            error("Invalid time")
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val parsedDate = sdf.parse(newDate) ?: error("Invalid date format: $newDate")
        val cal = java.util.Calendar.getInstance().apply {
            time = parsedDate
            set(java.util.Calendar.HOUR_OF_DAY, newTimeMinutes / 60)
            set(java.util.Calendar.MINUTE, newTimeMinutes % 60)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val targetMillis = cal.timeInMillis
        if (targetMillis <= System.currentTimeMillis()) {
            error("Cannot reschedule to a past time. Please select a future time.")
        }

        val conflict = findConflict(newDate, newTimeMinutes, occurrenceId)
        if (conflict != null) {
            val formattedTime = com.example.doora.ui.components.formatTime(newTimeMinutes)
            error("A task (\"${conflict.title}\") already exists at $formattedTime on $newDate")
        }

        val occurrence = occurrenceDao.findById(occurrenceId) ?: error("Task occurrence not found")

        val isSameDate = occurrence.scheduledDate == newDate
        val reservedOnNewDate = getReservedColorsForDate(
            newDate,
            excludeTemplateId = if (isSameDate) occurrence.templateId else -1L,
            excludeOccurrenceId = if (isSameDate) occurrenceId else -1L
        )
        if (occurrence.color in reservedOnNewDate) {
            error("This color is already assigned for that date")
        }

        val updated = occurrence.copy(
            scheduledDate = newDate,
            scheduledTimeMinutes = newTimeMinutes,
            snoozedUntilMillis = null,
            status = OccurrenceStatus.PENDING,
            hourlyReminderActive = false,
            nextReminderMillis = null
        )
        occurrenceDao.update(updated)

        // Cancel previous alarm, reminder alarm, and reminder notification
        AlarmHelper.cancelAlarm(context, occurrenceId)
        AlarmHelper.cancelHourlyReminderAlarm(context, occurrenceId)
        AlarmHelper.cancelHourlyReminderNotification(context, occurrenceId)
        AlarmHelper.scheduleAlarm(context, updated)
    }

    // ---------------------------------------------------------------------------
    // Hourly Completion Reminders
    // ---------------------------------------------------------------------------

    /**
     * Called when an alarm is dismissed without completing the task.
     * Schedules the first follow-up notification for exactly 1 hour after dismissal.
     */
    suspend fun startHourlyReminder(occurrenceId: Long) {
        val occ = occurrenceDao.findById(occurrenceId) ?: return
        if (occ.status != OccurrenceStatus.PENDING) return
        val today = todayString()
        if (occ.scheduledDate < today) {
            markMissed(occurrenceId)
            return
        }

        val firstReminderTime = System.currentTimeMillis() + 60 * 60 * 1000L
        occurrenceDao.updateHourlyReminder(occurrenceId, active = true, nextReminderMillis = firstReminderTime)
        AlarmHelper.scheduleHourlyReminderAlarm(context, occurrenceId, firstReminderTime)
    }

    /**
     * Triggered by HourlyReminderReceiver when an hourly reminder is due.
     * Posts the notification and schedules the next reminder 1 hour later.
     */
    suspend fun triggerHourlyReminder(occurrenceId: Long) {
        val occ = occurrenceDao.findById(occurrenceId) ?: return
        if (occ.status != OccurrenceStatus.PENDING || !occ.hourlyReminderActive) return

        val today = todayString()
        if (occ.scheduledDate < today) {
            markMissed(occurrenceId)
            return
        }

        // 1. Post notification
        showHourlyReminderNotification(occ)

        // 2. Schedule next hourly reminder for 1 hour later
        val nextTrigger = System.currentTimeMillis() + 60 * 60 * 1000L
        occurrenceDao.updateHourlyReminder(occurrenceId, active = true, nextReminderMillis = nextTrigger)
        AlarmHelper.scheduleHourlyReminderAlarm(context, occurrenceId, nextTrigger)
    }

    private fun showHourlyReminderNotification(occurrence: TaskOccurrence) {
        val markIntent = Intent(context, AlarmActionReceiver::class.java).apply {
            action = AlarmHelper.ACTION_MARK_COMPLETED
            putExtra(AlarmHelper.EXTRA_OCCURRENCE_ID, occurrence.id)
        }
        val markPendingIntent = PendingIntent.getBroadcast(
            context,
            occurrence.id.toInt() + 600000,
            markIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val noIntent = Intent(context, AlarmActionReceiver::class.java).apply {
            action = AlarmHelper.ACTION_REMINDER_NO
            putExtra(AlarmHelper.EXTRA_OCCURRENCE_ID, occurrence.id)
        }
        val noPendingIntent = PendingIntent.getBroadcast(
            context,
            occurrence.id.toInt() + 700000,
            noIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(context, com.example.doora.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            occurrence.id.toInt() + 800000,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = androidx.core.app.NotificationCompat.Builder(context, AlarmHelper.REMINDER_CHANNEL_ID)
            .setSmallIcon(com.example.doora.R.mipmap.ic_launcher)
            .setContentTitle(occurrence.title)
            .setContentText("Task Completed ? Mark it here !!")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .setContentIntent(openAppPendingIntent)
            .addAction(0, "mark it", markPendingIntent)
            .addAction(0, "no", noPendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(occurrence.id.toInt() + 500000, notification)
    }

    /**
     * Cancels any active hourly reminder for the given occurrence id.
     */
    suspend fun cancelHourlyReminder(occurrenceId: Long) {
        occurrenceDao.updateHourlyReminder(occurrenceId, active = false, nextReminderMillis = null)
        AlarmHelper.cancelHourlyReminderAlarm(context, occurrenceId)
        AlarmHelper.cancelHourlyReminderNotification(context, occurrenceId)
    }

    /**
     * Reconciles scheduled reminders against Room data on app launch and reboot.
     */
    suspend fun reconcileActiveReminders() {
        val today = todayString()
        val now = System.currentTimeMillis()
        val activeReminders = occurrenceDao.getActiveReminderOccurrences()

        activeReminders.forEach { occ ->
            if (occ.status != OccurrenceStatus.PENDING || occ.scheduledDate < today) {
                if (occ.status == OccurrenceStatus.PENDING && occ.scheduledDate < today) {
                    occurrenceDao.updateStatus(occ.id, OccurrenceStatus.MISSED, null)
                }
                occurrenceDao.updateHourlyReminder(occ.id, active = false, nextReminderMillis = null)
                AlarmHelper.cancelHourlyReminderAlarm(context, occ.id)
                AlarmHelper.cancelHourlyReminderNotification(context, occ.id)
            } else {
                val targetMillis = occ.nextReminderMillis
                if (targetMillis == null || targetMillis <= now) {
                    triggerHourlyReminder(occ.id)
                } else {
                    AlarmHelper.scheduleHourlyReminderAlarm(context, occ.id, targetMillis)
                }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Mutations — Delete
    // ---------------------------------------------------------------------------

    /** Deletes single occurrence. If it belongs to a one-time task, deactivates the template to release its color. */
    suspend fun deleteOccurrence(occurrence: TaskOccurrence) {
        AlarmHelper.cancelAlarm(context, occurrence.id)
        AlarmHelper.cancelHourlyReminderAlarm(context, occurrence.id)
        AlarmHelper.cancelHourlyReminderNotification(context, occurrence.id)
        occurrenceDao.deleteById(occurrence.id)
        val template = templateDao.getById(occurrence.templateId)
        if (template != null && template.recurrenceType == RecurrenceType.ONE_TIME) {
            templateDao.softDelete(occurrence.templateId)
        }
    }

    suspend fun isRecurring(templateId: Long): Boolean {
        val template = templateDao.getById(templateId)
        return template != null && template.recurrenceType != RecurrenceType.ONE_TIME
    }

    /**
     * Deletes the recurring template and its future pending occurrences.
     * Historical past occurrences are preserved as required.
     */
    suspend fun deleteRecurringTemplate(templateId: Long) {
        val today = todayString()
        occurrenceDao.findByTemplateAndDate(templateId, today)?.let {
            AlarmHelper.cancelAlarm(context, it.id)
            AlarmHelper.cancelHourlyReminderAlarm(context, it.id)
            AlarmHelper.cancelHourlyReminderNotification(context, it.id)
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
        color: Int,
        ringtoneUri: String? = occurrence.ringtoneUri
    ): Result<Unit> = runCatching {
        if (hasTimeConflict(occurrence.scheduledDate, timeMinutes, occurrence.id)) {
            error("A task already exists at this time on ${occurrence.scheduledDate}")
        }
        val reservedColors = getReservedColorsForDate(
            occurrence.scheduledDate,
            excludeTemplateId = occurrence.templateId,
            excludeOccurrenceId = occurrence.id
        )
        if (color in reservedColors) {
            error("This color is already assigned for that date")
        }
        val updated = occurrence.copy(
            title = title,
            scheduledTimeMinutes = timeMinutes,
            priority = priority,
            color = color,
            ringtoneUri = ringtoneUri
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
        specificDates: String = "",
        ringtoneUri: String? = template.ringtoneUri
    ): Result<Unit> = runCatching {
        val today = todayString()
        val currentOcc = occurrenceDao.findByTemplateAndDate(template.id, today)
        if (currentOcc != null && hasTimeConflict(today, timeMinutes, currentOcc.id)) {
            error("A task already exists at this time today")
        }

        val conflictDate = findRecurringColorConflict(
            color = color,
            recurrenceType = recurrenceType,
            startDate = template.startDate,
            weekdays = weekdays,
            dayOfMonth = dayOfMonth,
            specificDates = specificDates,
            excludeTemplateId = template.id
        )
        if (conflictDate != null) {
            error("This color is already assigned for that date")
        }

        val updatedTemplate = template.copy(
            title = title,
            priority = priority,
            color = color,
            recurrenceType = recurrenceType,
            scheduledTimeMinutes = timeMinutes,
            weekdays = weekdays,
            dayOfMonth = dayOfMonth,
            specificDates = specificDates,
            ringtoneUri = ringtoneUri
        )
        templateDao.update(updatedTemplate)

        // Update today's occurrence if still pending
        if (currentOcc != null && currentOcc.status == OccurrenceStatus.PENDING) {
            val updatedOcc = currentOcc.copy(
                title = title,
                priority = priority,
                color = color,
                scheduledTimeMinutes = timeMinutes,
                ringtoneUri = ringtoneUri
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
