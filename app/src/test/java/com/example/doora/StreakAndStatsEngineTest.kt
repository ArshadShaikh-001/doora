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

    @Test
    fun testInsights_OnlyDailyTasksContribute_NonDailyExcluded() {
        val daily = dailyTemplate(1L, "Exercise", "2026-09-20")
        val oneTime = TaskTemplate(
            id = 2L,
            title = "Doctor",
            priority = Priority.HIGH,
            color = 0,
            recurrenceType = RecurrenceType.ONE_TIME,
            scheduledTimeMinutes = 600,
            startDate = "2026-09-26",
            isActive = true
        )
        val weekly = TaskTemplate(
            id = 3L,
            title = "Laundry",
            priority = Priority.LOW,
            color = 0,
            recurrenceType = RecurrenceType.WEEKLY,
            scheduledTimeMinutes = 600,
            startDate = "2026-09-20",
            isActive = true
        )
        val monthly = TaskTemplate(
            id = 4L,
            title = "Rent",
            priority = Priority.HIGH,
            color = 0,
            recurrenceType = RecurrenceType.MONTHLY,
            scheduledTimeMinutes = 600,
            startDate = "2026-09-01",
            isActive = true
        )

        val templates = listOf(daily, oneTime, weekly, monthly)
        val occurrences = listOf(
            occ(1L, "Exercise", "2026-09-26", OccurrenceStatus.COMPLETED, templateId = 1L),
            occ(2L, "Doctor", "2026-09-26", OccurrenceStatus.COMPLETED, templateId = 2L),
            occ(3L, "Laundry", "2026-09-26", OccurrenceStatus.COMPLETED, templateId = 3L),
            occ(4L, "Rent", "2026-09-26", OccurrenceStatus.COMPLETED, templateId = 4L)
        )

        val stats = StreakAndStatsEngine.calculateStatistics(
            allOccurrences = occurrences,
            templates = templates,
            selectedRange = StatsTimeRange.TODAY,
            todayDateStr = "2026-09-26"
        )

        // Only the daily task "Exercise" should be counted
        assertEquals(1, stats.tasksCompletedToday)
        assertEquals(1, stats.totalTasksToday)
        assertEquals(100f, stats.dailyCompletionRate, 0.1f)
        assertEquals(1, stats.totalTasksCompleted)
        assertEquals("Exercise", stats.mostConsistentTaskTitle)
        assertEquals(mapOf("2026-09-26" to 1), stats.completedByDate)
    }

    @Test
    fun testInsights_OccurrencesBeforeStartDateExcluded() {
        val coding = dailyTemplate(1L, "Coding", "2026-09-24")
        val occurrences = listOf(
            occ(1L, "Coding", "2026-09-22", OccurrenceStatus.MISSED, templateId = 1L), // before start date!
            occ(2L, "Coding", "2026-09-23", OccurrenceStatus.MISSED, templateId = 1L), // before start date!
            occ(3L, "Coding", "2026-09-24", OccurrenceStatus.COMPLETED, templateId = 1L),
            occ(4L, "Coding", "2026-09-25", OccurrenceStatus.COMPLETED, templateId = 1L),
            occ(5L, "Coding", "2026-09-26", OccurrenceStatus.COMPLETED, templateId = 1L)
        )

        val stats = StreakAndStatsEngine.calculateStatistics(
            allOccurrences = occurrences,
            templates = listOf(coding),
            selectedRange = StatsTimeRange.ALL_TIME,
            todayDateStr = "2026-09-26"
        )

        // The 2 missed occurrences before start date must be ignored completely
        assertEquals(0, stats.totalTasksMissed)
        assertEquals(3, stats.totalTasksCompleted)
        assertEquals(3, stats.currentStreak)
        assertEquals(3, stats.longestStreak)
    }

    @Test
    fun testInsights_StatusCountsRespected() {
        val t1 = dailyTemplate(1L, "Study", "2026-09-20")
        val t2 = dailyTemplate(2L, "Read", "2026-09-20")
        val t3 = dailyTemplate(3L, "Meditate", "2026-09-20")
        val t4 = dailyTemplate(4L, "Walk", "2026-09-20")

        val occurrences = listOf(
            occ(1L, "Study", "2026-09-26", OccurrenceStatus.COMPLETED, templateId = 1L),
            occ(2L, "Read", "2026-09-26", OccurrenceStatus.MISSED, templateId = 2L),
            occ(3L, "Meditate", "2026-09-26", OccurrenceStatus.SKIPPED, templateId = 3L),
            occ(4L, "Walk", "2026-09-26", OccurrenceStatus.PENDING, templateId = 4L)
        )

        val stats = StreakAndStatsEngine.calculateStatistics(
            allOccurrences = occurrences,
            templates = listOf(t1, t2, t3, t4),
            selectedRange = StatsTimeRange.TODAY,
            todayDateStr = "2026-09-26"
        )

        assertEquals(1, stats.tasksCompletedToday)
        // Total tasks today excludes SKIPPED: Completed(1) + Missed(1) + Pending(1) = 3
        assertEquals(3, stats.totalTasksToday)
        assertEquals(33.33f, stats.dailyCompletionRate, 0.1f)
        assertEquals(1, stats.totalTasksCompleted)
        assertEquals(1, stats.totalTasksMissed)
        assertEquals(1, stats.totalTasksSkipped)
        assertEquals("Read", stats.mostFrequentMissedTaskTitle)
    }

    @Test
    fun testInsights_DeactivatedDailyTask_PreservesPastOccurrences() {
        // Soft-deleted daily task
        val oldHabit = TaskTemplate(
            id = 1L,
            title = "Old Habit",
            priority = Priority.MEDIUM,
            color = 0,
            recurrenceType = RecurrenceType.DAILY,
            scheduledTimeMinutes = 540,
            startDate = "2026-09-10",
            isActive = false
        )

        val occurrences = listOf(
            occ(1L, "Old Habit", "2026-09-15", OccurrenceStatus.COMPLETED, templateId = 1L)
        )

        val stats = StreakAndStatsEngine.calculateStatistics(
            allOccurrences = occurrences,
            templates = listOf(oldHabit),
            selectedRange = StatsTimeRange.ALL_TIME,
            todayDateStr = "2026-09-26"
        )

        assertEquals(1, stats.totalTasksCompleted)
        assertEquals("Old Habit", stats.mostConsistentTaskTitle)
    }

    @Test
    fun testInsights_StreakConsistencyWithTaskSpecificModel() {
        val habitA = dailyTemplate(1L, "Habit A", "2026-09-20")
        val habitB = dailyTemplate(2L, "Habit B", "2026-09-25")
        val oneTime = TaskTemplate(
            id = 3L,
            title = "One Time",
            priority = Priority.HIGH,
            color = 0,
            recurrenceType = RecurrenceType.ONE_TIME,
            scheduledTimeMinutes = 540,
            startDate = "2026-09-26",
            isActive = true
        )

        val occurrences = mutableListOf<TaskOccurrence>()
        // Habit A completed 7 days straight (20 to 26 Sep)
        for (day in 20..26) {
            occurrences.add(occ(day.toLong(), "Habit A", "2026-09-$day", OccurrenceStatus.COMPLETED, templateId = 1L))
        }
        // Habit B missed on 25 Sep
        occurrences.add(occ(100L, "Habit B", "2026-09-25", OccurrenceStatus.MISSED, templateId = 2L))
        // One Time missed on 26 Sep
        occurrences.add(occ(200L, "One Time", "2026-09-26", OccurrenceStatus.MISSED, templateId = 3L))

        val stats = StreakAndStatsEngine.calculateStatistics(
            allOccurrences = occurrences,
            templates = listOf(habitA, habitB, oneTime),
            selectedRange = StatsTimeRange.ALL_TIME,
            todayDateStr = "2026-09-26"
        )

        // Habit A has a 7-day streak. Habit B's miss and One Time's miss must not destroy Habit A's streak in Insights.
        assertEquals(7, stats.currentStreak)
        assertEquals(7, stats.longestStreak)
    }

    @Test
    fun testInsights_AllTimeRangesRespectDailyFilter() {
        val daily = dailyTemplate(1L, "Habit", "2026-09-01")
        val weekly = TaskTemplate(
            id = 2L,
            title = "Weekly",
            priority = Priority.LOW,
            color = 0,
            recurrenceType = RecurrenceType.WEEKLY,
            scheduledTimeMinutes = 540,
            startDate = "2026-09-01",
            isActive = true
        )

        val occurrences = listOf(
            occ(1L, "Habit", "2026-09-26", OccurrenceStatus.COMPLETED, templateId = 1L),
            occ(2L, "Weekly", "2026-09-26", OccurrenceStatus.COMPLETED, templateId = 2L),
            occ(3L, "Habit", "2026-09-20", OccurrenceStatus.COMPLETED, templateId = 1L),
            occ(4L, "Weekly", "2026-09-20", OccurrenceStatus.COMPLETED, templateId = 2L)
        )

        val templates = listOf(daily, weekly)

        for (range in StatsTimeRange.entries) {
            val stats = StreakAndStatsEngine.calculateStatistics(
                allOccurrences = occurrences,
                templates = templates,
                selectedRange = range,
                todayDateStr = "2026-09-26"
            )
            // Under no time range should Weekly task be counted
            assertTrue(stats.mostConsistentTaskTitle != "Weekly")
            when (range) {
                StatsTimeRange.TODAY -> assertEquals(1, stats.totalTasksCompleted)
                StatsTimeRange.LAST_7_DAYS -> assertEquals(2, stats.totalTasksCompleted)
                StatsTimeRange.ALL_TIME -> assertEquals(2, stats.totalTasksCompleted)
                else -> {}
            }
        }
    }
}
