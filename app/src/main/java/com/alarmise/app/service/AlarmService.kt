package com.alarmise.app.service

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.alarmise.app.R
import com.alarmise.app.data.model.AlarmState
import com.alarmise.app.data.repository.AlarmRepository
import com.alarmise.app.ui.activity.AlarmTriggerActivity
import com.alarmise.app.utils.AlarmLogger
import com.alarmise.app.utils.NotificationUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * Enhanced AlarmService handles alarm playback with state management
 * Critical for ensuring alarms play persistently regardless of app state
 * Implements the core requirements: continuous playback + math puzzle dismissal
 */
@AndroidEntryPoint
class AlarmService : Service() {
    
    @Inject
    lateinit var alarmRepository: AlarmRepository
    
    @Inject
    lateinit var notificationUtils: NotificationUtils
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentAlarmId: Long = -1
    private var isAlarmPlaying = false
    
    companion object {
        const val ACTION_START_ALARM = "com.alarmise.app.START_ALARM"
        const val ACTION_STOP_ALARM = "com.alarmise.app.STOP_ALARM"
        const val ACTION_DISMISS_ALARM = "com.alarmise.app.DISMISS_ALARM"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val NOTIFICATION_ID = 1001
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        AlarmLogger.logSessionStart("Alarm Service Created")
        acquireWakeLock()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val alarmId = intent?.getLongExtra(EXTRA_ALARM_ID, -1) ?: -1
        
        AlarmLogger.logSystemEvent("Alarm Service Command", mapOf(
            "action" to (action ?: "null"),
            "alarmId" to alarmId,
            "startId" to startId,
            "currentlyPlaying" to isAlarmPlaying
        ))
        
        when (action) {
            ACTION_START_ALARM -> {
                if (alarmId != -1L) {
                    startAlarm(alarmId)
                } else {
                    AlarmLogger.logWarning("Alarm Service", "Start alarm requested with invalid ID", alarmId)
                }
            }
            ACTION_STOP_ALARM -> {
                stopAlarm(alarmId, AlarmState.EXPIRED, "Auto-stopped by system")
            }
            ACTION_DISMISS_ALARM -> {
                stopAlarm(alarmId, AlarmState.DISMISSED, "Dismissed by user solving puzzle")
            }
            else -> {
                AlarmLogger.logWarning("Alarm Service", "Unknown action received: $action")
            }
        }
        
        // Return START_STICKY to restart if killed by system (critical for persistence)
        return START_STICKY
    }
    
    /**
     * Start alarm playback with comprehensive state management
     */
    private fun startAlarm(alarmId: Long) {
        serviceScope.launch {
            try {
                AlarmLogger.logSessionStart("Start Alarm $alarmId")
                
                // Get alarm from database
                val alarm = alarmRepository.getById(alarmId)
                if (alarm == null) {
                    AlarmLogger.logError("Start Alarm", alarmId, 
                        IllegalStateException("Alarm not found in database"))
                    stopSelf()
                    return@launch
                }
                
                // Validate alarm state
                if (alarm.state != AlarmState.SCHEDULED) {
                    AlarmLogger.logWarning("Start Alarm", 
                        "Alarm is not in SCHEDULED state: ${alarm.state}", alarmId)
                    // Continue anyway - might be a legitimate trigger
                }
                
                // Check if alarm should actually be playing now
                if (!alarm.shouldTriggerNow() && !alarm.isCurrentlyActive()) {
                    AlarmLogger.logWarning("Start Alarm", 
                        "Alarm triggered outside expected time window", alarmId)
                    // Continue with trigger - system might have delayed it
                }
                
                // Update alarm state to ACTIVE
                val activeAlarm = alarm.withStateTransition(
                    AlarmState.ACTIVE, 
                    "Alarm triggered at ${ZonedDateTime.now()}"
                )
                
                val updateResult = alarmRepository.update(activeAlarm)
                if (updateResult.isFailure) {
                    AlarmLogger.logError("Start Alarm State Update", alarmId, 
                        updateResult.exceptionOrNull() ?: Exception("Database update failed"))
                }
                
                // Set current alarm
                currentAlarmId = alarmId
                
                // Create foreground notification
                createAlarmNotification(activeAlarm)
                
                // Start alarm sound
                startAlarmSound()
                
                // Launch alarm trigger activity
                launchAlarmTriggerActivity(activeAlarm)
                
                // Mark as playing
                isAlarmPlaying = true
                
                AlarmLogger.logSuccess("Start Alarm", alarmId, "Alarm successfully activated and playing")
                AlarmLogger.logSessionEnd("Start Alarm $alarmId", true)
                
            } catch (e: Exception) {
                AlarmLogger.logError("Start Alarm", alarmId, e)
                AlarmLogger.logSessionEnd("Start Alarm $alarmId", false)
                stopSelf()
            }
        }
    }
    
