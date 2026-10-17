package com.example.doora

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.doora.data.db.DooraDatabase
import com.example.doora.data.repository.TaskRepository
import com.example.doora.data.repository.TasksPreferencesRepository

class DooraApplication : Application() {

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE task_templates ADD COLUMN ringtoneUri TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE task_occurrences ADD COLUMN ringtoneUri TEXT DEFAULT NULL")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE task_occurrences ADD COLUMN hourlyReminderActive INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE task_occurrences ADD COLUMN nextReminderMillis INTEGER DEFAULT NULL")
        }
    }

    val database: DooraDatabase by lazy {
        Room.databaseBuilder(applicationContext, DooraDatabase::class.java, "doora.db")
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
            .fallbackToDestructiveMigration(true)
            .build()
    }

    val repository: TaskRepository by lazy {
        TaskRepository(this, database.templateDao(), database.occurrenceDao())
    }

    val tasksPreferencesRepository: TasksPreferencesRepository by lazy {
        TasksPreferencesRepository(this)
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize notifications channel and day boundary check
        com.example.doora.alarm.AlarmHelper.createNotificationChannel(this)
    }
}
