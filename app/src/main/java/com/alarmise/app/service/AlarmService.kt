package com.alarmise.app.service

import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.alarmise.app.R
import com.alarmise.app.audio.AudioHardwareManager
import com.alarmise.app.audio.EnhancedAudioManager
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
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * Enhanced AlarmService - Persistent Foreground Service for Critical Alarm Playback
 * 
 * CRITICAL REQUIREMENTS IMPLEMENTATION:
 * - Persistent playback across all app states (foreground/background/closed)
 * - Foreground service for Android 8.0+ compatibility  
 * - Auto-restart mechanisms for system kills
 * - Service binding and communication via LocalBroadcastManager
 * - Battery optimization handling
 * - Proper service lifecycle management
 */
@AndroidEntryPoint
class AlarmService : Service() {
    
    @Inject
    lateinit var alarmRepository: AlarmRepository
    
    @Inject
    lateinit var notificationUtils: NotificationUtils
    
    @Inject
    lateinit var enhancedAudioManager: EnhancedAudioManager
    
    @Inject
    lateinit var audioHardwareManager: AudioHardwareManager
    
    // Service binding for UI communication
    private val binder = AlarmServiceBinder()
    private var isServiceBound = false
    
    // Coroutine scope for service operations
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Service state (audio now handled by EnhancedAudioManager)
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentAlarmId: Long = -1
    private var isAlarmPlaying = false
    private var isServiceStarted = false
    
    // Service state persistence
    private var alarmStartTime: ZonedDateTime? = null
    private var alarmEndTime: ZonedDateTime? = null
    private var alarmLabel: String = ""
    
    // Audio hardware event handling
    private val audioHardwareCallback = object : AudioHardwareManager.AudioHardwareCallback {
        override fun onHeadphonesDisconnected() {
            AlarmLogger.logWarning("Audio Hardware", "Headphones disconnected - ensuring alarm continues on speaker", currentAlarmId)
            // CRITICAL: Alarm must continue playing on speaker per requirements
            if (isAlarmPlaying) {
                audioHardwareManager.forceAudioToSpeaker()
                // Audio system will handle the transition automatically
                AlarmLogger.logSystemEvent("Alarm Continuity", mapOf(
                    "event" to "headphones_disconnect",
                    "action" to "force_speaker",
                    "alarmId" to currentAlarmId
                ))
            }
        }
        
        override fun onHeadphonesConnected() {
            AlarmLogger.logSystemEvent("Audio Hardware", mapOf(
                "event" to "headphones_connected",
                "alarmId" to currentAlarmId
            ))
            // Restore normal routing when headphones connect
            if (isAlarmPlaying) {
                audioHardwareManager.restoreNormalAudioRouting()
            }
        }
        
        override fun onBluetoothDisconnected() {
            AlarmLogger.logWarning("Audio Hardware", "Bluetooth audio disconnected - ensuring alarm continues", currentAlarmId)
            // CRITICAL: Alarm must continue playing 
            if (isAlarmPlaying) {
                audioHardwareManager.forceAudioToSpeaker()
                AlarmLogger.logSystemEvent("Alarm Continuity", mapOf(
                    "event" to "bluetooth_disconnect",
                    "action" to "force_speaker",
                    "alarmId" to currentAlarmId
                ))
            }
        }
        
        override fun onBluetoothConnected() {
            AlarmLogger.logSystemEvent("Audio Hardware", mapOf(
                "event" to "bluetooth_connected",
                "alarmId" to currentAlarmId
            ))
            if (isAlarmPlaying) {
                audioHardwareManager.restoreNormalAudioRouting()
            }
        }
        
        override fun onAudioRoutingChanged(newRoute: String) {
            AlarmLogger.logSystemEvent("Audio Hardware", mapOf(
                "event" to "routing_changed",
                "newRoute" to newRoute,
                "alarmId" to currentAlarmId
            ))
        }
        
        override fun onHardwareAudioFailure(reason: String) {
            AlarmLogger.logError("Audio Hardware", currentAlarmId, Exception("Hardware audio failure: $reason"))
            // Critical: Attempt to recover audio playback
            if (isAlarmPlaying) {
                serviceScope.launch {
                    delay(1000) // Brief delay for hardware to stabilize
                    try {
                        enhancedAudioManager.startAlarm(currentAlarmId, enableFadeIn = false)
                    } catch (e: Exception) {
                        AlarmLogger.logError("Audio Recovery", currentAlarmId, e)
                    }
                }
            }
        }
    }
    
