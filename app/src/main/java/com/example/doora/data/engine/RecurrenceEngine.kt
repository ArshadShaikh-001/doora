package com.example.doora.data.engine

import com.example.doora.data.db.entity.TaskTemplate
import com.example.doora.data.model.RecurrenceType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object RecurrenceEngine {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun parseDate(dateStr: String): Date? {
        return runCatching { dateFormat.parse(dateStr) }.getOrNull()
    }

    fun formatDate(date: Date): String = dateFormat.format(date)

    fun getTodayString(): String = dateFormat.format(Date())

    /**
     * Determines if a [TaskTemplate] applies to a given [dateStr] ("yyyy-MM-dd").
     */
    fun shouldOccurOnDate(template: TaskTemplate, dateStr: String): Boolean {
        if (!template.isActive) return false
        val date = parseDate(dateStr) ?: return false

        val cal = Calendar.getInstance().apply { time = date }

        if (template.recurrenceType == RecurrenceType.SPECIFIC_DATES) {
            val dates = template.specificDates.split(",").map { it.trim() }
            return dateStr in dates
        }

        // For other types, respect startDate if present
        if (template.startDate.isNotBlank() && dateStr < template.startDate) {
            return false
        }

        return when (template.recurrenceType) {
            RecurrenceType.ONE_TIME -> {
                dateStr == template.startDate
            }
            RecurrenceType.DAILY -> {
                true
            }
            RecurrenceType.WEEKDAYS -> {
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                dayOfWeek != Calendar.SATURDAY && dayOfWeek != Calendar.SUNDAY
            }
            RecurrenceType.SPECIFIC_WEEKDAYS -> {
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val allowedDays = template.weekdays.split(",").mapNotNull { it.trim().toIntOrNull() }
                dayOfWeek in allowedDays
            }
            RecurrenceType.WEEKLY -> {
                val startDate = parseDate(template.startDate) ?: date
                val startCal = Calendar.getInstance().apply { time = startDate }
                cal.get(Calendar.DAY_OF_WEEK) == startCal.get(Calendar.DAY_OF_WEEK)
            }
            RecurrenceType.MONTHLY -> {
                val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
                val maxDayInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val targetDay = template.dayOfMonth.coerceIn(1, 31)
                dayOfMonth == targetDay || (targetDay > maxDayInMonth && dayOfMonth == maxDayInMonth)
            }
            RecurrenceType.SPECIFIC_DATES -> {
                false
            }
        }
    }
}
