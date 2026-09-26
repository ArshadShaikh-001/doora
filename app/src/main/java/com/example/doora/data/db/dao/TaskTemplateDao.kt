package com.example.doora.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.doora.data.db.entity.TaskTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskTemplateDao {

    @Query("SELECT * FROM task_templates WHERE isActive = 1")
    fun observeActiveTemplates(): Flow<List<TaskTemplate>>

    @Query("SELECT * FROM task_templates WHERE isActive = 1")
    suspend fun getActiveTemplates(): List<TaskTemplate>

    @Query("SELECT * FROM task_templates WHERE id = :id")
    suspend fun getById(id: Long): TaskTemplate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: TaskTemplate): Long

    @Update
    suspend fun update(template: TaskTemplate)

    @Query("UPDATE task_templates SET isActive = 0 WHERE id = :id")
    suspend fun softDelete(id: Long)

    /**
     * Returns colours already used by active templates.
     * Pass [excludeId] = -1 to exclude nothing (add mode),
     * or a real template id to exclude that task (edit mode).
     */
    @Query(
        "SELECT color FROM task_templates WHERE isActive = 1 AND (:excludeId = -1 OR id != :excludeId)"
    )
    suspend fun getUsedColors(excludeId: Long): List<Int>
}
