package com.example.doora

import com.example.doora.data.db.entity.TaskOccurrence
import com.example.doora.data.model.OccurrenceStatus
import com.example.doora.data.model.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskOrderingTest {

    private fun occ(
        id: Long,
        title: String,
        priority: Priority,
        timeMinutes: Int,
        status: OccurrenceStatus,
        completedAt: Long? = null
    ): TaskOccurrence {
        return TaskOccurrence(
            id = id,
            templateId = 1L,
            title = title,
            priority = priority,
            color = 0,
            scheduledDate = "2026-09-26",
            scheduledTimeMinutes = timeMinutes,
            status = status,
            completedAt = completedAt
        )
    }

    private fun sortTasks(list: List<TaskOccurrence>): List<TaskOccurrence> {
        val incomplete = list.filter { it.status != OccurrenceStatus.COMPLETED }
            .sortedWith(
                compareBy<TaskOccurrence> {
                    when (it.priority) {
                        Priority.HIGH -> 0
                        Priority.MEDIUM -> 1
                        Priority.LOW -> 2
                    }
                }.thenBy { it.scheduledTimeMinutes }
                 .thenBy { it.id }
            )

        val completed = list.filter { it.status == OccurrenceStatus.COMPLETED }
            .sortedWith(
                compareBy<TaskOccurrence> { it.completedAt ?: 0L }
                    .thenBy { it.scheduledTimeMinutes }
                    .thenBy { it.id }
            )

        return incomplete + completed
    }

    @Test
    fun testIncompleteBeforeCompleted() {
        val list = listOf(
            occ(1L, "DSA", Priority.HIGH, 600, OccurrenceStatus.COMPLETED, completedAt = 1000L),
            occ(2L, "Reading", Priority.MEDIUM, 700, OccurrenceStatus.PENDING),
            occ(3L, "Typing", Priority.LOW, 500, OccurrenceStatus.PENDING),
            occ(4L, "Exercise", Priority.HIGH, 800, OccurrenceStatus.COMPLETED, completedAt = 900L)
        )

        val sorted = sortTasks(list)
        // Reading (Pending, Medium) and Typing (Pending, Low) must be FIRST
        assertEquals("Reading", sorted[0].title)
        assertEquals("Typing", sorted[1].title)
        // Exercise and DSA must be AFTER incomplete
        assertEquals("Exercise", sorted[2].title)
        assertEquals("DSA", sorted[3].title)
    }

    @Test
    fun testPriorityOrderingWithinIncomplete() {
        val list = listOf(
            occ(1L, "LowTask", Priority.LOW, 500, OccurrenceStatus.PENDING),
            occ(2L, "HighTask", Priority.HIGH, 900, OccurrenceStatus.PENDING),
            occ(3L, "MedTask", Priority.MEDIUM, 600, OccurrenceStatus.PENDING)
        )

        val sorted = sortTasks(list)
        assertEquals("HighTask", sorted[0].title)
        assertEquals("MedTask", sorted[1].title)
        assertEquals("LowTask", sorted[2].title)
    }

    @Test
    fun testNewTaskEntersAboveAllCompletedTasks() {
        // Initially: Reading, Typing, and completed: Exercise, DSA
        val existing = listOf(
            occ(1L, "Reading", Priority.MEDIUM, 600, OccurrenceStatus.PENDING),
            occ(2L, "Typing", Priority.LOW, 700, OccurrenceStatus.PENDING),
            occ(3L, "Exercise", Priority.HIGH, 500, OccurrenceStatus.COMPLETED, completedAt = 100L),
            occ(4L, "DSA", Priority.MEDIUM, 800, OccurrenceStatus.COMPLETED, completedAt = 200L)
        )

        // New Task "Gym" added (Pending, High priority)
        val withNewTask = existing + occ(5L, "Gym", Priority.HIGH, 400, OccurrenceStatus.PENDING)
        val sorted = sortTasks(withNewTask)

        // Gym must appear at the top of incomplete, strictly above all completed
        assertEquals("Gym", sorted[0].title)
        assertEquals("Reading", sorted[1].title)
        assertEquals("Typing", sorted[2].title)
        assertEquals("Exercise", sorted[3].title)
        assertEquals("DSA", sorted[4].title)
    }

    @Test
    fun testCompleteTaskMovesToCompletedSection() {
        val initial = listOf(
            occ(1L, "DSA", Priority.HIGH, 500, OccurrenceStatus.PENDING),
            occ(2L, "Reading", Priority.MEDIUM, 600, OccurrenceStatus.PENDING),
            occ(3L, "Typing", Priority.LOW, 700, OccurrenceStatus.PENDING),
            occ(4L, "Exercise", Priority.HIGH, 400, OccurrenceStatus.COMPLETED, completedAt = 100L),
            occ(5L, "Assignment", Priority.MEDIUM, 450, OccurrenceStatus.COMPLETED, completedAt = 200L)
        )

        val sortedInitial = sortTasks(initial)
        assertEquals("DSA", sortedInitial[0].title)
        assertEquals("Reading", sortedInitial[1].title)
        assertEquals("Typing", sortedInitial[2].title)
        assertEquals("Exercise", sortedInitial[3].title)
        assertEquals("Assignment", sortedInitial[4].title)

        // User completes DSA
        val afterCompletingDsa = initial.map {
            if (it.id == 1L) it.copy(status = OccurrenceStatus.COMPLETED, completedAt = 300L) else it
        }
        val sortedAfter = sortTasks(afterCompletingDsa)

        // Incomplete should now have Reading, Typing
        assertEquals("Reading", sortedAfter[0].title)
        assertEquals("Typing", sortedAfter[1].title)
        // Completed should now have Exercise, Assignment, DSA
        assertEquals("Exercise", sortedAfter[2].title)
        assertEquals("Assignment", sortedAfter[3].title)
        assertEquals("DSA", sortedAfter[4].title)
    }
}
