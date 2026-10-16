package com.example.doora.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.doora.data.db.dao.TaskOccurrenceDao
import com.example.doora.data.db.dao.TaskTemplateDao
import com.example.doora.data.db.entity.TaskOccurrence
import com.example.doora.data.db.entity.TaskTemplate

@Database(
    entities = [TaskTemplate::class, TaskOccurrence::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class DooraDatabase : RoomDatabase() {
    abstract fun templateDao(): TaskTemplateDao
    abstract fun occurrenceDao(): TaskOccurrenceDao
}
