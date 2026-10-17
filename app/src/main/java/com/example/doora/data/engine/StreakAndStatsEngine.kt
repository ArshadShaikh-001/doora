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

data class DailyTaskGraphItem(
    val occurrenceId: Long,
    val templateId: Long,
    val title: String,
    val color: Int,
    val scheduledTimeMinutes: Int,
    val status: OccurrenceStatus
)

data class DailyDateMetrics(
    val dateStr: String,
    val isToday: Boolean = false,
    val isFuture: Boolean = false,
    val tasks: List<DailyTaskGraphItem> = emptyList(),
    val completedCount: Int = 0,
    val missedCount: Int = 0,
    val skippedCount: Int = 0,
    val pendingCount: Int = 0,
    val completionRate: Float = 0f
)

data class DailyTrendDay(
    val dateStr: String,
    val dayLabel: String,
    val dayNumber: String,
    val isToday: Boolean,
    val completedCount: Int,
    val totalCount: Int,
    val completionRate: Float
)

data class WeeklyBucket(
    val label: String,
    val completed: Int,
    val total: Int,
    val rate: Float
)

data class MonthlyTrendSummary(
    val monthLabel: String,
    val totalCompleted: Int,
    val totalEligible: Int,
    val completionRate: Float,
    val weeklyBuckets: List<WeeklyBucket> = emptyList()
)

data class DailyTaskStreakSummary(
    val templateId: Long,
    val title: String,
    val color: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val totalCompleted: Int,
    val totalDaysEligible: Int
)

