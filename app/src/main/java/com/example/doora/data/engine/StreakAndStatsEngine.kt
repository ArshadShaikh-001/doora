package com.example.doora.data.engine

import com.example.doora.data.db.entity.TaskOccurrence
import com.example.doora.data.db.entity.TaskTemplate
import com.example.doora.data.model.OccurrenceStatus
import com.example.doora.data.model.Priority
import com.example.doora.data.model.RecurrenceType
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

data class DayStreakStatus(
    val date: String,
    val dayOfWeek: String,
    val dayOfMonth: Int,
    val status: OccurrenceStatus?
)

data class DailyTaskStreak(
    val templateId: Long,
    val title: String,
    val color: Int,
    val priority: Priority,
    val streakStartDate: String,
    val currentStreak: Int,
    val longestStreak: Int,
    val totalCompleted: Int,
    val totalDaysEligible: Int,
    val isCompletedToday: Boolean,
    val history: Map<String, OccurrenceStatus> = emptyMap(),
    val recentDays: List<DayStreakStatus> = emptyList()
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

    /**
     * Calculates the independent streak for a single Daily task template.
     * Only DAILY recurrence type tasks participate in streaks.
     * Dates before template.streakStartDate are NEVER evaluated and never treated as MISSED.
     */
    fun calculateDailyTaskStreak(
        template: TaskTemplate,
        occurrencesForTemplate: List<TaskOccurrence>,
        todayDateStr: String
    ): DailyTaskStreak {
        if (template.recurrenceType != RecurrenceType.DAILY) {
            return DailyTaskStreak(
                templateId = template.id,
                title = template.title,
                color = template.color,
                priority = template.priority,
                streakStartDate = template.streakStartDate,
                currentStreak = 0,
                longestStreak = 0,
                totalCompleted = 0,
                totalDaysEligible = 0,
                isCompletedToday = false
            )
        }

        val streakStartDate = template.streakStartDate
        // If the task start date is set in the future relative to today
        if (streakStartDate > todayDateStr) {
            return DailyTaskStreak(
                templateId = template.id,
                title = template.title,
                color = template.color,
                priority = template.priority,
                streakStartDate = streakStartDate,
                currentStreak = 0,
                longestStreak = 0,
                totalCompleted = 0,
                totalDaysEligible = 0,
                isCompletedToday = false
            )
        }

        // Generate chronological date list strictly within [streakStartDate .. todayDateStr]
        val dateList = mutableListOf<String>()
        val cal = Calendar.getInstance().apply {
            time = runCatching { dateFormat.parse(streakStartDate) }.getOrNull() ?: Date()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayCal = Calendar.getInstance().apply {
            time = runCatching { dateFormat.parse(todayDateStr) }.getOrNull() ?: Date()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        while (!cal.after(todayCal)) {
            dateList.add(dateFormat.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        val occByDate = occurrencesForTemplate.groupBy { it.scheduledDate }
        val historyMap = mutableMapOf<String, OccurrenceStatus>()

        for (date in dateList) {
            val occs = occByDate[date] ?: emptyList()
            val status = when {
                occs.any { it.status == OccurrenceStatus.COMPLETED } -> OccurrenceStatus.COMPLETED
                occs.any { it.status == OccurrenceStatus.SKIPPED } -> OccurrenceStatus.SKIPPED
                occs.any { it.status == OccurrenceStatus.MISSED } -> OccurrenceStatus.MISSED
                occs.any { it.status == OccurrenceStatus.PENDING } -> {
                    if (date < todayDateStr) OccurrenceStatus.MISSED else OccurrenceStatus.PENDING
                }
                else -> {
                    if (date < todayDateStr) OccurrenceStatus.MISSED else OccurrenceStatus.PENDING
                }
            }
            historyMap[date] = status
        }

        var runningStreak = 0
        var longestStreak = 0

        for (date in dateList) {
            val dayStatus = historyMap[date] ?: OccurrenceStatus.MISSED
            if (date == todayDateStr) {
                if (dayStatus == OccurrenceStatus.COMPLETED) {
                    val potential = runningStreak + 1
                    if (potential > longestStreak) {
                        longestStreak = potential
                    }
                }
            } else {
                // Past days strictly before today
                when (dayStatus) {
                    OccurrenceStatus.COMPLETED -> {
                        runningStreak++
                        if (runningStreak > longestStreak) {
                            longestStreak = runningStreak
                        }
                    }
                    OccurrenceStatus.SKIPPED -> {
                        // SKIPPED interrupts the consecutive completed-day streak
                        // It does NOT increment the streak, and consecutive run resets to 0
                        runningStreak = 0
                    }
                    OccurrenceStatus.MISSED, OccurrenceStatus.PENDING -> {
                        // MISSED breaks/resets current streak
                        runningStreak = 0
                    }
                }
            }
        }

        val todayStatus = historyMap[todayDateStr] ?: OccurrenceStatus.PENDING
        val isCompletedToday = todayStatus == OccurrenceStatus.COMPLETED

        val currentStreak = when (todayStatus) {
            OccurrenceStatus.COMPLETED -> runningStreak + 1
            OccurrenceStatus.PENDING -> runningStreak
            OccurrenceStatus.SKIPPED -> 0
            OccurrenceStatus.MISSED -> 0
        }

        if (currentStreak > longestStreak) {
            longestStreak = currentStreak
        }

        val totalCompleted = historyMap.values.count { it == OccurrenceStatus.COMPLETED }
        val totalDaysEligible = dateList.size

        val dayFormat = SimpleDateFormat("EEE", Locale.US)
        val dayOfMonthFormat = SimpleDateFormat("d", Locale.US)

        val recentDays = dateList.takeLast(14).map { d ->
            val parsed = runCatching { dateFormat.parse(d) }.getOrNull() ?: Date()
            DayStreakStatus(
                date = d,
                dayOfWeek = dayFormat.format(parsed),
                dayOfMonth = dayOfMonthFormat.format(parsed).toIntOrNull() ?: 1,
                status = historyMap[d]
            )
        }

        return DailyTaskStreak(
            templateId = template.id,
            title = template.title,
            color = template.color,
            priority = template.priority,
            streakStartDate = streakStartDate,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            totalCompleted = totalCompleted,
            totalDaysEligible = totalDaysEligible,
            isCompletedToday = isCompletedToday,
            history = historyMap,
            recentDays = recentDays
        )
    }

    /**
     * Calculates independent streaks for all active DAILY task templates.
     */
    fun calculateAllDailyStreaks(
        dailyTemplates: List<TaskTemplate>,
        allOccurrences: List<TaskOccurrence>,
        todayDateStr: String
    ): List<DailyTaskStreak> {
        val occurrencesByTemplate = allOccurrences.groupBy { it.templateId }
        return dailyTemplates
            .filter { it.recurrenceType == RecurrenceType.DAILY && it.isActive }
            .map { template ->
                calculateDailyTaskStreak(
                    template = template,
                    occurrencesForTemplate = occurrencesByTemplate[template.id] ?: emptyList(),
                    todayDateStr = todayDateStr
                )
            }
            .sortedWith(
                compareByDescending<DailyTaskStreak> { it.currentStreak }
                    .thenByDescending { it.longestStreak }
                    .thenBy { it.title.lowercase() }
            )
    }

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