    companion object {
        // Service actions
        const val ACTION_START_ALARM = "com.alarmise.app.START_ALARM"
        const val ACTION_STOP_ALARM = "com.alarmise.app.STOP_ALARM"
        const val ACTION_DISMISS_ALARM = "com.alarmise.app.DISMISS_ALARM"
        const val ACTION_FOREGROUND_START = "com.alarmise.app.FOREGROUND_START"
        
        // Broadcast actions for UI communication
        const val BROADCAST_ALARM_STARTED = "com.alarmise.app.ALARM_STARTED"
        const val BROADCAST_ALARM_STOPPED = "com.alarmise.app.ALARM_STOPPED"
        const val BROADCAST_SERVICE_STATE = "com.alarmise.app.SERVICE_STATE"
        
        // Intent extras
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_ALARM_LABEL = "extra_alarm_label"
        const val EXTRA_START_TIME = "extra_start_time"
        const val EXTRA_END_TIME = "extra_end_time"
        const val EXTRA_SERVICE_RUNNING = "extra_service_running"
        const val EXTRA_ALARM_PLAYING = "extra_alarm_playing"
        
        // Notification ID for foreground service
        const val FOREGROUND_NOTIFICATION_ID = 1001
        
        // Restart delay for auto-restart mechanism
        private const val RESTART_DELAY_MS = 3000L
    }
    
    /**
     * Service Binder for UI binding
     */
    inner class AlarmServiceBinder : Binder() {
        fun getService(): AlarmService = this@AlarmService
    }
    
    override fun onBind(intent: Intent?): IBinder {
        AlarmLogger.logSystemEvent("Service Bind", mapOf("intent" to (intent?.action ?: "null")))
        isServiceBound = true
        // TODO: Re-enable broadcastServiceState when communication components are restored
        // broadcastServiceState()
        return binder
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        AlarmLogger.logSystemEvent("Service Unbind", mapOf("intent" to (intent?.action ?: "null")))
        isServiceBound = false
        // TODO: Re-enable broadcastServiceState when communication components are restored
        // broadcastServiceState()
        return super.onUnbind(intent)
    }
    
    override fun onCreate() {
        super.onCreate()
        AlarmLogger.logSessionStart("Alarm Service Created")
        
        // Initialize foreground service immediately to prevent ANR
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService()
        }
        
        // Register audio hardware event monitoring
        audioHardwareManager.registerHardwareEvents(audioHardwareCallback)
        
        acquireWakeLock()
        // TODO: Re-enable broadcastServiceState when communication components are restored
        // broadcastServiceState()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val alarmId = intent?.getLongExtra(EXTRA_ALARM_ID, -1) ?: -1
        
        // Add extensive debug logging
        android.util.Log.d("AlarmService", "🎵 AlarmService.onStartCommand() called!")
        android.util.Log.d("AlarmService", "🎵 Intent action: $action")
        android.util.Log.d("AlarmService", "🎵 Alarm ID: $alarmId")
        android.util.Log.d("AlarmService", "🎵 Start ID: $startId")
        android.util.Log.d("AlarmService", "🎵 Intent extras: ${intent?.extras}")
        android.util.Log.d("AlarmService", "🎵 Currently playing: $isAlarmPlaying")
        android.util.Log.d("AlarmService", "🎵 Service started: $isServiceStarted")
        
        AlarmLogger.logSystemEvent("Alarm Service Command", mapOf(
            "action" to (action ?: "null"),
            "alarmId" to alarmId,
            "startId" to startId,
            "currentlyPlaying" to isAlarmPlaying,
            "isServiceStarted" to isServiceStarted
        ))
        
        // Ensure foreground service is running
        if (!isServiceStarted) {
            android.util.Log.d("AlarmService", "🎵 Starting foreground service...")
            startForegroundService()
        }
        
