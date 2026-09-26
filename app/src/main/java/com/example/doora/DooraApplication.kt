package com.example.doora

import android.app.Application
import androidx.room.Room
import com.example.doora.data.db.DooraDatabase
import com.example.doora.data.repository.TaskRepository

class DooraApplication : Application() {

    val database: DooraDatabase by lazy {
        Room.databaseBuilder(applicationContext, DooraDatabase::class.java, "doora.db").build()
    }

    val repository: TaskRepository by lazy {
        TaskRepository(database.templateDao(), database.occurrenceDao())
    }
}
