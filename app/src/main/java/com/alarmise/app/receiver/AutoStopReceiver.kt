package com.alarmise.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alarmise.app.data.model.AlarmState
import com.alarmise.app.data.repository.AlarmRepository
import com.alarmise.app.service.AlarmService
import com.alarmise.app.utils.AlarmLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * AutoStopReceiver handles automatic alarm stopping at end time
 * Critical safety feature: ensures alarm doesn't play indefinitely
 * Implements the core requirement: time-bounded operation
 */
@AndroidEntryPoint
class AutoStopReceiver : BroadcastReceiver() {
    
    @Inject
    lateinit var alarmRepository: AlarmRepository
    
    companion object {
        const val EXTRA_ALARM_ID = "extra_alarm_id"
    }
    
    // Use a coroutine scope for async operations
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        
        try {
            val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
            val alarmLabel = intent.getStringExtra("alarm_label") ?: "Unknown"
            val endTime = intent.getStringExtra("end_time") ?: "Unknown"
            
            if (alarmId == -1L) {
                AlarmLogger.logWarning("Auto-Stop Receiver", "Invalid alarm ID received", alarmId)
                pendingResult.finish()
                return
            }
            
            AlarmLogger.logSessionStart("Auto-Stop Alarm $alarmId")
            AlarmLogger.logSystemEvent("Auto-Stop Triggered", mapOf(
                "alarmId" to alarmId,
                "alarmLabel" to alarmLabel,
                "scheduledEndTime" to endTime,
                "actualTime" to ZonedDateTime.now().toString()
            ))
            
            handleAutoStop(context, alarmId, alarmLabel, pendingResult)
            
        } catch (e: Exception) {
            AlarmLogger.logError("Auto-Stop Receiver", null, e)
            pendingResult.finish()
        }
    }
    
    /**
     * Handle the auto-stop process
     */
    private fun handleAutoStop(
        context: Context,
        alarmId: Long,
        alarmLabel: String,
        pendingResult: BroadcastReceiver.PendingResult
    ) {
        scope.launch {
            try {
                // Get the alarm from database to verify state
                val alarm = alarmRepository.getById(alarmId)
                
                if (alarm == null) {
                    AlarmLogger.logWarning("Auto-Stop Process", "Alarm not found in database", alarmId)
                    pendingResult.finish()
                    return@launch
                }
                
                AlarmLogger.logAlarmExpiration(alarmId, alarm.endTime, ZonedDateTime.now())
                
                // Update alarm state to EXPIRED
                val expiredAlarm = alarm.withStateTransition(
                    AlarmState.EXPIRED, 
                    "Auto-stopped at end time - safety mechanism activated"
                )
                
                val updateResult = alarmRepository.update(expiredAlarm)
                if (updateResult.isFailure) {
                    AlarmLogger.logError("Auto-Stop State Update", alarmId, 
                        updateResult.exceptionOrNull() ?: Exception("Unknown database error"))
                }
                
                // Stop the alarm service if it's running
                stopAlarmService(context, alarmId)
                
                // Log the successful auto-stop
                AlarmLogger.logSuccess("Auto-Stop Process", alarmId, 
                    "Alarm auto-stopped at end time: ${alarm.endTime}")
                
                // Clean up any remaining notifications or wake locks
                cleanupAlarmResources(context, alarmId)
                
                AlarmLogger.logSessionEnd("Auto-Stop Alarm $alarmId", true)
                
            } catch (e: Exception) {
                AlarmLogger.logError("Auto-Stop Process", alarmId, e)
                AlarmLogger.logSessionEnd("Auto-Stop Alarm $alarmId", false)
            } finally {
                pendingResult.finish()
            }
        }
    }
    
    /**
     * Stop the alarm service
     */
    private fun stopAlarmService(context: Context, alarmId: Long) {
        try {
            val serviceIntent = Intent(context, AlarmService::class.java).apply {
                action = AlarmService.ACTION_STOP_ALARM
                putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
            }
            
            // Stop the service
            context.stopService(serviceIntent)
            
            AlarmLogger.logDebug("Stop Alarm Service", mapOf(
                "alarmId" to alarmId,
                "action" to AlarmService.ACTION_STOP_ALARM
            ))
            
        } catch (e: Exception) {
            AlarmLogger.logError("Stop Alarm Service", alarmId, e)
        }
    }
    
    /**
     * Clean up any remaining resources
     */
    private fun cleanupAlarmResources(context: Context, alarmId: Long) {
        try {
            // Cancel any notifications
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) 
                as android.app.NotificationManager
            notificationManager.cancel(alarmId.toInt())
            
            AlarmLogger.logDebug("Cleanup Resources", mapOf(
                "alarmId" to alarmId,
                "cleanedNotifications" to true
            ))
            
        } catch (e: Exception) {
            AlarmLogger.logError("Cleanup Resources", alarmId, e)
        }
    }
    
    /**
     * Validate that auto-stop was necessary
     * Helps debug timing issues
     */
    private fun validateAutoStop(alarmId: Long, actualEndTime: ZonedDateTime): Boolean {
        try {
            // Check if the auto-stop happened at the right time
            // This could be expanded to validate timing accuracy
            AlarmLogger.logDebug("Auto-Stop Validation", mapOf(
                "alarmId" to alarmId,
                "actualEndTime" to actualEndTime.toString(),
                "systemTime" to System.currentTimeMillis()
            ))
            
            return true
        } catch (e: Exception) {
            AlarmLogger.logError("Auto-Stop Validation", alarmId, e)
            return false
        }
    }
}