    /**
     * Stop alarm with proper state management
     */
    private fun stopAlarm(alarmId: Long, newState: AlarmState, reason: String) {
        serviceScope.launch {
            try {
                AlarmLogger.logSessionStart("Stop Alarm $alarmId")
                
                // Validate the alarm ID matches current alarm
                if (currentAlarmId != -1L && currentAlarmId != alarmId) {
                    AlarmLogger.logWarning("Stop Alarm", 
                        "Alarm ID mismatch: current=$currentAlarmId, requested=$alarmId")
                }
                
                // Get alarm from database
                val alarm = alarmRepository.getById(alarmId)
                if (alarm != null) {
                    // Update alarm state
                    val stoppedAlarm = alarm.withStateTransition(newState, reason)
                    val updateResult = alarmRepository.update(stoppedAlarm)
                    
                    if (updateResult.isFailure) {
                        AlarmLogger.logError("Stop Alarm State Update", alarmId, 
                            updateResult.exceptionOrNull() ?: Exception("Database update failed"))
                    }
                    
                    AlarmLogger.logStateTransition(alarmId, alarm.state, newState, reason)
                } else {
                    AlarmLogger.logWarning("Stop Alarm", "Alarm not found in database", alarmId)
                }
                
                // Stop alarm sound
                stopAlarmSound()
                
                // Stop foreground and remove notification
                stopForeground(true)
                
                // Clear current alarm
                currentAlarmId = -1
                isAlarmPlaying = false
                
                AlarmLogger.logSuccess("Stop Alarm", alarmId, "Alarm stopped: $reason")
                AlarmLogger.logSessionEnd("Stop Alarm $alarmId", true)
                
                // Stop the service
                stopSelf()
                
            } catch (e: Exception) {
                AlarmLogger.logError("Stop Alarm", alarmId, e)
                AlarmLogger.logSessionEnd("Stop Alarm $alarmId", false)
                stopSelf()
            }
        }
    }
    
    /**
     * Create foreground notification for alarm
     */
    private fun createAlarmNotification(alarm: com.alarmise.app.data.model.Alarm) {
        try {
            val notification = NotificationCompat.Builder(this, NotificationUtils.ALARM_CHANNEL_ID)
                .setContentTitle("${alarm.label} - Playing")
                .setContentText("Solve the math puzzle to stop the alarm")
                .setSmallIcon(R.drawable.ic_alarm_24)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setAutoCancel(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(
                    createAlarmTriggerPendingIntent(alarm),
                    true
                )
                .build()
                
            startForeground(NOTIFICATION_ID, notification)
            
            AlarmLogger.logDebug("Create Notification", mapOf(
                "alarmId" to alarm.id,
                "alarmLabel" to alarm.label,
                "notificationId" to NOTIFICATION_ID
            ))
            
        } catch (e: Exception) {
            AlarmLogger.logError("Create Notification", alarm.id, e)
        }
    }
    
    /**
     * Launch the alarm trigger activity (full screen)
     */
    private fun launchAlarmTriggerActivity(alarm: com.alarmise.app.data.model.Alarm) {
        try {
            val intent = Intent(this, AlarmTriggerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                       Intent.FLAG_ACTIVITY_CLEAR_TOP or
                       Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_ALARM_ID, alarm.id)
                putExtra("alarm", alarm)
            }
            
            startActivity(intent)
            
            AlarmLogger.logDebug("Launch Trigger Activity", mapOf(
                "alarmId" to alarm.id,
                "alarmLabel" to alarm.label
            ))
            
        } catch (e: Exception) {
            AlarmLogger.logError("Launch Trigger Activity", alarm.id, e)
        }
    }
    
    /**
     * Create pending intent for alarm trigger activity
     */
    private fun createAlarmTriggerPendingIntent(alarm: com.alarmise.app.data.model.Alarm): android.app.PendingIntent? {
        return try {
            val intent = Intent(this, AlarmTriggerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_ALARM_ID, alarm.id)
                putExtra("alarm", alarm)
            }
            
            android.app.PendingIntent.getActivity(
                this,
                alarm.id.toInt(),
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        } catch (e: Exception) {
            AlarmLogger.logError("Create Pending Intent", alarm.id, e)
            null
        }
    }
    
    
    /**
     * Start alarm sound with enhanced error handling and fallbacks
     */
    private fun startAlarmSound() {
        try {
            // Stop any existing media player
            stopAlarmSound()
            
            mediaPlayer = MediaPlayer().apply {
                // Set audio attributes for alarm stream
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
                
                // Set to use alarm stream for maximum volume
                setAudioStreamType(AudioManager.STREAM_ALARM)
                
                // Try to use system alarm sound
                try {
                    setDataSource(
                        this@AlarmService,
                        android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
                    )
                } catch (e: Exception) {
                    AlarmLogger.logWarning("Alarm Sound", "Failed to set system alarm sound, using fallback")
                    // Fallback to notification sound
                    setDataSource(
                        this@AlarmService,
                        android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
                    )
                }
                
                // Configure for continuous playback (critical requirement)
                isLooping = true
                setVolume(1.0f, 1.0f)
                
                // Set completion listener for error handling
                setOnCompletionListener { mp ->
                    AlarmLogger.logWarning("Alarm Sound", "Media player completed unexpectedly")
                    // Restart if it stops unexpectedly
                    if (isAlarmPlaying) {
                        startAlarmSound()
                    }
                }
                
                setOnErrorListener { mp, what, extra ->
                    AlarmLogger.logError("Alarm Sound", currentAlarmId, 
                        Exception("MediaPlayer error: what=$what, extra=$extra"))
                    
                    // Try to restart
                    if (isAlarmPlaying) {
                        startAlarmSound()
                    }
                    true // Return true to indicate we handled the error
                }
                
                prepare()
                start()
            }
            
            AlarmLogger.logDebug("Start Alarm Sound", mapOf(
                "alarmId" to currentAlarmId,
                "isLooping" to true,
                "volume" to 1.0f
            ))
            
        } catch (e: Exception) {
            AlarmLogger.logError("Start Alarm Sound", currentAlarmId, e)
            
            // Try fallback approach
            try {
                startFallbackAlarmSound()
            } catch (fallbackError: Exception) {
                AlarmLogger.logError("Fallback Alarm Sound", currentAlarmId, fallbackError)
            }
        }
    }
    
