package com.example.doora.data.db

import androidx.room.TypeConverter
import com.example.doora.data.model.OccurrenceStatus
import com.example.doora.data.model.Priority
import com.example.doora.data.model.RecurrenceType

class Converters {
    @TypeConverter fun priorityToString(p: Priority): String = p.name
    @TypeConverter fun stringToPriority(s: String): Priority = Priority.valueOf(s)

    @TypeConverter fun statusToString(s: OccurrenceStatus): String = s.name
    @TypeConverter fun stringToStatus(s: String): OccurrenceStatus = OccurrenceStatus.valueOf(s)

    @TypeConverter fun recurrenceToString(r: RecurrenceType): String = r.name
    @TypeConverter fun stringToRecurrence(s: String): RecurrenceType = RecurrenceType.valueOf(s)
}
