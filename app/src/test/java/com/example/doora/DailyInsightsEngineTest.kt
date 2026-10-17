package com.example.doora

import com.example.doora.data.db.entity.TaskOccurrence
import com.example.doora.data.db.entity.TaskTemplate
import com.example.doora.data.engine.StreakAndStatsEngine
import com.example.doora.data.model.OccurrenceStatus
import com.example.doora.data.model.Priority
import com.example.doora.data.model.RecurrenceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyInsightsEngineTest {

    private val RED = 0xFFEF5350.toInt()
    private val BLUE = 0xFF42A5F5.toInt()
    private val GREEN = 0xFF66BB6A.toInt()

    private fun dailyTemplate(
        id: Long,
        title: String,
        startDate: String = "2026-10-01",
        color: Int = RED,
        isActive: Boolean = true
    ): TaskTemplate {
        return TaskTemplate(
            id = id,
            title = title,
            priority = Priority.HIGH,
            color = color,
            recurrenceType = RecurrenceType.DAILY,
            scheduledTimeMinutes = 540,
            startDate = startDate,
            isActive = isActive
        )
    }

    private fun nonDailyTemplate(
        id: Long,
        title: String,
        type: RecurrenceType = RecurrenceType.ONE_TIME,
        color: Int = BLUE
    ): TaskTemplate {
        return TaskTemplate(
            id = id,
            title = title,
            priority = Priority.MEDIUM,
            color = color,
            recurrenceType = type,
            scheduledTimeMinutes = 600,
            startDate = "2026-10-15",
            isActive = true
        )
    }

    private fun occurrence(
        id: Long,
        templateId: Long,
        title: String,
        date: String,
        status: OccurrenceStatus,
        color: Int = RED,
        timeMinutes: Int = 540
    ): TaskOccurrence {
        return TaskOccurrence(
            id = id,
            templateId = templateId,
            title = title,
            priority = Priority.HIGH,
            color = color,
            scheduledDate = date,
            scheduledTimeMinutes = timeMinutes,
            status = status
        )
    }

    @Test
    fun testRequirement1_OnlyDailyTasksIncluded_NonDailyExcluded() {
        val templates = listOf(
            dailyTemplate(1L, "Daily DSA", "2026-10-01", color = RED),
            nonDailyTemplate(2L, "Weekly Review", RecurrenceType.WEEKLY, color = BLUE),
            nonDailyTemplate(3L, "Doctor Appointment", RecurrenceType.ONE_TIME, color = GREEN)
        )

        val occurrences = listOf(
            occurrence(101L, 1L, "Daily DSA", "2026-10-17", OccurrenceStatus.COMPLETED, color = RED),
            occurrence(201L, 2L, "Weekly Review", "2026-10-17", OccurrenceStatus.COMPLETED, color = BLUE),
            occurrence(301L, 3L, "Doctor Appointment", "2026-10-17", OccurrenceStatus.COMPLETED, color = GREEN)
        )

        val insights = StreakAndStatsEngine.calculateDailyInsights(
            selectedDateStr = "2026-10-17",
            todayDateStr = "2026-10-17",
            allTemplates = templates,
            allOccurrences = occurrences
        )

        // Only Daily DSA should appear in the daily graph
        assertEquals(1, insights.selectedDateMetrics.tasks.size)
        assertEquals("Daily DSA", insights.selectedDateMetrics.tasks.first().title)
        assertEquals(1, insights.totalCompletedAllTime)
    }

    @Test
    fun testRequirement2_AllFourStatusesRepresentedCorrectly() {
        val templates = listOf(
            dailyTemplate(1L, "DSA", "2026-10-17", color = RED),
            dailyTemplate(2L, "Exercise", "2026-10-17", color = BLUE),
            dailyTemplate(3L, "Reading", "2026-10-17", color = GREEN),
            dailyTemplate(4L, "Meditation", "2026-10-17", color = RED)
        )

        val occurrences = listOf(
            occurrence(101L, 1L, "DSA", "2026-10-17", OccurrenceStatus.COMPLETED, color = RED, timeMinutes = 480),
            occurrence(102L, 2L, "Exercise", "2026-10-17", OccurrenceStatus.PENDING, color = BLUE, timeMinutes = 540),
            occurrence(103L, 3L, "Reading", "2026-10-17", OccurrenceStatus.MISSED, color = GREEN, timeMinutes = 600),
            occurrence(104L, 4L, "Meditation", "2026-10-17", OccurrenceStatus.SKIPPED, color = RED, timeMinutes = 660)
        )

        val metrics = StreakAndStatsEngine.calculateDailyDateMetrics(
            selectedDateStr = "2026-10-17",
            todayDateStr = "2026-10-17",
            dailyTemplates = templates,
            dailyOccurrences = occurrences
        )

        assertEquals(4, metrics.tasks.size)
        assertEquals(1, metrics.completedCount)
        assertEquals(1, metrics.pendingCount)
        assertEquals(1, metrics.missedCount)
        assertEquals(1, metrics.skippedCount)

        // Denominator = completed (1) + pending (1) + missed (1) = 3 (skipped excluded)
        // Rate = 1/3 * 100 = 33.33%
        assertEquals(33.333332f, metrics.completionRate, 0.01f)
    }

    @Test
    fun testRequirement3And4_HistoricalColorsPreservedEvenIfTemplateColorChanges() {
        val template = dailyTemplate(1L, "DSA", "2026-10-10", color = GREEN) // Currently GREEN

        // Historical occurrence on Oct 10 was created when DSA was RED
        val historicalOcc = occurrence(101L, 1L, "DSA", "2026-10-10", OccurrenceStatus.COMPLETED, color = RED)

        val metrics = StreakAndStatsEngine.calculateDailyDateMetrics(
            selectedDateStr = "2026-10-10",
            todayDateStr = "2026-10-17",
            dailyTemplates = listOf(template),
            dailyOccurrences = listOf(historicalOcc)
        )

        assertEquals(1, metrics.tasks.size)
        assertEquals("Historical occurrence must preserve its assigned RED color", RED, metrics.tasks.first().color)
    }

    @Test
    fun testRequirement5_DateNavigationHelpers() {
        val today = "2026-10-17"
        val previousDay = StreakAndStatsEngine.getPreviousDateString(today)
        val nextDay = StreakAndStatsEngine.getNextDateString(today)

        assertEquals("2026-10-16", previousDay)
        assertEquals("2026-10-18", nextDay)

        val todayDisplay = StreakAndStatsEngine.formatDisplayDate(today, today)
        assertTrue(todayDisplay.startsWith("Today"))

        val yesterdayDisplay = StreakAndStatsEngine.formatDisplayDate(previousDay, today)
        assertTrue(yesterdayDisplay.startsWith("Yesterday"))

        val tomorrowDisplay = StreakAndStatsEngine.formatDisplayDate(nextDay, today)
        assertTrue(tomorrowDisplay.startsWith("Tomorrow"))
    }

    @Test
    fun testRequirement6_WeeklyAndMonthlyTrends() {
        val template = dailyTemplate(1L, "Workout", "2026-10-01", color = BLUE)

        // Completed 5 out of last 7 days (Oct 11 to Oct 17)
        val occurrences = listOf(
            occurrence(101L, 1L, "Workout", "2026-10-11", OccurrenceStatus.COMPLETED),
            occurrence(102L, 1L, "Workout", "2026-10-12", OccurrenceStatus.COMPLETED),
            occurrence(103L, 1L, "Workout", "2026-10-13", OccurrenceStatus.MISSED),
            occurrence(104L, 1L, "Workout", "2026-10-14", OccurrenceStatus.COMPLETED),
            occurrence(105L, 1L, "Workout", "2026-10-15", OccurrenceStatus.COMPLETED),
            occurrence(106L, 1L, "Workout", "2026-10-16", OccurrenceStatus.SKIPPED),
            occurrence(107L, 1L, "Workout", "2026-10-17", OccurrenceStatus.COMPLETED)
        )

        val weeklyTrend = StreakAndStatsEngine.calculateWeeklyTrend("2026-10-17", occurrences)
        assertEquals(7, weeklyTrend.size)

        val todayItem = weeklyTrend.find { it.isToday }!!
        assertEquals("2026-10-17", todayItem.dateStr)
        assertEquals(1, todayItem.completedCount)
        assertEquals(100f, todayItem.completionRate, 0.01f)

        val monthlyTrend = StreakAndStatsEngine.calculateMonthlyTrend("2026-10-17", occurrences)
        assertEquals(5, monthlyTrend.totalCompleted)
        assertEquals(6, monthlyTrend.totalEligible) // 6 non-skipped
        assertEquals(4, monthlyTrend.weeklyBuckets.size)
    }

    @Test
    fun testRequirement7_CreationDatesAndFutureDatesHandledProperly() {
        // Task created on Oct 15
        val template = dailyTemplate(1L, "DSA", "2026-10-15", color = RED)

        // 1. Prior date Oct 14: should NOT have any occurrences or count as missed
        val occurrences = listOf(
            occurrence(101L, 1L, "DSA", "2026-10-15", OccurrenceStatus.COMPLETED),
            occurrence(102L, 1L, "DSA", "2026-10-16", OccurrenceStatus.COMPLETED)
        )

        val oct14Metrics = StreakAndStatsEngine.calculateDailyDateMetrics(
            selectedDateStr = "2026-10-14",
            todayDateStr = "2026-10-16",
            dailyTemplates = listOf(template),
            dailyOccurrences = occurrences
        )
        assertTrue("No tasks on date before creation date", oct14Metrics.tasks.isEmpty())

        // 2. Future date Oct 18: should appear as PENDING, never completed or missed
        val oct18Metrics = StreakAndStatsEngine.calculateDailyDateMetrics(
            selectedDateStr = "2026-10-18",
            todayDateStr = "2026-10-16",
            dailyTemplates = listOf(template),
            dailyOccurrences = occurrences
        )
        assertEquals(1, oct18Metrics.tasks.size)
        assertEquals(OccurrenceStatus.PENDING, oct18Metrics.tasks.first().status)
        assertEquals(0f, oct18Metrics.completionRate, 0.01f)
    }

    @Test
    fun testRequirement8_StreaksConsistentWithExistingStreakEngine() {
        val template = dailyTemplate(1L, "DSA", "2026-10-10", color = RED)

        // Completed 5 consecutive days: Oct 10 to Oct 14, missed Oct 15, completed Oct 16 & 17
        val occurrences = listOf(
            occurrence(1L, 1L, "DSA", "2026-10-10", OccurrenceStatus.COMPLETED),
            occurrence(2L, 1L, "DSA", "2026-10-11", OccurrenceStatus.COMPLETED),
            occurrence(3L, 1L, "DSA", "2026-10-12", OccurrenceStatus.COMPLETED),
            occurrence(4L, 1L, "DSA", "2026-10-13", OccurrenceStatus.COMPLETED),
            occurrence(5L, 1L, "DSA", "2026-10-14", OccurrenceStatus.COMPLETED),
            occurrence(6L, 1L, "DSA", "2026-10-15", OccurrenceStatus.MISSED),
            occurrence(7L, 1L, "DSA", "2026-10-16", OccurrenceStatus.COMPLETED),
            occurrence(8L, 1L, "DSA", "2026-10-17", OccurrenceStatus.COMPLETED)
        )

        val directStreak = StreakAndStatsEngine.calculateDailyTaskStreak(template, occurrences, "2026-10-17")
        val insights = StreakAndStatsEngine.calculateDailyInsights(
            selectedDateStr = "2026-10-17",
            todayDateStr = "2026-10-17",
            allTemplates = listOf(template),
            allOccurrences = occurrences
        )

        assertEquals(directStreak.currentStreak, insights.bestCurrentStreak)
        assertEquals(directStreak.longestStreak, insights.longestStreakAllTime)
        assertEquals(2, insights.bestCurrentStreak)
        assertEquals(5, insights.longestStreakAllTime)
    }

    @Test
    fun testRequirement9_EmptyDatesHandledGracefully() {
        val insights = StreakAndStatsEngine.calculateDailyInsights(
            selectedDateStr = "2026-10-17",
            todayDateStr = "2026-10-17",
            allTemplates = emptyList(),
            allOccurrences = emptyList()
        )

        assertFalse(insights.hasAnyDailyTasks)
        assertEquals(0, insights.selectedDateMetrics.tasks.size)
        assertEquals(0f, insights.selectedDateMetrics.completionRate, 0.01f)
    }
}
