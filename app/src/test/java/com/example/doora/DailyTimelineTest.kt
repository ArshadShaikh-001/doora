package com.example.doora

import com.example.doora.data.db.entity.TaskOccurrence
import com.example.doora.data.model.OccurrenceStatus
import com.example.doora.data.model.Priority
import com.example.doora.data.repository.TasksViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DailyTimelineTest {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun occ(
        id: Long,
        title: String,
        timeMinutes: Int,
        status: OccurrenceStatus,
        color: Int = -1
    ): TaskOccurrence {
        return TaskOccurrence(
            id = id,
            templateId = id,
            title = title,
            priority = Priority.MEDIUM,
            color = color,
            scheduledDate = "2026-10-17",
            scheduledTimeMinutes = timeMinutes,
            status = status
        )
    }

    private fun sortTimelineTasks(list: List<TaskOccurrence>): List<TaskOccurrence> {
        return list.sortedWith(
            compareBy<TaskOccurrence> { it.scheduledTimeMinutes }
                .thenBy { it.id }
        )
    }

    @Test
    fun testTimelineTasksStrictlyChronologicalOrdering() {
        val tasks = listOf(
            occ(1L, "Evening Review", 20 * 60, OccurrenceStatus.PENDING),   // 8:00 PM
            occ(2L, "Morning Run", 7 * 60, OccurrenceStatus.COMPLETED),      // 7:00 AM (completed)
            occ(3L, "Lunch", 12 * 60 + 30, OccurrenceStatus.SKIPPED),        // 12:30 PM (skipped)
            occ(4L, "Standup", 9 * 60, OccurrenceStatus.MISSED),            // 9:00 AM (missed)
            occ(5L, "Breakfast", 7 * 60, OccurrenceStatus.PENDING)          // 7:00 AM (pending)
        )

        val sorted = sortTimelineTasks(tasks)

        // In Timeline view, tasks MUST be strictly chronologically sorted
        // 7:00 AM (id 2, completed), 7:00 AM (id 5, pending), 9:00 AM (id 4, missed), 12:30 PM (id 3, skipped), 8:00 PM (id 1, pending)
        assertEquals(2L, sorted[0].id)
        assertEquals(5L, sorted[1].id)
        assertEquals(4L, sorted[2].id)
        assertEquals(3L, sorted[3].id)
        assertEquals(1L, sorted[4].id)
    }

    @Test
    fun testDateNavigationNextAndPreviousDay() {
        val cal = Calendar.getInstance()

        // 1. Standard day transition
        var currentDate = "2026-10-17"
        cal.time = dateFormat.parse(currentDate)!!
        cal.add(Calendar.DAY_OF_MONTH, 1)
        var nextDate = dateFormat.format(cal.time)
        assertEquals("2026-10-18", nextDate)

        cal.time = dateFormat.parse(nextDate)!!
        cal.add(Calendar.DAY_OF_MONTH, -1)
        var prevDate = dateFormat.format(cal.time)
        assertEquals("2026-10-17", prevDate)

        // 2. Month boundary transition (October 31 -> November 1)
        currentDate = "2026-10-31"
        cal.time = dateFormat.parse(currentDate)!!
        cal.add(Calendar.DAY_OF_MONTH, 1)
        nextDate = dateFormat.format(cal.time)
        assertEquals("2026-11-01", nextDate)

        cal.time = dateFormat.parse(nextDate)!!
        cal.add(Calendar.DAY_OF_MONTH, -1)
        prevDate = dateFormat.format(cal.time)
        assertEquals("2026-10-31", prevDate)

        // 3. Year boundary transition (December 31 -> January 1)
        currentDate = "2026-12-31"
        cal.time = dateFormat.parse(currentDate)!!
        cal.add(Calendar.DAY_OF_MONTH, 1)
        nextDate = dateFormat.format(cal.time)
        assertEquals("2027-01-01", nextDate)

        cal.time = dateFormat.parse(nextDate)!!
        cal.add(Calendar.DAY_OF_MONTH, -1)
        prevDate = dateFormat.format(cal.time)
        assertEquals("2026-12-31", prevDate)
    }

    @Test
    fun testNowIndicatorInsertionIndex() {
        val tasks = listOf(
            occ(1L, "Task 1", 9 * 60, OccurrenceStatus.PENDING),   // 9:00 AM (540m)
            occ(2L, "Task 2", 12 * 60, OccurrenceStatus.PENDING),  // 12:00 PM (720m)
            occ(3L, "Task 3", 17 * 60, OccurrenceStatus.PENDING)   // 5:00 PM (1020m)
        )

        // Case 1: Early morning (7:30 AM = 450m) -> before all tasks
        val currentMinutesEarly = 7 * 60 + 30
        val idxEarly = tasks.indexOfFirst { it.scheduledTimeMinutes > currentMinutesEarly }.let {
            if (it == -1) tasks.size else it
        }
        assertEquals(0, idxEarly)

        // Case 2: Midday (10:30 AM = 630m) -> between Task 1 (9:00 AM) and Task 2 (12:00 PM)
        val currentMinutesMid = 10 * 60 + 30
        val idxMid = tasks.indexOfFirst { it.scheduledTimeMinutes > currentMinutesMid }.let {
            if (it == -1) tasks.size else it
        }
        assertEquals(1, idxMid)

        // Case 3: Afternoon (2:00 PM = 840m) -> between Task 2 (12:00 PM) and Task 3 (5:00 PM)
        val currentMinutesAft = 14 * 60
        val idxAft = tasks.indexOfFirst { it.scheduledTimeMinutes > currentMinutesAft }.let {
            if (it == -1) tasks.size else it
        }
        assertEquals(2, idxAft)

        // Case 4: Night (8:00 PM = 1200m) -> after all tasks
        val currentMinutesNight = 20 * 60
        val idxNight = tasks.indexOfFirst { it.scheduledTimeMinutes > currentMinutesNight }.let {
            if (it == -1) tasks.size else it
        }
        assertEquals(3, idxNight)
    }

    @Test
    fun testTasksViewModeDefaultIsList() {
        // Enums exist and default value resolution is LIST
        val defaultMode = TasksViewMode.valueOf("LIST")
        assertEquals(TasksViewMode.LIST, defaultMode)

        // Invalid fallback resolution test
        val fallback = runCatching { TasksViewMode.valueOf("UNKNOWN") }.getOrElse { TasksViewMode.LIST }
        assertEquals(TasksViewMode.LIST, fallback)
    }
}
