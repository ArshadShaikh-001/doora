package com.example.doora

import android.content.Context
import android.content.ContextWrapper
import com.example.doora.data.db.dao.TaskOccurrenceDao
import com.example.doora.data.db.dao.TaskTemplateDao
import com.example.doora.data.db.entity.TaskOccurrence
import com.example.doora.data.db.entity.TaskTemplate
import com.example.doora.data.model.OccurrenceStatus
import com.example.doora.data.model.Priority
import com.example.doora.data.model.RecurrenceType
import com.example.doora.data.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TaskColorAllocationTest {

    private class FakeTaskTemplateDao : TaskTemplateDao {
        val templates = mutableListOf<TaskTemplate>()
        var nextId = 1L

        override fun observeActiveTemplates(): Flow<List<TaskTemplate>> =
            flowOf(templates.filter { it.isActive })

        override fun observeAllTemplates(): Flow<List<TaskTemplate>> =
            flowOf(templates.toList())

        override suspend fun getAllTemplates(): List<TaskTemplate> = templates.toList()

        override suspend fun getActiveTemplates(): List<TaskTemplate> =
            templates.filter { it.isActive }

        override suspend fun getActiveTemplatesWithColor(color: Int): List<TaskTemplate> =
            templates.filter { it.isActive && it.color == color }

        override suspend fun getById(id: Long): TaskTemplate? =
            templates.find { it.id == id }

        override suspend fun insert(template: TaskTemplate): Long {
            val id = if (template.id != 0L) template.id else nextId++
            templates.removeAll { it.id == id }
            templates.add(template.copy(id = id))
            return id
        }

        override suspend fun update(template: TaskTemplate) {
            val idx = templates.indexOfFirst { it.id == template.id }
            if (idx != -1) templates[idx] = template
        }

        override suspend fun softDelete(id: Long) {
            val idx = templates.indexOfFirst { it.id == id }
            if (idx != -1) templates[idx] = templates[idx].copy(isActive = false)
        }

        override suspend fun getUsedColors(excludeId: Long): List<Int> =
            templates.filter { it.isActive && (excludeId == -1L || it.id != excludeId) }.map { it.color }
    }

    private class FakeTaskOccurrenceDao : TaskOccurrenceDao {
        val occurrences = mutableListOf<TaskOccurrence>()
        var nextId = 1L

        override fun observeForDate(date: String): Flow<List<TaskOccurrence>> =
            flowOf(occurrences.filter { it.scheduledDate == date })

        override suspend fun getForDate(date: String): List<TaskOccurrence> =
            occurrences.filter { it.scheduledDate == date }

        override suspend fun getPendingOccurrencesWithColor(color: Int, fromDate: String): List<TaskOccurrence> =
            occurrences.filter { it.status == OccurrenceStatus.PENDING && it.color == color && it.scheduledDate >= fromDate }

        override fun observeForDateRange(startDate: String, endDate: String): Flow<List<TaskOccurrence>> =
            flowOf(occurrences.filter { it.scheduledDate in startDate..endDate })

        override suspend fun getForDateRange(startDate: String, endDate: String): List<TaskOccurrence> =
            occurrences.filter { it.scheduledDate in startDate..endDate }

        override fun observeAllOccurrences(): Flow<List<TaskOccurrence>> =
            flowOf(occurrences.toList())

        override suspend fun getAllOccurrences(): List<TaskOccurrence> =
            occurrences.toList()

        override suspend fun getAllPendingOccurrences(): List<TaskOccurrence> =
            occurrences.filter { it.status == OccurrenceStatus.PENDING }

        override suspend fun getPastPendingOccurrences(todayDate: String): List<TaskOccurrence> =
            occurrences.filter { it.scheduledDate < todayDate && it.status == OccurrenceStatus.PENDING }

        override suspend fun findByTemplateAndDate(templateId: Long, date: String): TaskOccurrence? =
            occurrences.find { it.templateId == templateId && it.scheduledDate == date }

        override suspend fun findById(id: Long): TaskOccurrence? =
            occurrences.find { it.id == id }

        override suspend fun findConflict(date: String, timeMinutes: Int, excludeId: Long): TaskOccurrence? =
            occurrences.find { it.scheduledDate == date && it.scheduledTimeMinutes == timeMinutes && (excludeId == -1L || it.id != excludeId) }

        override suspend fun insert(occurrence: TaskOccurrence): Long {
            val id = if (occurrence.id != 0L) occurrence.id else nextId++
            occurrences.add(occurrence.copy(id = id))
            return id
        }

        override suspend fun update(occurrence: TaskOccurrence) {
            val idx = occurrences.indexOfFirst { it.id == occurrence.id }
            if (idx != -1) occurrences[idx] = occurrence
        }

        override suspend fun updateStatus(id: Long, status: OccurrenceStatus, completedAt: Long?) {
            val idx = occurrences.indexOfFirst { it.id == id }
            if (idx != -1) occurrences[idx] = occurrences[idx].copy(status = status, completedAt = completedAt)
        }

        override suspend fun updateSnooze(id: Long, snoozedUntilMillis: Long?) {
            val idx = occurrences.indexOfFirst { it.id == id }
            if (idx != -1) occurrences[idx] = occurrences[idx].copy(snoozedUntilMillis = snoozedUntilMillis)
        }

        override suspend fun deleteById(id: Long) {
            occurrences.removeAll { it.id == id }
        }

        override suspend fun deleteFuturePendingOccurrences(templateId: Long, fromDate: String) {
            occurrences.removeAll { it.templateId == templateId && it.scheduledDate >= fromDate && it.status == OccurrenceStatus.PENDING }
        }

        override suspend fun getActiveReminderOccurrences(): List<TaskOccurrence> =
            occurrences.filter { it.hourlyReminderActive && it.status == OccurrenceStatus.PENDING }

        override suspend fun updateHourlyReminder(id: Long, active: Boolean, nextReminderMillis: Long?) {
            val idx = occurrences.indexOfFirst { it.id == id }
            if (idx != -1) occurrences[idx] = occurrences[idx].copy(hourlyReminderActive = active, nextReminderMillis = nextReminderMillis)
        }
    }

    private lateinit var templateDao: FakeTaskTemplateDao
    private lateinit var occurrenceDao: FakeTaskOccurrenceDao
    private lateinit var repository: TaskRepository

    private val RED = 0xFFEF5350.toInt()
    private val BLUE = 0xFF42A5F5.toInt()
    private val GREEN = 0xFF66BB6A.toInt()
    private val PURPLE = 0xFFAB47BC.toInt()

    @Before
    fun setup() {
        templateDao = FakeTaskTemplateDao()
        occurrenceDao = FakeTaskOccurrenceDao()
        val dummyContext: Context = ContextWrapper(null)
        repository = TaskRepository(dummyContext, templateDao, occurrenceDao)
    }

    @Test
    fun testScenario1_DifferentTasksOnSameDayVsDifferentDays() = runBlocking {
        // Today is October 17.
        // Task "DSA" created for October 17 using Red.
        val templateId = templateDao.insert(
            TaskTemplate(
                id = 1L,
                title = "DSA",
                priority = Priority.HIGH,
                color = RED,
                recurrenceType = RecurrenceType.ONE_TIME,
                scheduledTimeMinutes = 540,
                startDate = "2026-10-17"
            )
        )
        occurrenceDao.insert(
            TaskOccurrence(
                id = 101L,
                templateId = templateId,
                title = "DSA",
                priority = Priority.HIGH,
                color = RED,
                scheduledDate = "2026-10-17",
                scheduledTimeMinutes = 540,
                status = OccurrenceStatus.PENDING
            )
        )

        // 1. Red must be unavailable (reserved) on October 17.
        val oct17Reserved = repository.getReservedColorsForDate("2026-10-17")
        assertTrue("Red should be reserved on Oct 17", RED in oct17Reserved)

        // 2. Red must be available on October 18, even if DSA from Oct 17 is still in the system.
        val oct18Reserved = repository.getReservedColorsForDate("2026-10-18")
        assertFalse("Red should be available on Oct 18", RED in oct18Reserved)

        // Reactive observation also emits the same
        val observedOct17 = repository.observeReservedColorsForDate("2026-10-17").first()
        assertTrue("Observed reserved colors should include Red on Oct 17", RED in observedOct17)
    }

    @Test
    fun testScenario2_DailyRecurringTask_CompletionReleasesTodayNotTomorrow() = runBlocking {
        // October 17: Daily recurring task DSA has Red.
        val templateId = templateDao.insert(
            TaskTemplate(
                id = 1L,
                title = "DSA",
                priority = Priority.HIGH,
                color = RED,
                recurrenceType = RecurrenceType.DAILY,
                scheduledTimeMinutes = 540,
                startDate = "2026-10-17",
                isActive = true
            )
        )
        val occId = occurrenceDao.insert(
            TaskOccurrence(
                id = 101L,
                templateId = templateId,
                title = "DSA",
                priority = Priority.HIGH,
                color = RED,
                scheduledDate = "2026-10-17",
                scheduledTimeMinutes = 540,
                status = OccurrenceStatus.PENDING
            )
        )

        // 1. Red is reserved on Oct 17 and also on Oct 18 (future daily recurrence).
        assertTrue("Red should be reserved on Oct 17", RED in repository.getReservedColorsForDate("2026-10-17"))
        assertTrue("Red should be reserved on Oct 18 for daily template", RED in repository.getReservedColorsForDate("2026-10-18"))

        // 2. Completing DSA on October 17 releases Red for October 17.
        occurrenceDao.updateStatus(occId, OccurrenceStatus.COMPLETED, System.currentTimeMillis())

        val oct17AfterCompletion = repository.getReservedColorsForDate("2026-10-17")
        assertFalse("Completing today's DSA must release Red on Oct 17", RED in oct17AfterCompletion)

        // 3. Completing DSA today does NOT release Red for tomorrow (October 18).
        val oct18AfterCompletion = repository.getReservedColorsForDate("2026-10-18")
        assertTrue("Red must remain reserved on Oct 18 for future occurrence", RED in oct18AfterCompletion)
    }

    @Test
    fun testScenario2_SkippedOrMissedReleasesColorOnThatDate() = runBlocking {
        val templateId = templateDao.insert(
            TaskTemplate(
                id = 1L,
                title = "DSA",
                priority = Priority.MEDIUM,
                color = RED,
                recurrenceType = RecurrenceType.DAILY,
                scheduledTimeMinutes = 540,
                startDate = "2026-10-17",
                isActive = true
            )
        )
        val occId = occurrenceDao.insert(
            TaskOccurrence(
                id = 101L,
                templateId = templateId,
                title = "DSA",
                priority = Priority.MEDIUM,
                color = RED,
                scheduledDate = "2026-10-17",
                scheduledTimeMinutes = 540,
                status = OccurrenceStatus.PENDING
            )
        )

        // When SKIPPED:
        occurrenceDao.updateStatus(occId, OccurrenceStatus.SKIPPED, null)
        assertFalse("Skipping must release Red on Oct 17", RED in repository.getReservedColorsForDate("2026-10-17"))

        // When MISSED:
        occurrenceDao.updateStatus(occId, OccurrenceStatus.MISSED, null)
        assertFalse("Missed occurrence must release Red on Oct 17", RED in repository.getReservedColorsForDate("2026-10-17"))
    }

    @Test
    fun testScenario3_FutureDatedTasks() = runBlocking {
        // On Oct 17, schedule one-time task "Workout" for October 19 using Blue.
        templateDao.insert(
            TaskTemplate(
                id = 2L,
                title = "Workout",
                priority = Priority.MEDIUM,
                color = BLUE,
                recurrenceType = RecurrenceType.ONE_TIME,
                scheduledTimeMinutes = 540,
                startDate = "2026-10-19",
                isActive = true
            )
        )
        occurrenceDao.insert(
            TaskOccurrence(
                id = 201L,
                templateId = 2L,
                title = "Workout",
                priority = Priority.MEDIUM,
                color = BLUE,
                scheduledDate = "2026-10-19",
                scheduledTimeMinutes = 600,
                status = OccurrenceStatus.PENDING
            )
        )

        // Blue remains available for October 17 and October 18.
        assertFalse("Blue should be available on Oct 17", BLUE in repository.getReservedColorsForDate("2026-10-17"))
        assertFalse("Blue should be available on Oct 18", BLUE in repository.getReservedColorsForDate("2026-10-18"))

        // Blue is unavailable (reserved) for any other task scheduled for October 19.
        assertTrue("Blue must be reserved on Oct 19", BLUE in repository.getReservedColorsForDate("2026-10-19"))
    }

    @Test
    fun testRescheduledTaskReleasesOldDateAndReservesNewDate() = runBlocking {
        // Task scheduled for Oct 17 with GREEN
        val occId = occurrenceDao.insert(
            TaskOccurrence(
                id = 301L,
                templateId = 3L,
                title = "Gym",
                priority = Priority.LOW,
                color = GREEN,
                scheduledDate = "2026-10-17",
                scheduledTimeMinutes = 700,
                status = OccurrenceStatus.PENDING
            )
        )

        assertTrue("Green reserved on Oct 17", GREEN in repository.getReservedColorsForDate("2026-10-17"))
        assertFalse("Green free on Oct 20", GREEN in repository.getReservedColorsForDate("2026-10-20"))

        // Reschedule to Oct 20
        val occ = occurrenceDao.findById(occId)!!
        occurrenceDao.update(occ.copy(scheduledDate = "2026-10-20"))

        assertFalse("Green released on old date Oct 17", GREEN in repository.getReservedColorsForDate("2026-10-17"))
        assertTrue("Green reserved on new date Oct 20", GREEN in repository.getReservedColorsForDate("2026-10-20"))
    }

    @Test
    fun testDeletedTaskReleasesColorImmediately() = runBlocking {
        // Task with PURPLE on Oct 17
        val occId = occurrenceDao.insert(
            TaskOccurrence(
                id = 401L,
                templateId = 4L,
                title = "Read",
                priority = Priority.LOW,
                color = PURPLE,
                scheduledDate = "2026-10-17",
                scheduledTimeMinutes = 800,
                status = OccurrenceStatus.PENDING
            )
        )

        assertTrue("Purple reserved on Oct 17", PURPLE in repository.getReservedColorsForDate("2026-10-17"))

        // Delete occurrence
        occurrenceDao.deleteById(occId)
        assertFalse("Purple released on Oct 17 immediately after deletion", PURPLE in repository.getReservedColorsForDate("2026-10-17"))
    }

    @Test
    fun testEditModeExcludesOwnTaskFromReservedColors() = runBlocking {
        val tmplId = templateDao.insert(
            TaskTemplate(
                id = 5L,
                title = "Study",
                priority = Priority.MEDIUM,
                color = RED,
                recurrenceType = RecurrenceType.ONE_TIME,
                scheduledTimeMinutes = 540,
                startDate = "2026-10-17",
                isActive = true
            )
        )
        val occId = occurrenceDao.insert(
            TaskOccurrence(
                id = 501L,
                templateId = tmplId,
                title = "Study",
                priority = Priority.MEDIUM,
                color = RED,
                scheduledDate = "2026-10-17",
                scheduledTimeMinutes = 900,
                status = OccurrenceStatus.PENDING
            )
        )

        // For other tasks (no exclude), Red is reserved
        assertTrue(RED in repository.getReservedColorsForDate("2026-10-17"))

        // When editing this task, excluding its occurrence & template ID leaves Red available to it
        val reservedForSelf = repository.getReservedColorsForDate(
            dateStr = "2026-10-17",
            excludeTemplateId = tmplId,
            excludeOccurrenceId = occId
        )
        assertFalse("Self-color must not be marked reserved during edit", RED in reservedForSelf)
    }

    @Test
    fun testFindRecurringColorConflictDetectsCollisions() = runBlocking {
        // Suppose there is an existing one-time task on 2026-10-20 with BLUE
        occurrenceDao.insert(
            TaskOccurrence(
                id = 601L,
                templateId = 6L,
                title = "Exam",
                priority = Priority.HIGH,
                color = BLUE,
                scheduledDate = "2026-10-20",
                scheduledTimeMinutes = 600,
                status = OccurrenceStatus.PENDING
            )
        )

        // Trying to create a DAILY task starting 2026-10-17 with BLUE must detect conflict on 2026-10-20
        val conflict = repository.findRecurringColorConflict(
            color = BLUE,
            recurrenceType = RecurrenceType.DAILY,
            startDate = "2026-10-17"
        )
        assertEquals("2026-10-20", conflict)

        // But creating with GREEN should have no conflict
        val noConflict = repository.findRecurringColorConflict(
            color = GREEN,
            recurrenceType = RecurrenceType.DAILY,
            startDate = "2026-10-17"
        )
        assertNull(noConflict)
    }
}
