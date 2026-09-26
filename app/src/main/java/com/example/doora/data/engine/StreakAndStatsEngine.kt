package com.example.doora.data.engine

import com.example.doora.data.db.entity.TaskOccurrence
import com.example.doora.data.model.OccurrenceStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class StatsTimeRange(val label: String) {
    TODAY("Today"),
    LAST_7_DAYS("7 Days"),
    LAST_30_DAYS("30 Days"),
    CURRENT_MONTH("Month"),
    CURRENT_YEAR("Year"),
    ALL_TIME("All Time")
}

data class StreakData(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0
)

data class StatisticsData(
    val tasksCompletedToday: Int = 0,
    val totalTasksToday: Int = 0,
    val dailyCompletionRate: Float = 0f,
    val weeklyCompletionRate: Float = 0f,
    val monthlyCompletionRate: Float = 0f,
    val selectedRangeCompletionRate: Float = 0f,
    val totalTasksCompleted: Int = 0,
    val totalTasksMissed: Int = 0,
    val totalTasksSkipped: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val mostConsistentTaskTitle: String? = null,
    val mostFrequentMissedTaskTitle: String? = null,
    val completedByDate: Map<String, Int> = emptyMap()
)

object StreakAndStatsEngine {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun calculateStreak(occurrences: List<TaskOccurrence>, todayDateStr: String): StreakData {
        if (occurrences.isEmpty()) return StreakData(0, 0)

        // Group by scheduledDate
        val byDate = occurrences.groupBy { it.scheduledDate }
        val sortedDates = byDate.keys.sorted()

        var currentStreak = 0
        var longestStreak = 0
        var runningStreak = 0

        for (date in sortedDates) {
            val tasks = byDate[date] ?: continue
            val hasMissed = tasks.any { it.status == OccurrenceStatus.MISSED }
            val completedCount = tasks.count { it.status == OccurrenceStatus.COMPLETED }
            val hasPending = tasks.any { it.status == OccurrenceStatus.PENDING }

            if (hasMissed) {
                runningStreak = 0
            } else if (completedCount > 0) {
                runningStreak += 1
                if (runningStreak > longestStreak) {
                    longestStreak = runningStreak
                }
            } else if (!hasPending) {
                // All skipped - streak persists unchanged
            }
        }

        // Calculate current streak backwards from today
        val todayTasks = byDate[todayDateStr] ?: emptyList()
        val todayHasMissed = todayTasks.any { it.status == OccurrenceStatus.MISSED }

        if (todayHasMissed) {
            currentStreak = 0
        } else {
            val todayCompleted = todayTasks.count { it.status == OccurrenceStatus.COMPLETED }
            var checkDate = if (todayCompleted > 0) {
                currentStreak = 1
                getPreviousDateString(todayDateStr)
            } else {
                // Today in progress, check from yesterday backwards
                getPreviousDateString(todayDateStr)
            }

            while (true) {
                val dayTasks = byDate[checkDate]
                if (dayTasks == null) {
                    // No tasks on this date, stop counting
                    break
                }
                val hasMissed = dayTasks.any { it.status == OccurrenceStatus.MISSED }
                val completed = dayTasks.count { it.status == OccurrenceStatus.COMPLETED }

                if (hasMissed) {
                    break
                } else if (completed > 0) {
                    currentStreak++
                    checkDate = getPreviousDateString(checkDate)
                } else {
                    // Skipped day preserves streak
                    checkDate = getPreviousDateString(checkDate)
                }
            }
        }

        if (currentStreak > longestStreak) {
            longestStreak = currentStreak
        }

        return StreakData(currentStreak, longestStreak)
    }

