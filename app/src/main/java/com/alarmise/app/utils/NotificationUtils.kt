package com.alarmise.app.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.alarmise.app.R
import com.alarmise.app.ui.activity.AlarmTriggerActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NotificationManager handles all notification-related operations for Alarmise
 * Critical for maintaining foreground service and alarm visibility
 */
@Singleton
class NotificationUtils @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        const val ALARM_CHANNEL_ID = "alarm_channel"
        const val SERVICE_CHANNEL_ID = "service_channel"
        const val GENERAL_CHANNEL_ID = "general_channel"
        
        const val ALARM_NOTIFICATION_ID = 1001
        const val SERVICE_NOTIFICATION_ID = 1002
    }
    
    private val notificationManager = 
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    init {
        createNotificationChannels()
    }
    
    /**
     * Create notification channels for different types of notifications
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            
            // High priority channel for active alarms
            val alarmChannel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "Active Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for active alarms"
                enableVibration(true)
                setSound(null, null) // Sound handled by AlarmService
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            
            // Default channel for foreground service
            val serviceChannel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "Alarm Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background alarm service"
                enableVibration(false)
                setSound(null, null)
            }
            
            // General notifications
            val generalChannel = NotificationChannel(
                GENERAL_CHANNEL_ID,
                "General",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General app notifications"
            }
            
            notificationManager.createNotificationChannels(
                listOf(alarmChannel, serviceChannel, generalChannel)
            )
        }
    }
    
    /**
     * Create notification for active alarm
     * High priority to ensure visibility during alarm
     */
    fun createAlarmNotification(
        alarmLabel: String,
        startTime: String,
        endTime: String
    ): android.app.Notification {
        
        val intent = Intent(context, AlarmTriggerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setContentTitle("🚨 $alarmLabel")
            .setContentText("Solve math puzzle to stop • $startTime - $endTime")
            .setSmallIcon(R.drawable.ic_alarm_24)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColorized(true)
            .setColor(ContextCompat.getColor(context, R.color.alarm_background))
            .build()
    }
    
    /**
     * Create notification for foreground service
     */
    fun createServiceNotification(): android.app.Notification {
        return NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setContentTitle("Alarmise")
            .setContentText("Monitoring for alarm triggers")
            .setSmallIcon(R.drawable.ic_alarm_24)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }
    
    /**
     * Create notification for foreground service with custom content
     */
    fun createServiceNotification(title: String, content: String): android.app.Notification {
        return NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_alarm_24)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }
    
    /**
     * Show alarm notification
     */
    fun showAlarmNotification(
        alarmLabel: String,
        startTime: String,
        endTime: String
    ) {
        val notification = createAlarmNotification(alarmLabel, startTime, endTime)
        notificationManager.notify(ALARM_NOTIFICATION_ID, notification)
    }
    
    /**
     * Cancel alarm notification
     */
    fun cancelAlarmNotification() {
        notificationManager.cancel(ALARM_NOTIFICATION_ID)
    }
    
    /**
     * Check if notifications are enabled
     */
    fun areNotificationsEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            notificationManager.areNotificationsEnabled()
        } else {
            true
        }
    }
}

/**
 * PermissionUtils handles all permission-related operations
 * Critical for ensuring app can function properly with required permissions
 */
@Singleton
class PermissionUtils @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    /**
     * Check if all required permissions are granted
     */
    fun areAllPermissionsGranted(): Boolean {
        return getRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Get list of required permissions
     */
    fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        
        // Basic permissions
        permissions.add(Manifest.permission.WAKE_LOCK)
        permissions.add(Manifest.permission.RECEIVE_BOOT_COMPLETED)
        permissions.add(Manifest.permission.FOREGROUND_SERVICE)
        permissions.add(Manifest.permission.VIBRATE)
        permissions.add(Manifest.permission.MODIFY_AUDIO_SETTINGS)
        
        // Android version specific permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.SCHEDULE_EXACT_ALARM)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            permissions.add(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        }
        
        return permissions
    }
    
    /**
     * Get list of missing permissions
     */
    fun getMissingPermissions(): List<String> {
        return getRequiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Check if specific permission is granted
     */
    fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Check if battery optimization is disabled
     * Critical for background alarm functionality
     */
    fun isBatteryOptimizationDisabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }
    
    /**
     * Get intent to request battery optimization exemption
     */
    fun getBatteryOptimizationIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
        } else {
            null
        }
    }
    
    /**
     * Check if Do Not Disturb permission is granted
     */
    fun hasDoNotDisturbPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.isNotificationPolicyAccessGranted
        } else {
            true
        }
    }
    
    /**
     * Get intent to request Do Not Disturb access
     */
    fun getDoNotDisturbIntent(): Intent {
        return Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    }
}
