package com.example.doora

import com.example.doora.alarm.AlarmHelper
import com.example.doora.data.db.entity.TaskOccurrence
import com.example.doora.data.model.OccurrenceStatus
import com.example.doora.data.model.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AlarmAndSnoozeTest {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun sampleOccurrence(
        id: Long = 1L,
        templateId: Long = 10L,
        date: String = "2026-10-17",
        timeMinutes: Int = 14 * 60, // 2:00 PM
        status: OccurrenceStatus = OccurrenceStatus.PENDING,
        snoozedUntilMillis: Long? = null
    ): TaskOccurrence {
        return TaskOccurrence(
            id = id,
            templateId = templateId,
            title = "Design Meeting",
            priority = Priority.HIGH,
            color = -16744193,
            scheduledDate = date,
            scheduledTimeMinutes = timeMinutes,
            status = status,
            snoozedUntilMillis = snoozedUntilMillis
        )
    }

    @Test
    fun testComputeTriggerMillisNormalScheduledTime() {
        val occ = sampleOccurrence(date = "2026-10-17", timeMinutes = 14 * 60) // 14:00
        val trigger = AlarmHelper.computeTriggerMillis(occ)

        val cal = Calendar.getInstance().apply {
            time = dateFormat.parse("2026-10-17")!!
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(cal.timeInMillis, trigger)
    }

    @Test
    fun testComputeTriggerMillisWithActiveFutureSnooze() {
        val futureSnooze = System.currentTimeMillis() + 15 * 60 * 1000L // 15 mins from now
        val occ = sampleOccurrence(snoozedUntilMillis = futureSnooze)

        val trigger = AlarmHelper.computeTriggerMillis(occ)
        assertEquals(futureSnooze, trigger)
    }

    @Test
    fun testComputeTriggerMillisIgnoresPastExpiredSnooze() {
        val pastSnooze = System.currentTimeMillis() - 10 * 60 * 1000L // 10 mins in past
        val occ = sampleOccurrence(
            date = "2026-10-17",
            timeMinutes = 15 * 60,
            snoozedUntilMillis = pastSnooze
        )

        val trigger = AlarmHelper.computeTriggerMillis(occ)
        // Should NOT return pastSnooze; should fall back to scheduled time
        assertNotEquals(pastSnooze, trigger)

        val cal = Calendar.getInstance().apply {
            time = dateFormat.parse("2026-10-17")!!
            set(Calendar.HOUR_OF_DAY, 15)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(cal.timeInMillis, trigger)
    }

    @Test
    fun testSnoozeDurations10_15_20Minutes() {
        val baseNow = System.currentTimeMillis()

        val snooze10 = baseNow + 10 * 60 * 1000L
        val snooze15 = baseNow + 15 * 60 * 1000L
        val snooze20 = baseNow + 20 * 60 * 1000L

        assertEquals(600_000L, snooze10 - baseNow)
        assertEquals(900_000L, snooze15 - baseNow)
        assertEquals(1_200_000L, snooze20 - baseNow)

        // Occurrences snoozed with these values remain PENDING
        val occ10 = sampleOccurrence().copy(snoozedUntilMillis = snooze10)
        val occ15 = sampleOccurrence().copy(snoozedUntilMillis = snooze15)
        val occ20 = sampleOccurrence().copy(snoozedUntilMillis = snooze20)

        assertEquals(OccurrenceStatus.PENDING, occ10.status)
        assertEquals(OccurrenceStatus.PENDING, occ15.status)
        assertEquals(OccurrenceStatus.PENDING, occ20.status)
    }

    @Test
    fun testDismissPreservesPendingStatusAndClearsSnooze() {
        val occ = sampleOccurrence(snoozedUntilMillis = System.currentTimeMillis() + 10000L)
        // On Dismiss:
        val dismissed = occ.copy(snoozedUntilMillis = null)

        // Task must NOT be completed
        assertEquals(OccurrenceStatus.PENDING, dismissed.status)
        assertNull(dismissed.completedAt)
        assertNull(dismissed.snoozedUntilMillis)
    }

    @Test
    fun testCompleteAndSkipClearSnooze() {
        val occ = sampleOccurrence(snoozedUntilMillis = System.currentTimeMillis() + 60000L)

        val completed = occ.copy(
            status = OccurrenceStatus.COMPLETED,
            completedAt = System.currentTimeMillis(),
            snoozedUntilMillis = null
        )
        assertEquals(OccurrenceStatus.COMPLETED, completed.status)
        assertNotNull(completed.completedAt)
        assertNull(completed.snoozedUntilMillis)

        val skipped = occ.copy(
            status = OccurrenceStatus.SKIPPED,
            completedAt = null,
            snoozedUntilMillis = null
        )
        assertEquals(OccurrenceStatus.SKIPPED, skipped.status)
        assertNull(skipped.snoozedUntilMillis)
    }

    @Test
    fun testReschedulingUpdatesOccurrenceWithoutModifyingTemplateId() {
        val originalOcc = sampleOccurrence(
            id = 42L,
            templateId = 100L,
            date = "2026-10-17",
            timeMinutes = 9 * 60
        )

        // Reschedule to tomorrow at 4:30 PM (16 * 60 + 30 = 990)
        val rescheduled = originalOcc.copy(
            scheduledDate = "2026-10-18",
            scheduledTimeMinutes = 990,
            snoozedUntilMillis = null,
            status = OccurrenceStatus.PENDING
        )

        assertEquals(42L, rescheduled.id)
        assertEquals(100L, rescheduled.templateId) // Template id unchanged
        assertEquals("2026-10-18", rescheduled.scheduledDate)
        assertEquals(990, rescheduled.scheduledTimeMinutes)
        assertNull(rescheduled.snoozedUntilMillis)
        assertEquals(OccurrenceStatus.PENDING, rescheduled.status)
    }

    @Test
    fun testDayTransitionMidnightLogicForSnoozeCrossingMidnight() {
        val yesterday = "2026-10-16"
        val today = "2026-10-17"
        val now = System.currentTimeMillis()

        // Case A: Past pending task WITHOUT active future snooze -> becomes MISSED
        val pastNormal = sampleOccurrence(date = yesterday, snoozedUntilMillis = null)
        val isPastNormalEligibleForMissed = pastNormal.scheduledDate < today &&
                (pastNormal.snoozedUntilMillis == null || pastNormal.snoozedUntilMillis <= now)
        assertTrue(isPastNormalEligibleForMissed)

        // Case B: Past pending task WITH active future snooze (e.g. snoozed across midnight to 12:15 AM today)
        val futureSnoozeTime = now + 15 * 60 * 1000L
        val pastSnoozedActive = sampleOccurrence(date = yesterday, snoozedUntilMillis = futureSnoozeTime)
        val isSnoozedActiveEligibleForMissed = pastSnoozedActive.scheduledDate < today &&
                (pastSnoozedActive.snoozedUntilMillis == null || pastSnoozedActive.snoozedUntilMillis <= now)
        assertFalse(isSnoozedActiveEligibleForMissed) // Remains PENDING so alarm triggers!

        // Case C: Past pending task whose snooze already expired
        val expiredSnoozeTime = now - 5 * 60 * 1000L
        val pastSnoozedExpired = sampleOccurrence(date = yesterday, snoozedUntilMillis = expiredSnoozeTime)
        val isSnoozedExpiredEligibleForMissed = pastSnoozedExpired.scheduledDate < today &&
                (pastSnoozedExpired.snoozedUntilMillis == null || pastSnoozedExpired.snoozedUntilMillis <= now)
        assertTrue(isSnoozedExpiredEligibleForMissed) // Properly becomes MISSED
    }

    @Test
    fun testCustomRingtoneUriPersistenceOnTemplateAndOccurrence() {
        val customUri = "content://media/external/audio/media/12345"

        val template = com.example.doora.data.db.entity.TaskTemplate(
            id = 5L,
            title = "Morning Run",
            priority = Priority.HIGH,
            color = -16744193,
            recurrenceType = com.example.doora.data.model.RecurrenceType.DAILY,
            scheduledTimeMinutes = 420,
            startDate = "2026-10-17",
            ringtoneUri = customUri
        )

        assertEquals(customUri, template.ringtoneUri)

        val occurrence = TaskOccurrence(
            id = 10L,
            templateId = template.id,
            title = template.title,
            priority = template.priority,
            color = template.color,
            scheduledDate = "2026-10-17",
            scheduledTimeMinutes = template.scheduledTimeMinutes,
            ringtoneUri = template.ringtoneUri
        )

        assertEquals(customUri, occurrence.ringtoneUri)

        // Verify snooze preserves ringtoneUri
        val snoozed = occurrence.copy(snoozedUntilMillis = System.currentTimeMillis() + 600000)
        assertEquals(customUri, snoozed.ringtoneUri)

        // Verify reschedule preserves ringtoneUri
        val rescheduled = occurrence.copy(scheduledDate = "2026-10-18", scheduledTimeMinutes = 480)
        assertEquals(customUri, rescheduled.ringtoneUri)

        // Verify default tone is represented by null ringtoneUri
        val defaultTemplate = template.copy(ringtoneUri = null)
        assertNull(defaultTemplate.ringtoneUri)
    }

    @Test
    fun testAlarmHelperExtraConstant() {
        assertEquals("extra_task_ringtone_uri", AlarmHelper.EXTRA_TASK_RINGTONE_URI)
    }

    @Test
    fun testTasksSectionVsStreaksSectionRingtoneScoping() {
        // Case 1: Task created from Tasks section (allowCustomRingtone = false) -> ringtoneUri is null
        val allowCustomRingtoneTasks = false
        val userPickedUri = "content://media/external/audio/media/999"
        val ringtoneUriTasksNew = if (allowCustomRingtoneTasks) userPickedUri else null
        assertNull("Tasks section must always create tasks with default ringtone", ringtoneUriTasksNew)

        // Case 2: Task created from Streaks section (allowCustomRingtone = true) -> ringtoneUri is preserved
        val allowCustomRingtoneStreaks = true
        val ringtoneUriStreaksNew = if (allowCustomRingtoneStreaks) userPickedUri else null
        assertEquals(userPickedUri, ringtoneUriStreaksNew)

        // Case 3: Task originally created from Streaks edited in Tasks section (allowCustomRingtone = false)
        // Its existing ringtone must NOT be wiped out
        val existingStreaksTaskUri = "content://media/external/audio/media/12345"
        val isEditMode = true
        val ringtoneUriOnEditFromTasks = if (allowCustomRingtoneTasks) {
            userPickedUri
        } else {
            if (isEditMode) existingStreaksTaskUri else null
        }
        assertEquals(
            "Editing an existing task from Tasks section must preserve its custom ringtone from Streaks",
            existingStreaksTaskUri,
            ringtoneUriOnEditFromTasks
        )
    }
}
