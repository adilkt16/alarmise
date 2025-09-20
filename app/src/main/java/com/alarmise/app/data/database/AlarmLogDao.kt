package com.alarmise.app.data.database

import androidx.room.*
import com.alarmise.app.data.model.AlarmLog
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmLogDao {
    
    @Query("SELECT * FROM alarm_logs ORDER BY startedAt DESC")
    fun getAllLogs(): Flow<List<AlarmLog>>
    
    @Query("SELECT * FROM alarm_logs WHERE alarmId = :alarmId ORDER BY startedAt DESC")
    fun getLogsForAlarm(alarmId: Long): Flow<List<AlarmLog>>
    
    @Query("SELECT * FROM alarm_logs WHERE stoppedAt IS NULL LIMIT 1")
    suspend fun getActiveLog(): AlarmLog?
    
    @Insert
    suspend fun insertLog(log: AlarmLog): Long
    
    @Update
    suspend fun updateLog(log: AlarmLog)
    
    @Delete
    suspend fun deleteLog(log: AlarmLog)
    
    @Query("DELETE FROM alarm_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)
    
    @Query("UPDATE alarm_logs SET stoppedAt = :stoppedAt, stoppedBy = :stoppedBy WHERE id = :id")
    suspend fun stopLog(id: Long, stoppedAt: Long, stoppedBy: AlarmLog.StoppedBy)
}
