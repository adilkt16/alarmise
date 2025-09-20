package com.alarmise.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.alarmise.app.data.model.Alarm
import com.alarmise.app.receiver.AlarmReceiver
import com.alarmise.app.receiver.AutoStopReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AlarmScheduler handles system-level alarm scheduling using AlarmManager
 * This is critical for ensuring alarms trigger even when app is closed
 * Implements the core requirement: persistent alarm regardless of app state
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    
    companion object {
        private const val START_ALARM_REQUEST_CODE_BASE = 10000
        private const val STOP_ALARM_REQUEST_CODE_BASE = 20000
    }
    
    /**
     * Schedule an alarm with the system AlarmManager
     * This ensures the alarm triggers even if the app is closed
     */
    fun scheduleAlarm(alarm: Alarm): Result<Unit> {
        return try {
            // Schedule start alarm
            scheduleStartAlarm(alarm)
            
            // Schedule auto-stop alarm
            scheduleAutoStopAlarm(alarm)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Cancel a scheduled alarm
     */
    fun cancelAlarm(alarmId: Long): Result<Unit> {
        return try {
            cancelStartAlarm(alarmId)
            cancelAutoStopAlarm(alarmId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Schedule the alarm start trigger
     */
    private fun scheduleStartAlarm(alarm: Alarm) {
        val startIntent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
        }
        
        val startPendingIntent = PendingIntent.getBroadcast(
            context,
            (START_ALARM_REQUEST_CODE_BASE + alarm.id).toInt(),
            startIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val startTimeMillis = getNextAlarmTimeMillis(alarm.startTime)
        
        // Use exact alarm for precision (critical requirement)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                startTimeMillis,
                startPendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                startTimeMillis,
                startPendingIntent
            )
        }
    }
    
    /**
     * Schedule the auto-stop trigger
     * Critical safety feature: ensures alarm doesn't play indefinitely
     */
    private fun scheduleAutoStopAlarm(alarm: Alarm) {
        val stopIntent = Intent(context, AutoStopReceiver::class.java).apply {
            putExtra(AutoStopReceiver.EXTRA_ALARM_ID, alarm.id)
        }
        
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            (STOP_ALARM_REQUEST_CODE_BASE + alarm.id).toInt(),
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopTimeMillis = getNextAlarmTimeMillis(alarm.endTime)
        
        // Schedule auto-stop
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                stopTimeMillis,
                stopPendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                stopTimeMillis,
                stopPendingIntent
            )
        }
    }
    
    /**
     * Cancel the start alarm
     */
    private fun cancelStartAlarm(alarmId: Long) {
        val startIntent = Intent(context, AlarmReceiver::class.java)
        val startPendingIntent = PendingIntent.getBroadcast(
            context,
            (START_ALARM_REQUEST_CODE_BASE + alarmId).toInt(),
            startIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.cancel(startPendingIntent)
    }
    
    /**
     * Cancel the auto-stop alarm
     */
    private fun cancelAutoStopAlarm(alarmId: Long) {
        val stopIntent = Intent(context, AutoStopReceiver::class.java)
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            (STOP_ALARM_REQUEST_CODE_BASE + alarmId).toInt(),
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.cancel(stopPendingIntent)
    }
    
    /**
     * Calculate the next occurrence of the given time
     * Handles both same-day and next-day scenarios
     */
    private fun getNextAlarmTimeMillis(time: LocalTime): Long {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        var alarmTime = now.with(time)
        
        // If the time has already passed today, schedule for tomorrow
        if (alarmTime.isBefore(now)) {
            alarmTime = alarmTime.plusDays(1)
        }
        
        return alarmTime.toInstant().toEpochMilli()
    }
    
    /**
     * Check if exact alarms can be scheduled
     * Important for Android 12+ devices
     */
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }
    
    /**
     * Request exact alarm permission (Android 12+)
     */
    fun requestExactAlarmPermission(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                }
            } else null
        } else null
    }
    
    /**
     * Reschedule all active alarms
     * Used after device boot or app update
     */
    suspend fun rescheduleAllAlarms(alarms: List<Alarm>): Result<Unit> {
        return try {
            alarms.filter { it.isActive && it.isEnabled }.forEach { alarm ->
                scheduleAlarm(alarm)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get info about next scheduled alarm
     */
    fun getNextAlarmInfo(): AlarmManager.AlarmClockInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            alarmManager.nextAlarmClock
        } else {
            null
        }
    }
}
