package com.alarmise.app.data.database

import androidx.room.TypeConverter
import com.alarmise.app.data.model.AlarmState
import com.alarmise.app.data.model.AlarmStateTransition
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalTime

class Converters {
    
    private val gson = Gson()
    
    @TypeConverter
    fun fromLocalTime(time: LocalTime?): String? {
        return time?.toString()
    }
    
    @TypeConverter
    fun toLocalTime(timeString: String?): LocalTime? {
        return timeString?.let { LocalTime.parse(it) }
    }
    
    @TypeConverter
    fun fromAlarmState(state: AlarmState): String {
        return state.name
    }
    
    @TypeConverter
    fun toAlarmState(stateName: String): AlarmState {
        return AlarmState.valueOf(stateName)
    }
    
    @TypeConverter
    fun fromAlarmStateTransitionList(transitions: List<AlarmStateTransition>): String {
        return gson.toJson(transitions)
    }
    
    @TypeConverter
    fun toAlarmStateTransitionList(transitionsJson: String): List<AlarmStateTransition> {
        val type = object : TypeToken<List<AlarmStateTransition>>() {}.type
        return gson.fromJson(transitionsJson, type) ?: emptyList()
    }
}