data class DailyInsightsData(
    val selectedDate: String = "",
    val selectedDateMetrics: DailyDateMetrics = DailyDateMetrics(""),
    val weeklyTrend: List<DailyTrendDay> = emptyList(),
    val monthlyTrend: MonthlyTrendSummary = MonthlyTrendSummary("", 0, 0, 0f),
    val todayCompletionRate: Float = 0f,
    val todayCompletedCount: Int = 0,
    val todayTotalCount: Int = 0,
    val totalCompletedAllTime: Int = 0,
    val bestCurrentStreak: Int = 0,
    val bestCurrentStreakTaskTitle: String? = null,
    val longestStreakAllTime: Int = 0,
    val longestStreakTaskTitle: String? = null,
    val mostConsistentTaskTitle: String? = null,
    val mostConsistentTaskColor: Int? = null,
    val mostConsistentTaskRate: Float? = null,
    val mostFrequentMissedTaskTitle: String? = null,
    val mostFrequentMissedTaskColor: Int? = null,
    val mostFrequentMissedCount: Int? = null,
    val dailyStreaks: List<DailyTaskStreakSummary> = emptyList(),
    val hasAnyDailyTasks: Boolean = false
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

    /**
     * Filters occurrences to include ONLY those belonging to DAILY recurring task templates.
     * Non-daily templates (ONE_TIME, WEEKLY, WEEKDAYS, MONTHLY, SPECIFIC_DATES) are excluded.
     * Occurrences scheduled prior to the template's creation/start date are also excluded.
     */
    fun filterDailyOccurrences(
        occurrences: List<TaskOccurrence>,
        templates: List<TaskTemplate>
    ): List<TaskOccurrence> {
        if (templates.isEmpty()) return occurrences
        val dailyTemplatesMap = templates
            .filter { it.recurrenceType == RecurrenceType.DAILY }
            .associateBy { it.id }

        return occurrences.filter { occ ->
            val template = dailyTemplatesMap[occ.templateId]
            template != null && occ.scheduledDate >= template.streakStartDate
        }
    }

    fun calculateStatistics(
        allOccurrences: List<TaskOccurrence>,
        templates: List<TaskTemplate>,
        selectedRange: StatsTimeRange,
        todayDateStr: String
    ): StatisticsData {
        val dailyOccurrences = if (templates.isNotEmpty()) {
            filterDailyOccurrences(allOccurrences, templates)
        } else {
            allOccurrences
        }

        val streakData = if (templates.isNotEmpty()) {
            val dailyTemplates = templates.filter { it.recurrenceType == RecurrenceType.DAILY }
            val activeDailyTemplates = dailyTemplates.filter { it.isActive }
            if (dailyTemplates.isNotEmpty()) {
                val allDailyStreaks = dailyTemplates.map { template ->
                    calculateDailyTaskStreak(
                        template = template,
                        occurrencesForTemplate = dailyOccurrences.filter { it.templateId == template.id },
                        todayDateStr = todayDateStr
                    )
                }
                val activeStreaks = allDailyStreaks.filter { s -> activeDailyTemplates.any { it.id == s.templateId } }
                val curr = activeStreaks.maxOfOrNull { it.currentStreak } ?: 0
                val longest = allDailyStreaks.maxOfOrNull { it.longestStreak } ?: 0
                StreakData(curr, longest)
            } else {
                StreakData(0, 0)
            }
        } else {
            calculateStreak(dailyOccurrences, todayDateStr)
        }

        // Today metrics
        val todayTasks = dailyOccurrences.filter { it.scheduledDate == todayDateStr }
        val completedToday = todayTasks.count { it.status == OccurrenceStatus.COMPLETED }
        val totalToday = todayTasks.count { it.status != OccurrenceStatus.SKIPPED }
        val dailyRate = if (totalToday > 0) (completedToday.toFloat() / totalToday) * 100f else 0f

        // Weekly metrics (last 7 days)
        val sevenDaysAgo = getDateDaysAgo(todayDateStr, 6)
        val weekTasks = dailyOccurrences.filter { it.scheduledDate in sevenDaysAgo..todayDateStr && it.status != OccurrenceStatus.SKIPPED }
        val weekCompleted = weekTasks.count { it.status == OccurrenceStatus.COMPLETED }
        val weeklyRate = if (weekTasks.isNotEmpty()) (weekCompleted.toFloat() / weekTasks.size) * 100f else 0f

        // Monthly metrics (current month)
        val currentMonthPrefix = todayDateStr.substring(0, 7) // "yyyy-MM"
        val monthTasks = dailyOccurrences.filter { it.scheduledDate.startsWith(currentMonthPrefix) && it.status != OccurrenceStatus.SKIPPED }
        val monthCompleted = monthTasks.count { it.status == OccurrenceStatus.COMPLETED }
        val monthlyRate = if (monthTasks.isNotEmpty()) (monthCompleted.toFloat() / monthTasks.size) * 100f else 0f

        // Filter for selected range
        val filteredOccurrences = filterByRange(dailyOccurrences, selectedRange, todayDateStr)
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

    fun calculateStatistics(
        allOccurrences: List<TaskOccurrence>,
        selectedRange: StatsTimeRange,
        todayDateStr: String
    ): StatisticsData = calculateStatistics(
        allOccurrences = allOccurrences,
        templates = emptyList(),
        selectedRange = selectedRange,
        todayDateStr = todayDateStr
    )

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

    fun getPreviousDateString(dateStr: String): String {
        return getDateDaysAgo(dateStr, 1)
    }

    fun getNextDateString(dateStr: String): String {
        val date = runCatching { dateFormat.parse(dateStr) }.getOrNull() ?: Date()
        val cal = Calendar.getInstance().apply {
            time = date
            add(Calendar.DAY_OF_YEAR, 1)
        }
        return dateFormat.format(cal.time)
    }

    fun formatDisplayDate(dateStr: String, todayDateStr: String): String {
        val date = runCatching { dateFormat.parse(dateStr) }.getOrNull() ?: return dateStr
        val displayFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
        val shortFormat = SimpleDateFormat("MMM d", Locale.US)
        val yesterday = getPreviousDateString(todayDateStr)
        val tomorrow = getNextDateString(todayDateStr)

        return when (dateStr) {
            todayDateStr -> "Today, ${shortFormat.format(date)}"
            yesterday -> "Yesterday, ${shortFormat.format(date)}"
            tomorrow -> "Tomorrow, ${shortFormat.format(date)}"
            else -> {
                val cal = Calendar.getInstance().apply { time = date }
                val todayCal = Calendar.getInstance().apply {
                    time = runCatching { dateFormat.parse(todayDateStr) }.getOrNull() ?: Date()
                }
                if (cal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)) {
                    SimpleDateFormat("EEE, MMM d", Locale.US).format(date)
                } else {
                    displayFormat.format(date)
                }
            }
        }
    }

    fun calculateDailyDateMetrics(
        selectedDateStr: String,
        todayDateStr: String,
        dailyTemplates: List<TaskTemplate>,
        dailyOccurrences: List<TaskOccurrence>
    ): DailyDateMetrics {
        val isToday = selectedDateStr == todayDateStr
        val isFuture = selectedDateStr > todayDateStr

        val templateMap = dailyTemplates.associateBy { it.id }

        val graphItems = if (isFuture) {
            dailyTemplates
                .filter { it.isActive && it.startDate <= selectedDateStr }
                .map { tmpl ->
                    val existingOcc = dailyOccurrences.find { it.templateId == tmpl.id && it.scheduledDate == selectedDateStr }
                    DailyTaskGraphItem(
                        occurrenceId = existingOcc?.id ?: -(tmpl.id),
                        templateId = tmpl.id,
                        title = existingOcc?.title ?: tmpl.title,
                        color = if (existingOcc != null && existingOcc.color != 0) existingOcc.color else tmpl.color,
                        scheduledTimeMinutes = existingOcc?.scheduledTimeMinutes ?: tmpl.scheduledTimeMinutes,
                        status = OccurrenceStatus.PENDING
                    )
                }
                .sortedWith(compareBy({ it.scheduledTimeMinutes }, { it.occurrenceId }))
        } else {
            val storedOccs = dailyOccurrences.filter { it.scheduledDate == selectedDateStr }
            val existingTemplateIds = storedOccs.map { it.templateId }.toSet()

            val missingActiveToday = if (isToday) {
                dailyTemplates.filter { it.isActive && it.startDate <= selectedDateStr && it.id !in existingTemplateIds }
                    .map { tmpl ->
                        DailyTaskGraphItem(
                            occurrenceId = -(tmpl.id),
                            templateId = tmpl.id,
                            title = tmpl.title,
                            color = tmpl.color,
                            scheduledTimeMinutes = tmpl.scheduledTimeMinutes,
                            status = OccurrenceStatus.PENDING
                        )
                    }
            } else emptyList()

            val storedItems = storedOccs.map { occ ->
                val tmpl = templateMap[occ.templateId]
                DailyTaskGraphItem(
                    occurrenceId = occ.id,
                    templateId = occ.templateId,
                    title = occ.title,
                    color = if (occ.color != 0) occ.color else (tmpl?.color ?: occ.color),
                    scheduledTimeMinutes = occ.scheduledTimeMinutes,
                    status = occ.status
                )
            }

            (storedItems + missingActiveToday).sortedWith(compareBy({ it.scheduledTimeMinutes }, { it.occurrenceId }))
        }

        val completed = graphItems.count { it.status == OccurrenceStatus.COMPLETED }
        val missed = graphItems.count { it.status == OccurrenceStatus.MISSED }
        val skipped = graphItems.count { it.status == OccurrenceStatus.SKIPPED }
        val pending = graphItems.count { it.status == OccurrenceStatus.PENDING }

        val rate = if (isFuture) {
            0f
        } else {
            val nonSkipped = graphItems.count { it.status != OccurrenceStatus.SKIPPED }
            if (nonSkipped > 0) (completed.toFloat() / nonSkipped) * 100f else 0f
        }

        return DailyDateMetrics(
            dateStr = selectedDateStr,
            isToday = isToday,
            isFuture = isFuture,
            tasks = graphItems,
            completedCount = completed,
            missedCount = missed,
            skippedCount = skipped,
            pendingCount = pending,
            completionRate = rate
        )
    }

    fun calculateWeeklyTrend(
        todayDateStr: String,
        dailyOccurrences: List<TaskOccurrence>
    ): List<DailyTrendDay> {
        val result = mutableListOf<DailyTrendDay>()
        val cal = Calendar.getInstance().apply {
            time = runCatching { dateFormat.parse(todayDateStr) }.getOrNull() ?: Date()
        }
        val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

        for (daysAgo in 6 downTo 0) {
            val targetCal = Calendar.getInstance().apply {
                time = cal.time
                add(Calendar.DAY_OF_YEAR, -daysAgo)
            }
            val dateStr = dateFormat.format(targetCal.time)
            val dayOfWeekIdx = targetCal.get(Calendar.DAY_OF_WEEK) - 1
            val dayLabel = dayNames.getOrElse(dayOfWeekIdx) { "" }
            val dayNumber = targetCal.get(Calendar.DAY_OF_MONTH).toString()

            val occs = dailyOccurrences.filter { it.scheduledDate == dateStr }
            val nonSkipped = occs.filter { it.status != OccurrenceStatus.SKIPPED }
            val completed = nonSkipped.count { it.status == OccurrenceStatus.COMPLETED }
            val rate = if (nonSkipped.isNotEmpty()) (completed.toFloat() / nonSkipped.size) * 100f else 0f

            result.add(
                DailyTrendDay(
                    dateStr = dateStr,
                    dayLabel = dayLabel,
                    dayNumber = dayNumber,
                    isToday = dateStr == todayDateStr,
                    completedCount = completed,
                    totalCount = nonSkipped.size,
                    completionRate = rate
                )
            )
        }
        return result
    }

    fun calculateMonthlyTrend(
        todayDateStr: String,
        dailyOccurrences: List<TaskOccurrence>
    ): MonthlyTrendSummary {
        val monthPrefix = todayDateStr.substring(0, 7)
        val cal = Calendar.getInstance().apply {
            time = runCatching { dateFormat.parse(todayDateStr) }.getOrNull() ?: Date()
        }
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.US)
        val monthLabel = monthFormat.format(cal.time)

        val monthOccs = dailyOccurrences.filter { it.scheduledDate.startsWith(monthPrefix) }
        val nonSkipped = monthOccs.filter { it.status != OccurrenceStatus.SKIPPED }
        val totalCompleted = nonSkipped.count { it.status == OccurrenceStatus.COMPLETED }
        val totalEligible = nonSkipped.size
        val rate = if (totalEligible > 0) (totalCompleted.toFloat() / totalEligible) * 100f else 0f

        val buckets = mutableListOf<WeeklyBucket>()
        val bucketRanges = listOf(
            "Week 1" to (1..7),
            "Week 2" to (8..14),
            "Week 3" to (15..21),
            "Week 4" to (22..31)
        )

        for ((label, range) in bucketRanges) {
            val bucketOccs = nonSkipped.filter {
                val dayOfMonth = it.scheduledDate.takeLast(2).toIntOrNull() ?: 0
                dayOfMonth in range
            }
            val comp = bucketOccs.count { it.status == OccurrenceStatus.COMPLETED }
            val total = bucketOccs.size
            val bucketRate = if (total > 0) (comp.toFloat() / total) * 100f else 0f
            buckets.add(WeeklyBucket(label = label, completed = comp, total = total, rate = bucketRate))
        }

        return MonthlyTrendSummary(
            monthLabel = monthLabel,
            totalCompleted = totalCompleted,
            totalEligible = totalEligible,
            completionRate = rate,
            weeklyBuckets = buckets
        )
    }

    fun calculateDailyTaskSummaries(
        dailyTemplates: List<TaskTemplate>,
        dailyOccurrences: List<TaskOccurrence>,
        todayDateStr: String
    ): List<DailyTaskStreakSummary> {
        return dailyTemplates.filter { it.isActive }.map { template ->
            val streak = calculateDailyTaskStreak(
                template = template,
                occurrencesForTemplate = dailyOccurrences.filter { it.templateId == template.id },
                todayDateStr = todayDateStr
            )
            DailyTaskStreakSummary(
                templateId = template.id,
                title = template.title,
                color = template.color,
                currentStreak = streak.currentStreak,
                longestStreak = streak.longestStreak,
                totalCompleted = streak.totalCompleted,
                totalDaysEligible = streak.totalDaysEligible
            )
        }.sortedWith(
            compareByDescending<DailyTaskStreakSummary> { it.currentStreak }
                .thenByDescending { it.longestStreak }
                .thenBy { it.title.lowercase() }
        )
    }

    fun calculateDailyInsights(
        selectedDateStr: String,
        todayDateStr: String,
        allTemplates: List<TaskTemplate>,
        allOccurrences: List<TaskOccurrence>
    ): DailyInsightsData {
        val dailyTemplates = allTemplates.filter { it.recurrenceType == RecurrenceType.DAILY }
        val dailyOccurrences = filterDailyOccurrences(allOccurrences, allTemplates)

        if (dailyTemplates.isEmpty()) {
            return DailyInsightsData(
                selectedDate = selectedDateStr,
                selectedDateMetrics = DailyDateMetrics(selectedDateStr),
                hasAnyDailyTasks = false
            )
        }

        val selectedDateMetrics = calculateDailyDateMetrics(
            selectedDateStr = selectedDateStr,
            todayDateStr = todayDateStr,
            dailyTemplates = dailyTemplates,
            dailyOccurrences = dailyOccurrences
        )

        val weeklyTrend = calculateWeeklyTrend(todayDateStr, dailyOccurrences)
        val monthlyTrend = calculateMonthlyTrend(todayDateStr, dailyOccurrences)

        val todayOccs = dailyOccurrences.filter { it.scheduledDate == todayDateStr }
        val todayComp = todayOccs.count { it.status == OccurrenceStatus.COMPLETED }
        val todayNonSkipped = todayOccs.count { it.status != OccurrenceStatus.SKIPPED }
        val todayRate = if (todayNonSkipped > 0) (todayComp.toFloat() / todayNonSkipped) * 100f else 0f

        val totalCompletedAllTime = dailyOccurrences.count { it.status == OccurrenceStatus.COMPLETED }

        val dailyStreaks = calculateDailyTaskSummaries(dailyTemplates, dailyOccurrences, todayDateStr)
        val bestCurrentStreak = dailyStreaks.maxOfOrNull { it.currentStreak } ?: 0
        val bestCurrentStreakTaskTitle = dailyStreaks.firstOrNull { it.currentStreak == bestCurrentStreak && it.currentStreak > 0 }?.title
        val longestStreakAllTime = dailyStreaks.maxOfOrNull { it.longestStreak } ?: 0
        val longestStreakTaskTitle = dailyStreaks.firstOrNull { it.longestStreak == longestStreakAllTime && it.longestStreak > 0 }?.title

        val templateMap = dailyTemplates.associateBy { it.id }
        val byTemplate = dailyOccurrences.groupBy { it.templateId }

        var mostConsistentTitle: String? = null
        var mostConsistentColor: Int? = null
        var highestRate = -1f

        var mostMissedTitle: String? = null
        var mostMissedColor: Int? = null
        var highestMissedCount = 0

        for ((tmplId, occs) in byTemplate) {
            val tmpl = templateMap[tmplId] ?: continue
            val nonSkipped = occs.filter { it.status != OccurrenceStatus.SKIPPED }
            if (nonSkipped.isNotEmpty()) {
                val comp = nonSkipped.count { it.status == OccurrenceStatus.COMPLETED }
                val r = comp.toFloat() / nonSkipped.size
                if (r > highestRate && comp >= 1) {
                    highestRate = r
                    mostConsistentTitle = tmpl.title
                    mostConsistentColor = tmpl.color
                }
            }
            val missed = occs.count { it.status == OccurrenceStatus.MISSED }
            if (missed > highestMissedCount) {
                highestMissedCount = missed
                mostMissedTitle = tmpl.title
                mostMissedColor = tmpl.color
            }
        }

        return DailyInsightsData(
            selectedDate = selectedDateStr,
            selectedDateMetrics = selectedDateMetrics,
            weeklyTrend = weeklyTrend,
            monthlyTrend = monthlyTrend,
            todayCompletionRate = todayRate,
            todayCompletedCount = todayComp,
            todayTotalCount = todayNonSkipped,
            totalCompletedAllTime = totalCompletedAllTime,
            bestCurrentStreak = bestCurrentStreak,
            bestCurrentStreakTaskTitle = bestCurrentStreakTaskTitle,
            longestStreakAllTime = longestStreakAllTime,
            longestStreakTaskTitle = longestStreakTaskTitle,
            mostConsistentTaskTitle = mostConsistentTitle,
            mostConsistentTaskColor = mostConsistentColor,
            mostConsistentTaskRate = if (highestRate >= 0f) highestRate * 100f else null,
            mostFrequentMissedTaskTitle = mostMissedTitle,
            mostFrequentMissedTaskColor = mostMissedColor,
            mostFrequentMissedCount = if (highestMissedCount > 0) highestMissedCount else null,
            dailyStreaks = dailyStreaks,
            hasAnyDailyTasks = true
        )
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
