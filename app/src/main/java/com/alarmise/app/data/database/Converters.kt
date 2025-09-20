package com.alarmise.app.data.database

import androidx.room.TypeConverter
import java.time.LocalTime

class Converters {
    
    @TypeConverter
    fun fromLocalTime(time: LocalTime?): String? {
        return time?.toString()
    }
    
    @TypeConverter
    fun toLocalTime(timeString: String?): LocalTime? {
        return timeString?.let { LocalTime.parse(it) }
    }
}
