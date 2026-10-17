package com.example.doora.data.repository

import android.content.Context
import android.content.SharedPreferences

enum class TasksViewMode {
    LIST,
    TIMELINE
}

class TasksPreferencesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "doora_tasks_prefs"
        const val KEY_DEFAULT_VIEW_MODE = "default_tasks_view_mode"
    }

    /**
     * Returns the persistent default view preference.
     * Defaults to [TasksViewMode.LIST] for existing and new users.
     */
    fun getDefaultViewMode(): TasksViewMode {
        val name = prefs.getString(KEY_DEFAULT_VIEW_MODE, TasksViewMode.LIST.name)
        return try {
            TasksViewMode.valueOf(name ?: TasksViewMode.LIST.name)
        } catch (_: Exception) {
            TasksViewMode.LIST
        }
    }

    /**
     * Persistently updates the default view preference.
     */
    fun setDefaultViewMode(mode: TasksViewMode) {
        prefs.edit().putString(KEY_DEFAULT_VIEW_MODE, mode.name).apply()
    }
}