        when (action) {
            ACTION_START_ALARM -> {
                android.util.Log.d("AlarmService", "🎵 Processing ACTION_START_ALARM")
                if (alarmId != -1L) {
                    android.util.Log.d("AlarmService", "🎵 Valid alarm ID received, starting alarm...")
                    startAlarm(alarmId, intent)
                } else {
                    android.util.Log.e("AlarmService", "🎵 Invalid alarm ID received: $alarmId")
                    AlarmLogger.logWarning("Alarm Service", "Start alarm requested with invalid ID", alarmId)
                }
            }
            ACTION_STOP_ALARM -> {
                android.util.Log.d("AlarmService", "🎵 Processing ACTION_STOP_ALARM")
                stopAlarm(alarmId, AlarmState.EXPIRED, "Auto-stopped by system")
            }
            ACTION_DISMISS_ALARM -> {
                android.util.Log.d("AlarmService", "🎵 Processing ACTION_DISMISS_ALARM")
                stopAlarm(alarmId, AlarmState.DISMISSED, "Dismissed by user solving puzzle")
            }
            ACTION_FOREGROUND_START -> {
                android.util.Log.d("AlarmService", "🎵 Processing ACTION_FOREGROUND_START")
                // Just maintain foreground state - no alarm action needed
                AlarmLogger.logSystemEvent("Foreground Service", mapOf("reason" to "explicit_start"))
            }
            else -> {
                android.util.Log.e("AlarmService", "🎵 Unknown action or null action: $action")
                // Handle service restart scenarios
                // TODO: Re-enable handleServiceRestart when enhanced service components are restored
                AlarmLogger.logSystemEvent("Service Restart", mapOf("alarmId" to alarmId, "startId" to startId))
            }
        }
        
        // TODO: Re-enable broadcastServiceState when communication components are restored
        // broadcastServiceState()
        