    /**
     * Fallback alarm sound using ToneGenerator
     */
    private fun startFallbackAlarmSound() {
        try {
            // Use ToneGenerator as last resort
            val toneGenerator = android.media.ToneGenerator(
                AudioManager.STREAM_ALARM,
                android.media.ToneGenerator.MAX_VOLUME
            )
            
            // Play a repeating tone
            serviceScope.launch {
                while (isAlarmPlaying) {
                    toneGenerator.startTone(android.media.ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1000)
                    kotlinx.coroutines.delay(1500)
                }
                toneGenerator.release()
            }
            
            AlarmLogger.logWarning("Fallback Alarm Sound", "Using ToneGenerator fallback", currentAlarmId)
            
        } catch (e: Exception) {
            AlarmLogger.logError("Fallback Alarm Sound", currentAlarmId, e)
        }
    }
    
    /**
     * Stop alarm sound with proper cleanup
     */
    private fun stopAlarmSound() {
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.release()
                
                AlarmLogger.logDebug("Stop Alarm Sound", mapOf(
                    "alarmId" to currentAlarmId,
                    "wasPlaying" to mp.isPlaying
                ))
            }
        } catch (e: Exception) {
            AlarmLogger.logError("Stop Alarm Sound", currentAlarmId, e)
        } finally {
            mediaPlayer = null
        }
    }
    
    /**
     * Acquire wake lock to prevent device sleep
     */
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "Alarmise::AlarmWakeLock"
            )
            
            // Acquire for maximum expected alarm duration (safety limit)
            wakeLock?.acquire(60 * 60 * 1000L) // 1 hour max
            
            AlarmLogger.logDebug("Acquire Wake Lock", mapOf(
                "timeout" to "1 hour",
                "flags" to "PARTIAL_WAKE_LOCK + ACQUIRE_CAUSES_WAKEUP"
            ))
            
        } catch (e: Exception) {
            AlarmLogger.logError("Acquire Wake Lock", null, e)
        }
    }
    
    /**
     * Release wake lock
     */
    private fun releaseWakeLock() {
        try {
            wakeLock?.let { wl ->
                if (wl.isHeld) {
                    wl.release()
                    AlarmLogger.logDebug("Release Wake Lock", mapOf("wasHeld" to true))
                }
            }
        } catch (e: Exception) {
            AlarmLogger.logError("Release Wake Lock", null, e)
        } finally {
            wakeLock = null
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        AlarmLogger.logSystemEvent("Alarm Service Destroyed", mapOf(
            "currentAlarmId" to currentAlarmId,
            "isAlarmPlaying" to isAlarmPlaying
        ))
        
        // Clean up all resources
        stopAlarmSound()
        releaseWakeLock()
        serviceScope.cancel()
        
        AlarmLogger.logSessionEnd("Alarm Service", true)
    }
    
    /**
     * Handle service restart after being killed
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        
        AlarmLogger.logSystemEvent("Task Removed", mapOf(
            "currentAlarmId" to currentAlarmId,
            "isAlarmPlaying" to isAlarmPlaying,
            "rootIntent" to (rootIntent?.toString() ?: "null")
        ))
        
        // If we have an active alarm, restart the service
        if (isAlarmPlaying && currentAlarmId != -1L) {
            AlarmLogger.logWarning("Task Removed", "Restarting alarm service due to task removal", currentAlarmId)
            
            val restartIntent = Intent(this, AlarmService::class.java).apply {
                action = ACTION_START_ALARM
                putExtra(EXTRA_ALARM_ID, currentAlarmId)
            }
            startForegroundService(restartIntent)
        }
    }
    
    /**
     * Get current service state for debugging
     */
    fun getServiceState(): Map<String, Any> {
        return mapOf(
            "currentAlarmId" to currentAlarmId,
            "isAlarmPlaying" to isAlarmPlaying,
            "hasMediaPlayer" to (mediaPlayer != null),
            "mediaPlayerPlaying" to (mediaPlayer?.isPlaying ?: false),
            "hasWakeLock" to (wakeLock != null),
            "wakeLockHeld" to (wakeLock?.isHeld ?: false),
            "serviceStarted" to true
        )
    }
}
