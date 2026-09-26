package com.example.doora

import com.example.doora.data.db.entity.TaskTemplate
import com.example.doora.data.engine.RecurrenceEngine
import com.example.doora.data.model.Priority
import com.example.doora.data.model.RecurrenceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RecurrenceEngineTest {

    private fun createTemplate(
        type: RecurrenceType,
        startDate: String = "2026-09-01",
        weekdays: String = "",
        dayOfMonth: Int = 1,
        specificDates: String = ""
    ): TaskTemplate {
        return TaskTemplate(
            title = "Test",
            priority = Priority.MEDIUM,
            color = 0,
            recurrenceType = type,
            scheduledTimeMinutes = 540,
            startDate = startDate,
            weekdays = weekdays,
            dayOfMonth = dayOfMonth,
            specificDates = specificDates
        )
    }

    @Test
    fun testOneTimeRecurrence() {
        val template = createTemplate(RecurrenceType.ONE_TIME, startDate = "2026-09-26")
        assertTrue(RecurrenceEngine.shouldOccurOnDate(template, "2026-09-26"))
        assertFalse(RecurrenceEngine.shouldOccurOnDate(template, "2026-09-27"))
        assertFalse(RecurrenceEngine.shouldOccurOnDate(template, "2026-09-25"))
    }

    @Test
    fun testDailyRecurrence() {
        val template = createTemplate(RecurrenceType.DAILY, startDate = "2026-09-20")
        assertFalse(RecurrenceEngine.shouldOccurOnDate(template, "2026-09-19"))
        assertTrue(RecurrenceEngine.shouldOccurOnDate(template, "2026-09-20"))
        assertTrue(RecurrenceEngine.shouldOccurOnDate(template, "2026-09-26"))
        assertTrue(RecurrenceEngine.shouldOccurOnDate(template, "2026-10-01"))
    }

    @Test
    fun testWeekdaysRecurrence() {
        val template = createTemplate(RecurrenceType.WEEKDAYS, startDate = "2026-09-20")
        // 2026-09-25 is Friday, 2026-09-26 is Saturday, 2026-09-27 is Sunday, 2026-09-28 is Monday
        assertTrue(RecurrenceEngine.shouldOccurOnDate(template, "2026-09-25")) // Friday
        assertFalse(RecurrenceEngine.shouldOccurOnDate(template, "2026-09-26")) // Saturday
        assertFalse(RecurrenceEngine.shouldOccurOnDate(template, "2026-09-27")) // Sunday
        assertTrue(RecurrenceEngine.shouldOccurOnDate(template, "2026-09-28")) // Monday
    }

    @Test
    fun testSpecificWeekdaysRecurrence() {
        // Monday (2) and Wednesday (4)
        val template = createTemplate(
            RecurrenceType.SPECIFIC_WEEKDAYS,
            startDate = "2026-09-01",
            weekdays = "${Calendar.MONDAY},${Calendar.WEDNESDAY}"
        )
        // 2026-09-21 is Monday, 2026-09-22 is Tuesday, 2026-09-23 is Wednesday
        assertTrue(RecurrenceEngine.shouldOccurOnDate(template, "2026-09-21")) // Mon
        assertFalse(RecurrenceEngine.shouldOccurOnDate(template, "2026-09-22")) // Tue
        assertTrue(RecurrenceEngine.shouldOccurOnDate(template, "2026-09-23")) // Wed
    }

    @Test
    fun testMonthlyRecurrence() {
        val template = createTemplate(
            RecurrenceType.MONTHLY,
            startDate = "2026-01-01",
            dayOfMonth = 15
        )
        assertTrue(RecurrenceEngine.shouldOccurOnDate(template, "2026-09-15"))
        assertFalse(RecurrenceEngine.shouldOccurOnDate(template, "2026-09-16"))
        assertTrue(RecurrenceEngine.shouldOccurOnDate(template, "2026-10-15"))
    }

    @Test
    fun testSpecificDatesRecurrence() {
        val template = createTemplate(
            RecurrenceType.SPECIFIC_DATES,
            specificDates = "2026-09-26,2026-10-05,2026-11-12"
        )
        assertTrue(RecurrenceEngine.shouldOccurOnDate(template, "2026-09-26"))
        assertTrue(RecurrenceEngine.shouldOccurOnDate(template, "2026-10-05"))
        assertFalse(RecurrenceEngine.shouldOccurOnDate(template, "2026-10-06"))
    }
}
