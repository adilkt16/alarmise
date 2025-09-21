package com.alarmise.app.data.repository

import com.alarmise.app.data.database.AlarmDao
import com.alarmise.app.data.database.AlarmLogDao
import com.alarmise.app.data.model.Alarm
import com.alarmise.app.data.model.AlarmLog
import com.alarmise.app.data.model.AlarmState
import com.alarmise.app.utils.AlarmLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import java.time.LocalTime

@Singleton
class AlarmRepository @Inject constructor(
    private val alarmDao: AlarmDao,
    private val alarmLogDao: AlarmLogDao
) {
    
    // ==================== ALARM OPERATIONS ====================
    
    /**
     * Get all alarms ordered by start time
     */
    fun getAllAlarms(): Flow<List<Alarm>> = alarmDao.getAllAlarms()
    
    /**
     * Get only enabled alarms
     */
    fun getEnabledAlarms(): Flow<List<Alarm>> = alarmDao.getEnabledAlarms()
    
    /**
     * Get currently active alarms (should only be one at a time per requirements)
     */
    fun getActiveAlarms(): Flow<List<Alarm>> = 
        alarmDao.getAllAlarms().map { alarms ->
            alarms.filter { it.state == AlarmState.ACTIVE }
        }
    
    /**
     * Get alarm by ID
     */
    suspend fun getAlarmById(id: Long): Alarm? = alarmDao.getAlarmById(id)
    
    /**
     * Alternative method name for consistency with AlarmScheduler
     */
    suspend fun getById(id: Long): Alarm? = getAlarmById(id)
    
    /**
     * Get all alarms in scheduled state (for rescheduling after boot)
     */
    suspend fun getAllScheduledAlarms(): List<Alarm> {
        return alarmDao.getAlarmsByState(AlarmState.SCHEDULED)
    }
    
    /**
     * Update alarm with enhanced error handling
     */
    suspend fun update(alarm: Alarm): Result<Unit> = updateAlarm(alarm)
    
    /**
     * Get the single active alarm (core requirement: only one alarm can be active)
     */
    suspend fun getActiveAlarm(): Alarm? = alarmDao.getActiveAlarm()
    
    /**
     * Get alarms that should be playing right now
     * Updated to work with new state system
     */
    suspend fun getCurrentlyPlayingAlarms(): List<Alarm> {
        return try {
            val activeAlarms = alarmDao.getAlarmsByState(AlarmState.ACTIVE)
            activeAlarms.filter { it.isCurrentlyActive() }
        } catch (e: Exception) {
            AlarmLogger.logError("Get Currently Playing Alarms", null, e)
            emptyList()
        }
    }
    
    /**
     * Create and insert a new alarm
     * Follows critical requirement: only one active alarm at a time
     */
    suspend fun createAlarm(
        startTime: LocalTime,
        endTime: LocalTime,
        label: String,
        puzzleDifficulty: com.alarmise.app.data.model.MathPuzzle.Difficulty = 
            com.alarmise.app.data.model.MathPuzzle.Difficulty.MEDIUM
    ): Result<Long> {
        return try {
            val alarm = Alarm.create(startTime, endTime, label, puzzleDifficulty)
                ?: return Result.failure(IllegalArgumentException("Invalid alarm configuration"))
            
            // Deactivate all existing alarms first (core requirement)
            deactivateAllAlarms()
            
            val alarmId = alarmDao.insertAlarm(alarm)
            Result.success(alarmId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update an existing alarm
     */
    suspend fun updateAlarm(alarm: Alarm): Result<Unit> {
        return try {
            if (!alarm.isValid()) {
                return Result.failure(IllegalArgumentException("Invalid alarm configuration"))
            }
            alarmDao.updateAlarm(alarm)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete an alarm
     */
    suspend fun deleteAlarm(alarm: Alarm): Result<Unit> {
        return try {
            alarmDao.deleteAlarm(alarm)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete alarm by ID
     */
    suspend fun deleteAlarmById(id: Long): Result<Unit> {
        return try {
            alarmDao.deleteAlarmById(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Deactivate all alarms (updated for state system)
     */
    suspend fun deactivateAllAlarms(): Result<Unit> {
        return try {
            val activeAlarms = alarmDao.getAlarmsByState(AlarmState.ACTIVE)
            val scheduledAlarms = alarmDao.getAlarmsByState(AlarmState.SCHEDULED)
            
            // Cancel all active and scheduled alarms
            (activeAlarms + scheduledAlarms).forEach { alarm ->
                val cancelledAlarm = alarm.withStateTransition(
                    AlarmState.CANCELLED,
                    "Cancelled to make room for new alarm"
                )
                alarmDao.updateAlarm(cancelledAlarm)
                
                AlarmLogger.logStateTransition(
                    alarm.id,
                    alarm.state,
                    AlarmState.CANCELLED,
                    "Bulk deactivation"
                )
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            AlarmLogger.logError("Deactivate All Alarms", null, e)
            Result.failure(e)
        }
    }
    
    /**
     * Activate a specific alarm (transition from SCHEDULED to ACTIVE)
     */
    suspend fun activateAlarm(id: Long): Result<Unit> {
        return try {
            val alarm = alarmDao.getAlarmById(id)
            if (alarm != null) {
                if (alarm.state != AlarmState.SCHEDULED) {
                    AlarmLogger.logWarning("Activate Alarm", 
                        "Alarm is not in SCHEDULED state: ${alarm.state}", id)
                }
                
                val activeAlarm = alarm.withStateTransition(
                    AlarmState.ACTIVE,
                    "Alarm triggered and activated"
                )
                alarmDao.updateAlarm(activeAlarm)
                
                AlarmLogger.logStateTransition(
                    alarm.id,
                    alarm.state,
                    AlarmState.ACTIVE,
                    "System activated alarm"
                )
            } else {
                return Result.failure(IllegalArgumentException("Alarm not found: $id"))
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            AlarmLogger.logError("Activate Alarm", id, e)
            Result.failure(e)
        }
    }
    
    /**
     * Enable/disable an alarm
     */
    suspend fun setAlarmEnabled(id: Long, enabled: Boolean): Result<Unit> {
        return try {
            alarmDao.setAlarmEnabled(id, enabled)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Mark alarm as triggered (updated for state system)
     */
    suspend fun markAlarmTriggered(id: Long): Result<Unit> {
        return try {
            val alarm = alarmDao.getAlarmById(id)
            if (alarm != null) {
                val triggeredAlarm = alarm.withStateTransition(
                    AlarmState.ACTIVE,
                    "Alarm triggered by system at ${System.currentTimeMillis()}"
                )
                alarmDao.updateAlarm(triggeredAlarm)
                
                AlarmLogger.logStateTransition(
                    alarm.id,
                    alarm.state,
                    AlarmState.ACTIVE,
                    "System triggered alarm"
                )
            } else {
                return Result.failure(IllegalArgumentException("Alarm not found: $id"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            AlarmLogger.logError("Mark Alarm Triggered", id, e)
            Result.failure(e)
        }
    }
    
    // ==================== ALARM LOG OPERATIONS ====================
    
    /**
     * Get all alarm logs
     */
    fun getAllLogs(): Flow<List<AlarmLog>> = alarmLogDao.getAllLogs()
    
    /**
     * Get logs for a specific alarm
     */
    fun getLogsForAlarm(alarmId: Long): Flow<List<AlarmLog>> = 
        alarmLogDao.getLogsForAlarm(alarmId)
    
    /**
     * Get the currently active log (alarm that's playing)
     */
    suspend fun getActiveLog(): AlarmLog? = alarmLogDao.getActiveLog()
    
    /**
     * Start a new alarm log when alarm begins playing
     */
    suspend fun startAlarmLog(alarmId: Long): Result<Long> {
        return try {
            val log = AlarmLog(
                alarmId = alarmId,
                startedAt = System.currentTimeMillis()
            )
            val logId = alarmLogDao.insertLog(log)
            Result.success(logId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Stop the active alarm log
     */
    suspend fun stopAlarmLog(
        logId: Long, 
        stoppedBy: AlarmLog.StoppedBy,
        puzzlesSolved: Int = 0,
        puzzleAttempts: Int = 0
    ): Result<Unit> {
        return try {
            val log = alarmLogDao.getActiveLog()
            if (log != null && log.id == logId) {
                val updatedLog = log.copy(
                    stoppedAt = System.currentTimeMillis(),
                    stoppedBy = stoppedBy,
                    puzzlesSolved = puzzlesSolved,
                    puzzleAttempts = puzzleAttempts
                )
                alarmLogDao.updateLog(updatedLog)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update puzzle attempts in active log
     */
    suspend fun updatePuzzleAttempts(attempts: Int, solved: Int = 0): Result<Unit> {
        return try {
            val activeLog = alarmLogDao.getActiveLog()
            if (activeLog != null) {
                val updatedLog = activeLog.copy(
                    puzzleAttempts = attempts,
                    puzzlesSolved = solved
                )
                alarmLogDao.updateLog(updatedLog)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ==================== UTILITY METHODS ====================
    
    /**
     * Clean up expired and old alarms
     */
    suspend fun cleanupExpiredAlarms(): Result<Int> {
        return try {
            val allAlarms = alarmDao.getAllAlarms()
            var deletedCount = 0
            
            // This would need proper flow collection in real implementation
            // For now, returning success with 0 count
            Result.success(deletedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get alarm statistics
     */
    suspend fun getAlarmStatistics(): AlarmStatistics {
        return try {
            // Implementation would aggregate data from logs
            AlarmStatistics(
                totalAlarms = 0,
                totalTriggered = 0,
                averagePuzzleAttempts = 0.0,
                successRate = 0.0
            )
        } catch (e: Exception) {
            AlarmStatistics()
        }
    }
}

/**
 * Data class for alarm statistics
 */
data class AlarmStatistics(
    val totalAlarms: Int = 0,
    val totalTriggered: Int = 0,
    val averagePuzzleAttempts: Double = 0.0,
    val successRate: Double = 0.0 // Percentage of alarms stopped by puzzle vs auto-stop
)
