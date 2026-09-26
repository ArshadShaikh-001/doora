package com.example.doora

import com.example.doora.data.db.entity.TaskOccurrence
import com.example.doora.data.db.entity.TaskTemplate
import com.example.doora.data.engine.StatsTimeRange
import com.example.doora.data.engine.StreakAndStatsEngine
import com.example.doora.data.model.OccurrenceStatus
import com.example.doora.data.model.Priority
import com.example.doora.data.model.RecurrenceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakAndStatsEngineTest {

    private fun occ(
        id: Long,
        title: String,
        date: String,
        status: OccurrenceStatus,
        templateId: Long = 1L
    ): TaskOccurrence {
        return TaskOccurrence(
            id = id,
            templateId = templateId,
            title = title,
            priority = Priority.MEDIUM,
            color = 0,
            scheduledDate = date,
            scheduledTimeMinutes = 540,
            status = status
        )
    }

    private fun dailyTemplate(
        id: Long,
        title: String,
        startDate: String
    ): TaskTemplate {
        return TaskTemplate(
            id = id,
            title = title,
            priority = Priority.HIGH,
            color = 0,
            recurrenceType = RecurrenceType.DAILY,
            scheduledTimeMinutes = 540,
            startDate = startDate,
            isActive = true
        )
    }

    @Test
    fun testScenarioA_CreateDailyTaskOn25SepAndCompleteIt() {
        // Task "DSA" created on 25 September. If completed on 25 September: streak = 1.
        val template = dailyTemplate(1L, "DSA", "2026-09-25")
        val occurrences = listOf(
            occ(1L, "DSA", "2026-09-25", OccurrenceStatus.COMPLETED, templateId = 1L)
        )
        val streak = StreakAndStatsEngine.calculateDailyTaskStreak(template, occurrences, "2026-09-25")
        assertEquals(1, streak.currentStreak)
        assertEquals(1, streak.longestStreak)
        assertTrue(streak.isCompletedToday)
    }

    @Test
    fun testScenarioB_CreateDailyTaskOn25SepNotCompleted() {
        // Task created on 25 Sep, but not completed.
        val template = dailyTemplate(1L, "DSA", "2026-09-25")
        // On 25 Sep (today), pending:
        val occ25 = listOf(
            occ(1L, "DSA", "2026-09-25", OccurrenceStatus.PENDING, templateId = 1L)
        )
        val streakToday = StreakAndStatsEngine.calculateDailyTaskStreak(template, occ25, "2026-09-25")
        assertEquals(0, streakToday.currentStreak)
        assertEquals(0, streakToday.longestStreak)

        // On 26 Sep (next day), past 25 Sep occurrence becomes MISSED:
        val occ26 = listOf(
            occ(1L, "DSA", "2026-09-25", OccurrenceStatus.MISSED, templateId = 1L),
            occ(2L, "DSA", "2026-09-26", OccurrenceStatus.PENDING, templateId = 1L)
        )
        val streakNextDay = StreakAndStatsEngine.calculateDailyTaskStreak(template, occ26, "2026-09-26")
        assertEquals(0, streakNextDay.currentStreak)
        assertEquals(0, streakNextDay.longestStreak)
    }

    @Test
    fun testScenarioC_CreateDailyTaskOn25SepAndCompleteOn25_26_27() {
        // Complete on 25, 26, 27 Sep. Expected streak = 3.
        val template = dailyTemplate(1L, "DSA", "2026-09-25")
        val occurrences = listOf(
            occ(1L, "DSA", "2026-09-25", OccurrenceStatus.COMPLETED, templateId = 1L),
            occ(2L, "DSA", "2026-09-26", OccurrenceStatus.COMPLETED, templateId = 1L),
            occ(3L, "DSA", "2026-09-27", OccurrenceStatus.COMPLETED, templateId = 1L)
        )
        val streak = StreakAndStatsEngine.calculateDailyTaskStreak(template, occurrences, "2026-09-27")
        assertEquals(3, streak.currentStreak)
        assertEquals(3, streak.longestStreak)
        assertEquals(3, streak.totalCompleted)
    }

    @Test
    fun testScenarioD_DatesBeforeCreationDateDoNotParticipate() {
        // Template created on 25 Sep.
        val template = dailyTemplate(1L, "DSA", "2026-09-25")
        // Suppose there were past occurrences or missing dates on 20, 21, 22, 23, 24 Sep.
        val occurrences = listOf(
            occ(99L, "DSA", "2026-09-23", OccurrenceStatus.MISSED, templateId = 1L), // before start date
            occ(1L, "DSA", "2026-09-25", OccurrenceStatus.COMPLETED, templateId = 1L)
        )
        val streak = StreakAndStatsEngine.calculateDailyTaskStreak(template, occurrences, "2026-09-25")
        // Dates before 25 Sep MUST NOT break or participate in streak.
        assertEquals(1, streak.currentStreak)
        assertEquals(1, streak.longestStreak)
    }

    @Test
    fun testScenarioE_SkippedInterruptsConsecutiveCompletedStreak() {
        // 25 Sep: Completed
        // 26 Sep: Skipped (does NOT increment streak, interrupts consecutive completed streak)
        // 27 Sep: Completed
        val template = dailyTemplate(1L, "DSA", "2026-09-25")
        val occurrences = listOf(
            occ(1L, "DSA", "2026-09-25", OccurrenceStatus.COMPLETED, templateId = 1L),
            occ(2L, "DSA", "2026-09-26", OccurrenceStatus.SKIPPED, templateId = 1L),
            occ(3L, "DSA", "2026-09-27", OccurrenceStatus.COMPLETED, templateId = 1L)
        )
        val streak = StreakAndStatsEngine.calculateDailyTaskStreak(template, occurrences, "2026-09-27")
        // On 27 Sep, current streak restarted from 27 Sep completion -> 1 day.
        assertEquals(1, streak.currentStreak)
        // Longest streak was 1 (from 25 Sep).
        assertEquals(1, streak.longestStreak)
    }

    @Test
    fun testScenarioF_NonDailyTasksDoNotAppearInStreaks() {
        val daily = dailyTemplate(1L, "DSA", "2026-09-25")
        val weekly = TaskTemplate(
            id = 2L,
            title = "Laundry",
            priority = Priority.LOW,
            color = 0,
            recurrenceType = RecurrenceType.WEEKLY,
            scheduledTimeMinutes = 600,
            startDate = "2026-09-20",
            isActive = true
        )
        val oneTime = TaskTemplate(
            id = 3L,
            title = "Doctor",
            priority = Priority.HIGH,
            color = 0,
            recurrenceType = RecurrenceType.ONE_TIME,
            scheduledTimeMinutes = 600,
            startDate = "2026-09-26",
            isActive = true
        )

        val streaks = StreakAndStatsEngine.calculateAllDailyStreaks(
            dailyTemplates = listOf(daily, weekly, oneTime),
            allOccurrences = emptyList(),
            todayDateStr = "2026-09-26"
        )

        assertEquals(1, streaks.size)
        assertEquals("DSA", streaks[0].title)
    }

    @Test
    fun testScenarioG_IndependentStreaksForDifferentDailyTasks() {
        val dsa = dailyTemplate(1L, "DSA", "2026-09-15")
        val reading = dailyTemplate(2L, "Reading", "2026-09-20")
        val typing = dailyTemplate(3L, "Typing", "2026-09-23")

        val occurrences = mutableListOf<TaskOccurrence>()
        // DSA completed for 12 days (15 to 26 Sep)
        for (day in 15..26) {
            occurrences.add(occ(day.toLong(), "DSA", "2026-09-$day", OccurrenceStatus.COMPLETED, templateId = 1L))
        }
        // Reading completed for 7 days (20 to 26 Sep)
        for (day in 20..26) {
            occurrences.add(occ((100 + day).toLong(), "Reading", "2026-09-$day", OccurrenceStatus.COMPLETED, templateId = 2L))
        }
        // Typing completed for 4 days (23 to 26 Sep)
        for (day in 23..26) {
            occurrences.add(occ((200 + day).toLong(), "Typing", "2026-09-$day", OccurrenceStatus.COMPLETED, templateId = 3L))
        }

        val streaks = StreakAndStatsEngine.calculateAllDailyStreaks(
            dailyTemplates = listOf(dsa, reading, typing),
            allOccurrences = occurrences,
            todayDateStr = "2026-09-26"
        )

        assertEquals(3, streaks.size)
        val dsaStreak = streaks.first { it.title == "DSA" }
        val readingStreak = streaks.first { it.title == "Reading" }
        val typingStreak = streaks.first { it.title == "Typing" }

        assertEquals(12, dsaStreak.currentStreak)
        assertEquals(7, readingStreak.currentStreak)
        assertEquals(4, typingStreak.currentStreak)
    }

    @Test
    fun testStatisticsCalculation() {
        val list = listOf(
            occ(1, "Work", "2026-09-26", OccurrenceStatus.COMPLETED),
            occ(2, "Gym", "2026-09-26", OccurrenceStatus.PENDING),
            occ(3, "Read", "2026-09-25", OccurrenceStatus.COMPLETED),
            occ(4, "Meditate", "2026-09-25", OccurrenceStatus.MISSED)
        )
        val stats = StreakAndStatsEngine.calculateStatistics(list, StatsTimeRange.ALL_TIME, "2026-09-26")
        assertEquals(1, stats.tasksCompletedToday)
        assertEquals(2, stats.totalTasksToday)
        assertEquals(50f, stats.dailyCompletionRate, 0.1f)
        assertEquals(2, stats.totalTasksCompleted)
        assertEquals(1, stats.totalTasksMissed)
    }
}