    fun calculateStatistics(
        allOccurrences: List<TaskOccurrence>,
        selectedRange: StatsTimeRange,
        todayDateStr: String
    ): StatisticsData {
        val streakData = calculateStreak(allOccurrences, todayDateStr)

        // Today metrics
        val todayTasks = allOccurrences.filter { it.scheduledDate == todayDateStr }
        val completedToday = todayTasks.count { it.status == OccurrenceStatus.COMPLETED }
        val totalToday = todayTasks.count { it.status != OccurrenceStatus.SKIPPED }
        val dailyRate = if (totalToday > 0) (completedToday.toFloat() / totalToday) * 100f else 0f

        // Weekly metrics (last 7 days)
        val sevenDaysAgo = getDateDaysAgo(todayDateStr, 6)
        val weekTasks = allOccurrences.filter { it.scheduledDate in sevenDaysAgo..todayDateStr && it.status != OccurrenceStatus.SKIPPED }
        val weekCompleted = weekTasks.count { it.status == OccurrenceStatus.COMPLETED }
        val weeklyRate = if (weekTasks.isNotEmpty()) (weekCompleted.toFloat() / weekTasks.size) * 100f else 0f

        // Monthly metrics (current month)
        val currentMonthPrefix = todayDateStr.substring(0, 7) // "yyyy-MM"
        val monthTasks = allOccurrences.filter { it.scheduledDate.startsWith(currentMonthPrefix) && it.status != OccurrenceStatus.SKIPPED }
        val monthCompleted = monthTasks.count { it.status == OccurrenceStatus.COMPLETED }
        val monthlyRate = if (monthTasks.isNotEmpty()) (monthCompleted.toFloat() / monthTasks.size) * 100f else 0f

        // Filter for selected range
        val filteredOccurrences = filterByRange(allOccurrences, selectedRange, todayDateStr)
        val rangeNonSkipped = filteredOccurrences.filter { it.status != OccurrenceStatus.SKIPPED }
        val rangeCompleted = rangeNonSkipped.count { it.status == OccurrenceStatus.COMPLETED }
        val rangeRate = if (rangeNonSkipped.isNotEmpty()) (rangeCompleted.toFloat() / rangeNonSkipped.size) * 100f else 0f

        val totalCompleted = filteredOccurrences.count { it.status == OccurrenceStatus.COMPLETED }
        val totalMissed = filteredOccurrences.count { it.status == OccurrenceStatus.MISSED }
        val totalSkipped = filteredOccurrences.count { it.status == OccurrenceStatus.SKIPPED }

        // Most consistently completed & most frequently missed task titles
        val byTitle = filteredOccurrences.groupBy { it.title }
        var mostConsistentTitle: String? = null
        var highestCompletionPercent = -1f

        var mostMissedTitle: String? = null
        var highestMissedCount = 0

        for ((title, list) in byTitle) {
            val nonSkipped = list.filter { it.status != OccurrenceStatus.SKIPPED }
            if (nonSkipped.isNotEmpty()) {
                val comp = nonSkipped.count { it.status == OccurrenceStatus.COMPLETED }
                val rate = comp.toFloat() / nonSkipped.size
                if (rate > highestCompletionPercent && comp >= 1) {
                    highestCompletionPercent = rate
                    mostConsistentTitle = title
                }
            }
            val missed = list.count { it.status == OccurrenceStatus.MISSED }
            if (missed > highestMissedCount) {
                highestMissedCount = missed
                mostMissedTitle = title
            }
        }

        // Completed by date map
        val completedByDate = filteredOccurrences
            .filter { it.status == OccurrenceStatus.COMPLETED }
            .groupBy { it.scheduledDate }
            .mapValues { it.value.size }

        return StatisticsData(
            tasksCompletedToday = completedToday,
            totalTasksToday = totalToday,
            dailyCompletionRate = dailyRate,
            weeklyCompletionRate = weeklyRate,
            monthlyCompletionRate = monthlyRate,
            selectedRangeCompletionRate = rangeRate,
            totalTasksCompleted = totalCompleted,
            totalTasksMissed = totalMissed,
            totalTasksSkipped = totalSkipped,
            currentStreak = streakData.currentStreak,
            longestStreak = streakData.longestStreak,
            mostConsistentTaskTitle = mostConsistentTitle,
            mostFrequentMissedTaskTitle = mostMissedTitle,
            completedByDate = completedByDate
        )
    }

    private fun filterByRange(
        list: List<TaskOccurrence>,
        range: StatsTimeRange,
        todayDateStr: String
    ): List<TaskOccurrence> {
        return when (range) {
            StatsTimeRange.TODAY -> list.filter { it.scheduledDate == todayDateStr }
            StatsTimeRange.LAST_7_DAYS -> {
                val start = getDateDaysAgo(todayDateStr, 6)
                list.filter { it.scheduledDate in start..todayDateStr }
            }
            StatsTimeRange.LAST_30_DAYS -> {
                val start = getDateDaysAgo(todayDateStr, 29)
                list.filter { it.scheduledDate in start..todayDateStr }
            }
            StatsTimeRange.CURRENT_MONTH -> {
                val prefix = todayDateStr.substring(0, 7)
                list.filter { it.scheduledDate.startsWith(prefix) }
            }
            StatsTimeRange.CURRENT_YEAR -> {
                val prefix = todayDateStr.substring(0, 4)
                list.filter { it.scheduledDate.startsWith(prefix) }
            }
            StatsTimeRange.ALL_TIME -> list
        }
    }

    private fun getPreviousDateString(dateStr: String): String {
        return getDateDaysAgo(dateStr, 1)
    }

    private fun getDateDaysAgo(dateStr: String, daysAgo: Int): String {
        val date = runCatching { dateFormat.parse(dateStr) }.getOrNull() ?: Date()
        val cal = Calendar.getInstance().apply {
            time = date
            add(Calendar.DAY_OF_YEAR, -daysAgo)
        }
        return dateFormat.format(cal.time)
    }
}
