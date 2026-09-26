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

    /**
     * Returns today's occurrences sorted by priority (HIGH first) then by scheduled time.
     * The CASE expression maps the enum string to a sort integer.
     */
    @Query(
        """
        SELECT * FROM task_occurrences
        WHERE scheduledDate = :date
        ORDER BY
            CASE priority WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END ASC,
            scheduledTimeMinutes ASC
        """
    )
    fun observeForDate(date: String): Flow<List<TaskOccurrence>>

    @Query(
        "SELECT * FROM task_occurrences WHERE templateId = :templateId AND scheduledDate = :date LIMIT 1"
    )
    suspend fun findByTemplateAndDate(templateId: Long, date: String): TaskOccurrence?

    @Query("SELECT * FROM task_occurrences WHERE id = :id")
    suspend fun findById(id: Long): TaskOccurrence?

    /**
     * Returns an existing occurrence that would conflict with the given (date, time) slot.
     * [excludeId] = -1 means exclude nothing (add mode); pass a real id to skip it (edit mode).
     */
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

    @Query("DELETE FROM task_occurrences WHERE id = :id")
    suspend fun deleteById(id: Long)
}
