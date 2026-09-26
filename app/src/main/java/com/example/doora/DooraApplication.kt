package com.example.doora

import android.app.Application
import androidx.room.Room
import com.example.doora.data.db.DooraDatabase
import com.example.doora.data.repository.TaskRepository

class DooraApplication : Application() {

    val database: DooraDatabase by lazy {
        Room.databaseBuilder(applicationContext, DooraDatabase::class.java, "doora.db")
            .fallbackToDestructiveMigration(true)
            .build()
    }

    val repository: TaskRepository by lazy {
        TaskRepository(this, database.templateDao(), database.occurrenceDao())
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize notifications channel and day boundary check
        com.example.doora.alarm.AlarmHelper.createNotificationChannel(this)
    }
}
