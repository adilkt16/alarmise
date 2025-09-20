package com.alarmise.app.data.database

import androidx.room.*
import com.alarmise.app.data.model.Alarm
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    
    @Query("SELECT * FROM alarms ORDER BY startTime ASC")
    fun getAllAlarms(): Flow<List<Alarm>>
    
    @Query("SELECT * FROM alarms WHERE isEnabled = 1 ORDER BY startTime ASC")
    fun getEnabledAlarms(): Flow<List<Alarm>>
    
    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getAlarmById(id: Long): Alarm?
    
    @Query("SELECT * FROM alarms WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveAlarm(): Alarm?
    
    @Insert
    suspend fun insertAlarm(alarm: Alarm): Long
    
    @Update
    suspend fun updateAlarm(alarm: Alarm)
    
    @Delete
    suspend fun deleteAlarm(alarm: Alarm)
    
    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun deleteAlarmById(id: Long)
    
    @Query("UPDATE alarms SET isActive = 0")
    suspend fun deactivateAllAlarms()
    
    @Query("UPDATE alarms SET isActive = 1 WHERE id = :id")
    suspend fun activateAlarm(id: Long)
    
    @Query("UPDATE alarms SET isEnabled = :enabled WHERE id = :id")
    suspend fun setAlarmEnabled(id: Long, enabled: Boolean)
}
