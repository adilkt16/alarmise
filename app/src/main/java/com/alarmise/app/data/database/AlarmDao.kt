package com.alarmise.app.data.database

import androidx.room.*
import com.alarmise.app.data.model.Alarm
import com.alarmise.app.data.model.AlarmState
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    
    @Query("SELECT * FROM alarms ORDER BY startTime ASC")
    fun getAllAlarms(): Flow<List<Alarm>>
    
    @Query("SELECT * FROM alarms WHERE isEnabled = 1 ORDER BY startTime ASC")
    fun getEnabledAlarms(): Flow<List<Alarm>>
    
    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getAlarmById(id: Long): Alarm?
    
    @Query("SELECT * FROM alarms WHERE state = 'ACTIVE' LIMIT 1")
    suspend fun getActiveAlarm(): Alarm?
    
    @Query("SELECT * FROM alarms WHERE state = :state")
    suspend fun getAlarmsByState(state: AlarmState): List<Alarm>
    
    @Insert
    suspend fun insertAlarm(alarm: Alarm): Long
    
    @Update
    suspend fun updateAlarm(alarm: Alarm)
    
    @Delete
    suspend fun deleteAlarm(alarm: Alarm)
    
    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun deleteAlarmById(id: Long)
    
    @Query("UPDATE alarms SET state = 'CANCELLED' WHERE state = 'SCHEDULED' OR state = 'ACTIVE'")
    suspend fun deactivateAllAlarms()
    
    @Query("UPDATE alarms SET state = 'ACTIVE' WHERE id = :id")
    suspend fun activateAlarm(id: Long)
    
    @Query("UPDATE alarms SET isEnabled = :enabled WHERE id = :id")
    suspend fun setAlarmEnabled(id: Long, enabled: Boolean)
}
