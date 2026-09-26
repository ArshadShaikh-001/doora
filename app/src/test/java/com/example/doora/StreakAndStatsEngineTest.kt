package com.example.doora

import com.example.doora.data.db.entity.TaskOccurrence
import com.example.doora.data.engine.StatsTimeRange
import com.example.doora.data.engine.StreakAndStatsEngine
import com.example.doora.data.model.OccurrenceStatus
import com.example.doora.data.model.Priority
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakAndStatsEngineTest {

    private fun occ(
        id: Long,
        title: String,
        date: String,
        status: OccurrenceStatus
    ): TaskOccurrence {
        return TaskOccurrence(
            id = id,
            templateId = 1,
            title = title,
            priority = Priority.MEDIUM,
            color = 0,
            scheduledDate = date,
            scheduledTimeMinutes = 540,
            status = status
        )
    }

    @Test
    fun testStreakActiveSuccess() {
        val list = listOf(
            occ(1, "Task 1", "2026-09-24", OccurrenceStatus.COMPLETED),
            occ(2, "Task 2", "2026-09-25", OccurrenceStatus.COMPLETED),
            occ(3, "Task 3", "2026-09-26", OccurrenceStatus.COMPLETED)
        )
        val streak = StreakAndStatsEngine.calculateStreak(list, "2026-09-26")
        assertEquals(3, streak.currentStreak)
        assertEquals(3, streak.longestStreak)
    }

    @Test
    fun testMissedTaskBreaksStreak() {
        val list = listOf(
            occ(1, "Task 1", "2026-09-23", OccurrenceStatus.COMPLETED),
            occ(2, "Task 2", "2026-09-24", OccurrenceStatus.COMPLETED),
            occ(3, "Task 3", "2026-09-25", OccurrenceStatus.MISSED), // Breaks streak!
            occ(4, "Task 4", "2026-09-26", OccurrenceStatus.COMPLETED)
        )
        val streak = StreakAndStatsEngine.calculateStreak(list, "2026-09-26")
        assertEquals(1, streak.currentStreak)
        assertEquals(2, streak.longestStreak)
    }

    @Test
    fun testSkippedTaskDoesNotBreakStreak() {
        val list = listOf(
            occ(1, "Task 1", "2026-09-23", OccurrenceStatus.COMPLETED),
            occ(2, "Task 2", "2026-09-24", OccurrenceStatus.COMPLETED),
            occ(3, "Task 3", "2026-09-25", OccurrenceStatus.SKIPPED), // Skipped does NOT break streak!
            occ(4, "Task 4", "2026-09-26", OccurrenceStatus.COMPLETED)
        )
        val streak = StreakAndStatsEngine.calculateStreak(list, "2026-09-26")
        assertEquals(3, streak.currentStreak)
        assertEquals(3, streak.longestStreak)
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
