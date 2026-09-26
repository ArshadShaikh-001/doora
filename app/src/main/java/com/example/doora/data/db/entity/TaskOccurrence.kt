package com.example.doora.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.doora.data.model.OccurrenceStatus
import com.example.doora.data.model.Priority

/**
 * Represents a task on a specific calendar date.
 * Fields (title, priority, color) are snapshotted from the template so that
 * historical occurrences are not affected by future edits.
 *
 * Unique constraint on (scheduledDate, scheduledTimeMinutes) prevents two tasks
 * from occupying the same slot on the same day.
 */
@Entity(
    tableName = "task_occurrences",
    foreignKeys = [
        ForeignKey(
            entity = TaskTemplate::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("templateId"),
        Index(value = ["scheduledDate", "scheduledTimeMinutes"], unique = true)
    ]
)
data class TaskOccurrence(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    /** Snapshot of template title at creation time */
    val title: String,
    /** Snapshot of template priority */
    val priority: Priority,
    /** Snapshot of template ARGB colour */
    val color: Int,
    /** ISO date string "yyyy-MM-dd" */
    val scheduledDate: String,
    /** Minutes since midnight (0–1439) */
    val scheduledTimeMinutes: Int,
    val status: OccurrenceStatus = OccurrenceStatus.PENDING,
    /** Epoch millis; non-null only when status == COMPLETED */
    val completedAt: Long? = null
)
