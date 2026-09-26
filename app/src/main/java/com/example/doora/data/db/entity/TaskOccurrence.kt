package com.example.doora.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.doora.data.model.OccurrenceStatus
import com.example.doora.data.model.Priority

/**
 * Represents a task occurrence on a specific calendar date.
 * Fields are snapshotted from the template to preserve historical data.
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
    val completedAt: Long? = null,
    /** Epoch millis; set when snoozed, clears when dismissed/fired */
    val snoozedUntilMillis: Long? = null
)
