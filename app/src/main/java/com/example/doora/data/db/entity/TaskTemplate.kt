package com.example.doora.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.doora.data.model.Priority
import com.example.doora.data.model.RecurrenceType

/**
 * Defines the task (its title, schedule, colour, etc.).
 * For a ONE_TIME task there is exactly one TaskOccurrence.
 * For a DAILY task a new TaskOccurrence is generated each day.
 */
@Entity(tableName = "task_templates")
data class TaskTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val priority: Priority,
    /** ARGB packed colour Int */
    val color: Int,
    val recurrenceType: RecurrenceType,
    /** Minutes since midnight (0–1439) */
    val scheduledTimeMinutes: Int,
    val createdAt: Long = System.currentTimeMillis(),
    /** Soft-delete flag; false means the template (and recurrence) is cancelled */
    val isActive: Boolean = true
)