        android.util.Log.d("AlarmService", "🎵 Returning START_STICKY")
        // Return START_STICKY for automatic restart on system kill
        // This is CRITICAL for persistent alarm playback
        return START_STICKY
    }
    
    /**
     * Start foreground service with persistent notification
     * Required for Android 8.0+ background service limitations
     */
    private fun startForegroundService() {
        try {
            val notification = if (isAlarmPlaying && alarmLabel.isNotEmpty()) {
                // Show alarm notification if alarm is active
                notificationUtils.createAlarmNotification(
                    alarmLabel = alarmLabel,
                    startTime = alarmStartTime?.toString() ?: "",
                    endTime = alarmEndTime?.toString() ?: ""
                )
            } else {
                // Show service notification for background operation
                notificationUtils.createServiceNotification(
                    title = "Alarm Service Active",
                    content = "Ready to play alarms"
                )
            }
            
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
            isServiceStarted = true
            
            AlarmLogger.logSystemEvent("Foreground Service Started", mapOf(
                "notificationType" to if (isAlarmPlaying) "alarm" else "service"
            ))
            
        } catch (e: Exception) {
            AlarmLogger.logError("Start Foreground Service", currentAlarmId, e)
        }
    }
    
    /**
     * Start alarm playback with comprehensive state management and persistence
     */
    private fun startAlarm(alarmId: Long, intent: Intent? = null) {
        android.util.Log.d("AlarmService", "🎵 startAlarm() called with ID: $alarmId")
        android.util.Log.d("AlarmService", "🎵 Intent extras in startAlarm: ${intent?.extras}")
        
        serviceScope.launch {
            try {
                android.util.Log.d("AlarmService", "🎵 Inside startAlarm coroutine")
                AlarmLogger.logSessionStart("Start Alarm $alarmId")
                
                // Extract alarm details from intent if available (for restart scenarios)
                alarmLabel = intent?.getStringExtra(EXTRA_ALARM_LABEL) ?: ""
                val startTimeStr = intent?.getStringExtra(EXTRA_START_TIME)
                val endTimeStr = intent?.getStringExtra(EXTRA_END_TIME)
                
                android.util.Log.d("AlarmService", "🎵 Extracted label: '$alarmLabel'")
                
                // Get alarm from database
                android.util.Log.d("AlarmService", "🎵 Loading alarm from database...")
                val alarm = alarmRepository.getById(alarmId)
                android.util.Log.d("AlarmService", "🎵 Alarm from database: ${alarm?.let { "${it.label} at ${it.startTime}" } ?: "null"}")
                
                if (alarm == null && alarmLabel.isEmpty()) {
                    android.util.Log.e("AlarmService", "🎵 ERROR: Alarm not found and no cached data!")
                    AlarmLogger.logError("Start Alarm", alarmId, 
                        IllegalStateException("Alarm not found in database and no cached data"))
                    stopSelf()
                    return@launch
                }
                
                // Use cached data if available, otherwise database data
                if (alarm != null) {
                    android.util.Log.d("AlarmService", "🎵 Using alarm from database")
                    alarmLabel = alarm.label
                    // Convert LocalTime to ZonedDateTime for today
                    val today = ZonedDateTime.now().toLocalDate()
                    alarmStartTime = ZonedDateTime.of(today, alarm.startTime, ZonedDateTime.now().zone)
                    alarmEndTime = ZonedDateTime.of(today, alarm.endTime, ZonedDateTime.now().zone)
                } else if (startTimeStr != null && endTimeStr != null) {
                    android.util.Log.d("AlarmService", "🎵 Using cached data from intent")
                    // Use cached data from intent (restart scenario)
                    try {
                        alarmStartTime = ZonedDateTime.parse(startTimeStr)
                        alarmEndTime = ZonedDateTime.parse(endTimeStr)
                    } catch (e: Exception) {
                        android.util.Log.e("AlarmService", "🎵 Error parsing cached times", e)
                        AlarmLogger.logError("Parse Cached Times", alarmId, e)
                    }
                }
                
                // Validate alarm state (if available)
                if (alarm != null && alarm.state != AlarmState.SCHEDULED && alarm.state != AlarmState.ACTIVE) {
                    android.util.Log.w("AlarmService", "🎵 Warning: Alarm state is ${alarm.state}, not SCHEDULED/ACTIVE")
                    AlarmLogger.logWarning("Start Alarm", 
                        "Alarm is not in expected state: ${alarm.state}", alarmId)
                }
                
                // Check if alarm should actually be playing now
                if (alarm?.shouldTriggerNow() == false && alarm.isCurrentlyActive() == false) {
                    android.util.Log.w("AlarmService", "🎵 Warning: Alarm triggered outside expected time window")
                    AlarmLogger.logWarning("Start Alarm", 
                        "Alarm triggered outside expected time window", alarmId)
                    // Continue anyway - might be legitimate restart
                }
                
                // Update alarm state to ACTIVE (if database available)
                if (alarm != null) {
                    android.util.Log.d("AlarmService", "🎵 Updating alarm state to ACTIVE...")
                    val activeAlarm = alarm.withStateTransition(
                        AlarmState.ACTIVE, 
                        "Alarm triggered at ${ZonedDateTime.now()}"
                    )
                    
                    val updateResult = alarmRepository.update(activeAlarm)
                    if (updateResult.isFailure) {
                        android.util.Log.e("AlarmService", "🎵 Error updating alarm state", updateResult.exceptionOrNull())
                        AlarmLogger.logError("Start Alarm State Update", alarmId, 
                            updateResult.exceptionOrNull() ?: Exception("Database update failed"))
                    } else {
                        android.util.Log.d("AlarmService", "🎵 Successfully updated alarm state to ACTIVE")
                    }
                }
                
                // Set current alarm
                currentAlarmId = alarmId
                isAlarmPlaying = true
                
                android.util.Log.d("AlarmService", "🎵 Set currentAlarmId=$currentAlarmId, isAlarmPlaying=$isAlarmPlaying")
                
                // Update foreground notification with alarm details
                android.util.Log.d("AlarmService", "🎵 Updating foreground service notification...")
                startForegroundService()
                
                // Start alarm sound with enhanced audio system
                android.util.Log.d("AlarmService", "🎵 Starting enhanced audio manager...")
                enhancedAudioManager.startAlarm(currentAlarmId, enableFadeIn = true)
                android.util.Log.d("AlarmService", "🎵 Enhanced audio manager started!")
                
                // Launch alarm trigger activity
                if (alarm != null) {
                    android.util.Log.d("AlarmService", "🎵 Launching alarm trigger activity...")
                    launchAlarmTriggerActivity(alarm)
                } else {
                    android.util.Log.w("AlarmService", "🎵 Skipping alarm trigger activity - no alarm object")
                }
                
                // Broadcast alarm started
                // TODO: Re-enable broadcastAlarmStarted when communication components are restored
                // broadcastAlarmStarted(alarmId)
                
                AlarmLogger.logSuccess("Start Alarm", alarmId, 
                    "Alarm successfully started and playing")
                
            } catch (e: Exception) {
                AlarmLogger.logError("Start Alarm", alarmId, e)
                stopAlarm(alarmId, AlarmState.ERROR, "Error starting alarm: ${e.message}")
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
                
                // Stop alarm sound with enhanced audio system
                enhancedAudioManager.stopAlarm()
                
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
                
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
            
            AlarmLogger.logDebug("Create Notification", mapOf(
                "alarmId" to alarm.id,
                "alarmLabel" to alarm.label,
                "notificationId" to FOREGROUND_NOTIFICATION_ID
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
        AlarmLogger.logSystemEvent("Service Destroy", mapOf(
            "currentAlarmId" to currentAlarmId,
            "isPlaying" to isAlarmPlaying
        ))
        
        // If alarm is playing, schedule restart using enhanced mechanisms
        if (isAlarmPlaying && currentAlarmId != -1L) {
            // TODO: Re-enable ServiceRestartManager when components are restored
            // ServiceRestartManager.handleServiceDestroyed(this, currentAlarmId, isAlarmPlaying)
        }
        
        // Broadcast service stopping
        // TODO: Re-enable broadcastServiceState when communication components are restored
        // broadcastServiceState()
        
        performCleanup()
        super.onDestroy()
    }
    
    /**
     * Handle task removal (user swipes app away)
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        
        AlarmLogger.logSystemEvent("Task Removed", mapOf(
            "currentAlarmId" to currentAlarmId,
            "isAlarmPlaying" to isAlarmPlaying,
            "rootIntent" to (rootIntent?.toString() ?: "null")
        ))
        
        // If we have an active alarm, ensure service continues
        if (isAlarmPlaying && currentAlarmId != -1L) {
            AlarmLogger.logWarning("Task Removed", "Ensuring alarm service continues", currentAlarmId)
            
            // Schedule restart as fallback
            // TODO: Re-enable ServiceRestartManager when components are restored
            // ServiceRestartManager.handleServiceDestroyed(this, currentAlarmId, isAlarmPlaying)
        }
    }
    
    /**
     * Get current service state for debugging
     */
    fun getServiceState(): Map<String, Any> {
        val audioState = enhancedAudioManager.getAudioState()
        return mapOf(
            "currentAlarmId" to currentAlarmId,
            "isAlarmPlaying" to isAlarmPlaying,
            "audioManagerState" to audioState,
            "hasWakeLock" to (wakeLock != null),
            "wakeLockHeld" to (wakeLock?.isHeld ?: false),
            "serviceStarted" to true
        )
    }
    
    /**
     * Perform cleanup when service is destroyed
     */
    private fun performCleanup() {
        try {
            // Stop alarm sound with enhanced audio system
            enhancedAudioManager.stopAlarm()
            
            // Unregister audio hardware events
            audioHardwareManager.unregisterHardwareEvents()
            
            // Cleanup enhanced audio manager
            enhancedAudioManager.cleanup()
            
            // Release wake lock
            releaseWakeLock()
            
            // Cancel service scope
            serviceScope.cancel()
            
            // Reset state
            isAlarmPlaying = false
            currentAlarmId = -1L
            alarmLabel = ""
            
        } catch (e: Exception) {
            AlarmLogger.logError("Service Cleanup", null, e)
        }
    }
}
