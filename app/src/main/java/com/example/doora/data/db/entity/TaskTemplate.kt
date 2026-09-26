package com.example.doora.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.doora.data.model.Priority
import com.example.doora.data.model.RecurrenceType

/**
 * Defines the recurring or one-time task definition.
 * TaskOccurrence represents a task on a specific calendar date.
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
    /** Start date in "yyyy-MM-dd" format */
    val startDate: String = "",
    /** Comma-separated day numbers (1=Monday ... 7=Sunday) for SPECIFIC_WEEKDAYS */
    val weekdays: String = "",
    /** Day of month (1..31) for MONTHLY */
    val dayOfMonth: Int = 1,
    /** Comma-separated dates "yyyy-MM-dd,yyyy-MM-dd" for SPECIFIC_DATES */
    val specificDates: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** Soft-delete flag; false means template recurrence is cancelled */
    val isActive: Boolean = true
)
