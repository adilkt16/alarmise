package com.alarmise.app.data.repository

import com.alarmise.app.data.database.AlarmDao
import com.alarmise.app.data.database.AlarmLogDao
import com.alarmise.app.data.model.Alarm
import com.alarmise.app.data.model.AlarmLog
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmRepository @Inject constructor(
    private val alarmDao: AlarmDao,
    private val alarmLogDao: AlarmLogDao
) {
    
    // Alarm operations
    fun getAllAlarms(): Flow<List<Alarm>> = alarmDao.getAllAlarms()
    
    fun getEnabledAlarms(): Flow<List<Alarm>> = alarmDao.getEnabledAlarms()
    
    suspend fun getAlarmById(id: Long): Alarm? = alarmDao.getAlarmById(id)
    
    suspend fun getActiveAlarm(): Alarm? = alarmDao.getActiveAlarm()
    
    suspend fun insertAlarm(alarm: Alarm): Long = alarmDao.insertAlarm(alarm)
    
    suspend fun updateAlarm(alarm: Alarm) = alarmDao.updateAlarm(alarm)
    
    suspend fun deleteAlarm(alarm: Alarm) = alarmDao.deleteAlarm(alarm)
    
    suspend fun deleteAlarmById(id: Long) = alarmDao.deleteAlarmById(id)
    
    suspend fun deactivateAllAlarms() = alarmDao.deactivateAllAlarms()
    
    suspend fun activateAlarm(id: Long) = alarmDao.activateAlarm(id)
    
    suspend fun setAlarmEnabled(id: Long, enabled: Boolean) = alarmDao.setAlarmEnabled(id, enabled)
    
    // Alarm log operations
    fun getAllLogs(): Flow<List<AlarmLog>> = alarmLogDao.getAllLogs()
    
    fun getLogsForAlarm(alarmId: Long): Flow<List<AlarmLog>> = alarmLogDao.getLogsForAlarm(alarmId)
    
    suspend fun getActiveLog(): AlarmLog? = alarmLogDao.getActiveLog()
    
    suspend fun insertLog(log: AlarmLog): Long = alarmLogDao.insertLog(log)
    
    suspend fun updateLog(log: AlarmLog) = alarmLogDao.updateLog(log)
    
    suspend fun stopLog(id: Long, stoppedAt: Long, stoppedBy: AlarmLog.StoppedBy) = 
        alarmLogDao.stopLog(id, stoppedAt, stoppedBy)
}
