package com.alarmise.app.utils

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PermissionManager handles all permission-related operations for Alarmise
 * Critical for ensuring proper alarm scheduling functionality
 * Provides user-friendly error handling and fallback strategies
 */
@Singleton
class PermissionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        const val PERMISSION_REQUEST_CODE_NOTIFICATIONS = 1001
        const val PERMISSION_REQUEST_CODE_EXACT_ALARM = 1002
        const val PERMISSION_REQUEST_CODE_BATTERY_OPTIMIZATION = 1003
        const val PERMISSION_REQUEST_CODE_SYSTEM_ALERT_WINDOW = 1004
    }
    
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    
    /**
     * Check if exact alarm permission is granted
     * Critical for Android 12+ devices
     */
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val canSchedule = alarmManager.canScheduleExactAlarms()
            AlarmLogger.logPermissionCheck("SCHEDULE_EXACT_ALARM", canSchedule)
            canSchedule
        } else {
            AlarmLogger.logPermissionCheck("SCHEDULE_EXACT_ALARM", true)
            true
        }
    }
    
    /**
     * Request exact alarm permission
     * Returns intent to open settings or null if permission already granted
     */
    fun requestExactAlarmPermission(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                AlarmLogger.logSystemEvent("Request Exact Alarm Permission", mapOf(
                    "package" to context.packageName,
                    "androidVersion" to Build.VERSION.SDK_INT
                ))
                
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            } else {
                AlarmLogger.logDebug("Permission Request", mapOf(
                    "permission" to "SCHEDULE_EXACT_ALARM",
                    "status" to "Already granted"
                ))
                null
            }
        } else {
            AlarmLogger.logDebug("Permission Request", mapOf(
                "permission" to "SCHEDULE_EXACT_ALARM",
                "status" to "Not required for this Android version"
            ))
            null
        }
    }
    
    /**
     * Check if notification permission is granted
     * Required for Android 13+ devices
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            
            AlarmLogger.logPermissionCheck("POST_NOTIFICATIONS", hasPermission)
            hasPermission
        } else {
            AlarmLogger.logPermissionCheck("POST_NOTIFICATIONS", true)
            true
        }
    }
    
    /**
     * Check if the app is exempt from battery optimization
     * Important for background alarm functionality
     */
    fun isBatteryOptimizationDisabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val isIgnored = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            
            AlarmLogger.logPermissionCheck("BATTERY_OPTIMIZATION_DISABLED", isIgnored)
            isIgnored
        } else {
            AlarmLogger.logPermissionCheck("BATTERY_OPTIMIZATION_DISABLED", true)
            true
        }
    }
    
    /**
     * Request to disable battery optimization
     */
    fun requestDisableBatteryOptimization(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            
            if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                AlarmLogger.logSystemEvent("Request Battery Optimization Exemption", mapOf(
                    "package" to context.packageName
                ))
                
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            } else {
                AlarmLogger.logDebug("Battery Optimization", mapOf(
                    "status" to "Already disabled"
                ))
                null
            }
        } else {
            null
        }
    }
    
    /**
     * Check if system alert window permission is granted
     * Needed for showing alarm over other apps
     */
    fun hasSystemAlertWindowPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val hasPermission = Settings.canDrawOverlays(context)
            AlarmLogger.logPermissionCheck("SYSTEM_ALERT_WINDOW", hasPermission)
            hasPermission
        } else {
            AlarmLogger.logPermissionCheck("SYSTEM_ALERT_WINDOW", true)
            true
        }
    }
    
    /**
     * Request system alert window permission
     */
    fun requestSystemAlertWindowPermission(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                AlarmLogger.logSystemEvent("Request System Alert Window Permission", mapOf(
                    "package" to context.packageName
                ))
                
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            } else {
                AlarmLogger.logDebug("System Alert Window", mapOf(
                    "status" to "Already granted"
                ))
                null
            }
        } else {
            null
        }
    }
    
    /**
     * Check all critical permissions needed for alarm functionality
     */
    fun checkAllCriticalPermissions(): PermissionStatus {
        val exactAlarm = canScheduleExactAlarms()
        val notifications = hasNotificationPermission()
        val batteryOptimization = isBatteryOptimizationDisabled()
        val systemAlertWindow = hasSystemAlertWindowPermission()
        
        val criticalMissing = !exactAlarm || !notifications
        val recommendedMissing = !batteryOptimization || !systemAlertWindow
        
        return PermissionStatus(
            exactAlarm = exactAlarm,
            notifications = notifications,
            batteryOptimization = batteryOptimization,
            systemAlertWindow = systemAlertWindow,
            allCriticalGranted = !criticalMissing,
            allRecommendedGranted = !recommendedMissing
        )
    }
    
    /**
     * Get user-friendly permission explanation
     */
    fun getPermissionExplanation(permission: String): String {
        return when (permission) {
            "SCHEDULE_EXACT_ALARM" -> 
                "Alarmise needs permission to schedule exact alarms to ensure your alarms trigger precisely at the set time."
            
            "POST_NOTIFICATIONS" -> 
                "Alarmise needs notification permission to show alarm notifications and keep the alarm service running."
            
            "BATTERY_OPTIMIZATION" -> 
                "Disabling battery optimization ensures Alarmise can run in the background and trigger alarms even when your device is sleeping."
            
            "SYSTEM_ALERT_WINDOW" -> 
                "System alert window permission allows Alarmise to show the alarm screen over other apps when the alarm triggers."
            
            else -> "This permission is required for proper alarm functionality."
        }
    }
    
    /**
     * Get list of missing critical permissions
     */
    fun getMissingCriticalPermissions(): List<MissingPermission> {
        val missing = mutableListOf<MissingPermission>()
        
        if (!canScheduleExactAlarms()) {
            missing.add(MissingPermission(
                name = "Exact Alarm Scheduling",
                key = "SCHEDULE_EXACT_ALARM",
                explanation = getPermissionExplanation("SCHEDULE_EXACT_ALARM"),
                intent = requestExactAlarmPermission(),
                isCritical = true
            ))
        }
        
        if (!hasNotificationPermission()) {
            missing.add(MissingPermission(
                name = "Notifications",
                key = "POST_NOTIFICATIONS",
                explanation = getPermissionExplanation("POST_NOTIFICATIONS"),
                intent = null, // This needs to be requested from Activity
                isCritical = true
            ))
        }
        
        if (!isBatteryOptimizationDisabled()) {
            missing.add(MissingPermission(
                name = "Battery Optimization Exemption",
                key = "BATTERY_OPTIMIZATION",
                explanation = getPermissionExplanation("BATTERY_OPTIMIZATION"),
                intent = requestDisableBatteryOptimization(),
                isCritical = false
            ))
        }
        
        if (!hasSystemAlertWindowPermission()) {
            missing.add(MissingPermission(
                name = "Display over other apps",
                key = "SYSTEM_ALERT_WINDOW",
                explanation = getPermissionExplanation("SYSTEM_ALERT_WINDOW"),
                intent = requestSystemAlertWindowPermission(),
                isCritical = false
            ))
        }
        
        return missing
    }
    
    /**
     * Log all permission states for debugging
     */
    fun logAllPermissionStates() {
        val status = checkAllCriticalPermissions()
        
        AlarmLogger.logSystemEvent("Permission Status Check", mapOf(
            "exactAlarm" to status.exactAlarm,
            "notifications" to status.notifications,
            "batteryOptimization" to status.batteryOptimization,
            "systemAlertWindow" to status.systemAlertWindow,
            "allCriticalGranted" to status.allCriticalGranted,
            "allRecommendedGranted" to status.allRecommendedGranted,
            "androidVersion" to Build.VERSION.SDK_INT
        ))
    }
}

/**
 * Data class representing overall permission status
 */
data class PermissionStatus(
    val exactAlarm: Boolean,
    val notifications: Boolean,
    val batteryOptimization: Boolean,
    val systemAlertWindow: Boolean,
    val allCriticalGranted: Boolean,
    val allRecommendedGranted: Boolean
)

/**
 * Data class representing a missing permission
 */
data class MissingPermission(
    val name: String,
    val key: String,
    val explanation: String,
    val intent: Intent?,
    val isCritical: Boolean
)