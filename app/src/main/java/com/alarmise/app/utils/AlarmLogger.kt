package com.alarmise.app.utils

import android.util.Log
import com.alarmise.app.data.model.Alarm
import com.alarmise.app.data.model.AlarmState
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Comprehensive logging system for debugging alarm scheduling issues
 * Provides structured logging for state transitions, timing calculations, and system interactions
 */
object AlarmLogger {
    
    private const val TAG = "AlarmiseScheduler"
    
    // Log levels
    private const val VERBOSE = Log.VERBOSE
    private const val DEBUG = Log.DEBUG
    private const val INFO = Log.INFO
    private const val WARN = Log.WARN
    private const val ERROR = Log.ERROR
    
    /**
     * Log alarm scheduling events
     */
    fun logScheduling(alarmId: Long, startTime: LocalTime, endTime: LocalTime, nextTriggerTime: ZonedDateTime) {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
        val message = """
            |📅 SCHEDULING ALARM
            |├─ Alarm ID: $alarmId
            |├─ Start Time: $startTime
            |├─ End Time: $endTime
            |├─ Next Trigger: ${nextTriggerTime.format(formatter)}
            |└─ Timezone: ${nextTriggerTime.zone}
        """.trimMargin()
        
        Log.i(TAG, message)
    }
    
    /**
     * Log alarm state transitions
     */
    fun logStateTransition(alarmId: Long, fromState: AlarmState, toState: AlarmState, reason: String?) {
        val reasonText = reason?.let { " (Reason: $it)" } ?: ""
        val message = """
            |🔄 STATE TRANSITION
            |├─ Alarm ID: $alarmId
            |├─ From: ${fromState.getDescription()}
            |├─ To: ${toState.getDescription()}
            |└─ Reason: ${reason ?: "Not specified"}
        """.trimMargin()
        
        Log.i(TAG, message)
    }
    
    /**
     * Log alarm trigger events
     */
    fun logAlarmTrigger(alarm: Alarm, actualTriggerTime: ZonedDateTime) {
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        val message = """
            |🚨 ALARM TRIGGERED
            |├─ Alarm ID: ${alarm.id}
            |├─ Label: ${alarm.label}
            |├─ Expected: ${alarm.startTime.format(formatter)}
            |├─ Actual: ${actualTriggerTime.format(formatter)}
            |└─ State: ${alarm.state.getDescription()}
        """.trimMargin()
        
        Log.i(TAG, message)
    }
    
    /**
     * Log alarm expiration events
     */
    fun logAlarmExpiration(alarmId: Long, endTime: LocalTime, actualExpirationTime: ZonedDateTime) {
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        val message = """
            |⏰ ALARM EXPIRED
            |├─ Alarm ID: $alarmId
            |├─ Expected End: ${endTime.format(formatter)}
            |├─ Actual End: ${actualExpirationTime.format(formatter)}
            |└─ Auto-stopped due to time limit
        """.trimMargin()
        
        Log.i(TAG, message)
    }
    
    /**
     * Log timezone-related calculations
     */
    fun logTimezoneCalculation(localTime: LocalTime, zonedTime: ZonedDateTime, timezone: String) {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
        val message = """
            |🌍 TIMEZONE CALCULATION
            |├─ Local Time: $localTime
            |├─ Zoned Time: ${zonedTime.format(formatter)}
            |└─ Timezone: $timezone
        """.trimMargin()
        
        Log.d(TAG, message)
    }
    
    /**
     * Log edge case handling (same day vs next day)
     */
    fun logEdgeCaseHandling(alarmId: Long, startTime: LocalTime, endTime: LocalTime, decision: String) {
        val message = """
            |🎯 EDGE CASE HANDLING
            |├─ Alarm ID: $alarmId
            |├─ Start: $startTime
            |├─ End: $endTime
            |└─ Decision: $decision
        """.trimMargin()
        
        Log.d(TAG, message)
    }
    
    /**
     * Log permission-related events
     */
    fun logPermissionCheck(permission: String, granted: Boolean) {
        val status = if (granted) "✅ GRANTED" else "❌ DENIED"
        val message = """
            |🔐 PERMISSION CHECK
            |├─ Permission: $permission
            |└─ Status: $status
        """.trimMargin()
        
        Log.i(TAG, message)
    }
    
