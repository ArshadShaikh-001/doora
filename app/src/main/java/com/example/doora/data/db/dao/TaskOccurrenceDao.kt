package com.example.doora.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.doora.data.db.entity.TaskOccurrence
import com.example.doora.data.model.OccurrenceStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskOccurrenceDao {

    @Query(
        """
        SELECT * FROM task_occurrences
        WHERE scheduledDate = :date
        ORDER BY scheduledTimeMinutes ASC, id ASC
        """
    )
    fun observeForDate(date: String): Flow<List<TaskOccurrence>>

    @Query(
        """
        SELECT * FROM task_occurrences
        WHERE scheduledDate >= :startDate AND scheduledDate <= :endDate
        ORDER BY scheduledDate ASC, scheduledTimeMinutes ASC
        """
    )
    fun observeForDateRange(startDate: String, endDate: String): Flow<List<TaskOccurrence>>

    @Query(
        """
        SELECT * FROM task_occurrences
        WHERE scheduledDate >= :startDate AND scheduledDate <= :endDate
        ORDER BY scheduledDate ASC, scheduledTimeMinutes ASC
        """
    )
    suspend fun getForDateRange(startDate: String, endDate: String): List<TaskOccurrence>

    @Query("SELECT * FROM task_occurrences ORDER BY scheduledDate ASC, scheduledTimeMinutes ASC")
    fun observeAllOccurrences(): Flow<List<TaskOccurrence>>

    @Query("SELECT * FROM task_occurrences ORDER BY scheduledDate ASC, scheduledTimeMinutes ASC")
    suspend fun getAllOccurrences(): List<TaskOccurrence>

    @Query("SELECT * FROM task_occurrences WHERE status = 'PENDING'")
    suspend fun getAllPendingOccurrences(): List<TaskOccurrence>

    @Query("SELECT * FROM task_occurrences WHERE scheduledDate < :todayDate AND status = 'PENDING'")
    suspend fun getPastPendingOccurrences(todayDate: String): List<TaskOccurrence>

    @Query("SELECT * FROM task_occurrences WHERE templateId = :templateId AND scheduledDate = :date LIMIT 1")
    suspend fun findByTemplateAndDate(templateId: Long, date: String): TaskOccurrence?

    @Query("SELECT * FROM task_occurrences WHERE id = :id")
    suspend fun findById(id: Long): TaskOccurrence?

    @Query(
        """
        SELECT * FROM task_occurrences
        WHERE scheduledDate = :date
          AND scheduledTimeMinutes = :timeMinutes
          AND (:excludeId = -1 OR id != :excludeId)
        LIMIT 1
        """
    )
    suspend fun findConflict(date: String, timeMinutes: Int, excludeId: Long): TaskOccurrence?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(occurrence: TaskOccurrence): Long

    @Update
    suspend fun update(occurrence: TaskOccurrence)

    @Query("UPDATE task_occurrences SET status = :status, completedAt = :completedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: OccurrenceStatus, completedAt: Long?)

    @Query("UPDATE task_occurrences SET snoozedUntilMillis = :snoozedUntilMillis WHERE id = :id")
    suspend fun updateSnooze(id: Long, snoozedUntilMillis: Long?)

    @Query("DELETE FROM task_occurrences WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM task_occurrences WHERE templateId = :templateId AND scheduledDate >= :fromDate AND status = 'PENDING'")
    suspend fun deleteFuturePendingOccurrences(templateId: Long, fromDate: String)

    @Query("SELECT * FROM task_occurrences WHERE hourlyReminderActive = 1 AND status = 'PENDING'")
    suspend fun getActiveReminderOccurrences(): List<TaskOccurrence>

    @Query("UPDATE task_occurrences SET hourlyReminderActive = :active, nextReminderMillis = :nextReminderMillis WHERE id = :id")
    suspend fun updateHourlyReminder(id: Long, active: Boolean, nextReminderMillis: Long?)
}