    /**
     * Log system events (boot, app restart, etc.)
     */
    fun logSystemEvent(event: String, details: Map<String, Any> = emptyMap()) {
        val detailsText = details.entries.joinToString("\n") { "├─ ${it.key}: ${it.value}" }
        val message = """
            |🔧 SYSTEM EVENT
            |├─ Event: $event
            |$detailsText
            |└─ Timestamp: ${System.currentTimeMillis()}
        """.trimMargin()
        
        Log.i(TAG, message)
    }
    
    /**
     * Log errors with context
     */
    fun logError(operation: String, alarmId: Long?, error: Throwable, context: Map<String, Any> = emptyMap()) {
        val alarmText = alarmId?.let { "Alarm ID: $it" } ?: "No alarm context"
        val contextText = context.entries.joinToString("\n") { "├─ ${it.key}: ${it.value}" }
        val message = """
            |❌ ERROR
            |├─ Operation: $operation
            |├─ $alarmText
            |$contextText
            |├─ Error: ${error.message}
            |└─ Stack trace will follow
        """.trimMargin()
        
        Log.e(TAG, message, error)
    }
    
    /**
     * Log warning events
     */
    fun logWarning(operation: String, warning: String, alarmId: Long? = null) {
        val alarmText = alarmId?.let { "Alarm ID: $it" } ?: ""
        val message = """
            |⚠️ WARNING
            |├─ Operation: $operation
            |${if (alarmText.isNotEmpty()) "├─ $alarmText" else ""}
            |└─ Warning: $warning
        """.trimMargin()
        
        Log.w(TAG, message)
    }
    
    /**
     * Log debug information for development
     */
    fun logDebug(operation: String, details: Map<String, Any>) {
        val detailsText = details.entries.joinToString("\n") { "├─ ${it.key}: ${it.value}" }
        val message = """
            |🔍 DEBUG
            |├─ Operation: $operation
            |$detailsText
            |└─ Timestamp: ${System.currentTimeMillis()}
        """.trimMargin()
        
        Log.d(TAG, message)
    }
    
    /**
     * Log successful operations
     */
    fun logSuccess(operation: String, alarmId: Long? = null, details: String? = null) {
        val alarmText = alarmId?.let { "Alarm ID: $it" } ?: ""
        val detailsText = details?.let { "Details: $it" } ?: ""
        val message = """
            |✅ SUCCESS
            |├─ Operation: $operation
            |${if (alarmText.isNotEmpty()) "├─ $alarmText" else ""}
            |${if (detailsText.isNotEmpty()) "└─ $detailsText" else ""}
        """.trimMargin()
        
        Log.i(TAG, message)
    }
    
    /**
     * Create a session header for major operations
     */
    fun logSessionStart(operation: String) {
        val separator = "═".repeat(50)
        val message = """
            |$separator
            |🚀 STARTING: $operation
            |📅 Time: ${ZonedDateTime.now()}
            |$separator
        """.trimMargin()
        
        Log.i(TAG, message)
    }
    
    /**
     * Create a session footer for major operations
     */
    fun logSessionEnd(operation: String, success: Boolean) {
        val status = if (success) "✅ COMPLETED" else "❌ FAILED"
        val separator = "═".repeat(50)
        val message = """
            |$separator
            |🏁 $status: $operation
            |📅 Time: ${ZonedDateTime.now()}
            |$separator
        """.trimMargin()
        
        Log.i(TAG, message)
    }
}

/**
 * Extension functions for easier logging
 */
fun Alarm.logStateChange(toState: AlarmState, reason: String? = null) {
    AlarmLogger.logStateTransition(this.id, this.state, toState, reason)
}

fun Alarm.logScheduling(nextTriggerTime: ZonedDateTime) {
    AlarmLogger.logScheduling(this.id, this.startTime, this.endTime, nextTriggerTime)
}

fun Alarm.logTrigger(actualTriggerTime: ZonedDateTime) {
    AlarmLogger.logAlarmTrigger(this, actualTriggerTime)
}