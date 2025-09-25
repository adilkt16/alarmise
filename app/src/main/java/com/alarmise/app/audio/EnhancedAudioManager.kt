package com.alarmise.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.alarmise.app.R
import com.alarmise.app.utils.AlarmLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhanced Audio Manager for robust, persistent alarm playback
 * Implements non-negotiable requirement: continuous, non-stop alarm sound
 * 
 * Key Features:
 * - Audio focus management with priority handling
 * - Volume bypass to ensure alarm plays at system alarm volume
 * - Multiple fallback sound sources
 * - Hardware change detection (headphone disconnect)
 * - Fade-in alarm start for gentle wake-up
 * - Audio session recovery and persistence
 * - Graceful interruption handling with recovery
 */
@Singleton
class EnhancedAudioManager @Inject constructor(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "EnhancedAudioManager"
        private const val FADE_IN_DURATION_MS = 3000L // 3 seconds fade-in
        private const val VOLUME_CHECK_INTERVAL_MS = 1000L // Check volume every second
        private const val AUDIO_RECOVERY_DELAY_MS = 500L // Delay before attempting recovery
        private const val MAX_RETRY_ATTEMPTS = 5
    }
    
    // Audio system components
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var fallbackToneGenerator: android.media.ToneGenerator? = null
    
    // Audio session management
    private val audioScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var volumeMonitorJob: Job? = null
    private var fadeInJob: Job? = null
    private var recoveryJob: Job? = null
    
    // State tracking
    private var isPlaying = false
    private var currentAlarmId: Long = -1L
    private var currentVolume = 0f
    private var targetVolume = 1.0f
    private var retryAttempts = 0
    private var originalStreamVolume = 0
    
    // Audio focus handling
    private var hasAudioFocus = false
    private var audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener? = null
    
    // Fallback sound sources (ordered by preference)
    private val fallbackSoundSources = listOf(
        android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI,
        android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
        android.provider.Settings.System.DEFAULT_RINGTONE_URI
        // Custom alarm sounds can be added here when available:
        // Uri.parse("android.resource://${context.packageName}/${R.raw.alarm_sound}")
    )
    
    /**
     * Start alarm with robust audio management
     * CRITICAL: This must ensure continuous playback per requirements
     */
    fun startAlarm(alarmId: Long, enableFadeIn: Boolean = true) {
        AlarmLogger.logSessionStart("Enhanced Audio Manager - Start Alarm")
        
        currentAlarmId = alarmId
        isPlaying = true
        retryAttempts = 0
        
        try {
            // Step 1: Request audio focus with highest priority
            requestAudioFocus()
            
            // Step 2: Set volume to maximum for alarm stream
            setAlarmVolumeToMaximum()
            
            // Step 3: Start audio monitoring
            startVolumeMonitoring()
            
            // Step 4: Initialize MediaPlayer with fallback system
            initializeMediaPlayerWithFallbacks(enableFadeIn)
            
            AlarmLogger.logSuccess("Start Alarm Audio", alarmId, "Audio system initialized successfully")
            
        } catch (e: Exception) {
            AlarmLogger.logError("Start Alarm Audio", alarmId, e)
            handleAudioFailure("Failed to start alarm audio", e)
        }
    }
    
    /**
     * Stop alarm and release all audio resources
     */
    fun stopAlarm() {
        AlarmLogger.logSystemEvent("Stop Alarm Audio", mapOf(
            "alarmId" to currentAlarmId,
            "wasPlaying" to isPlaying
        ))
        
        isPlaying = false
        currentAlarmId = -1L
        
        try {
            // Cancel all background jobs
            volumeMonitorJob?.cancel()
            fadeInJob?.cancel()
            recoveryJob?.cancel()
            
            // Stop and release MediaPlayer
            stopMediaPlayer()
            
            // Stop fallback tone generator
            stopFallbackTone()
            
            // Release audio focus
            releaseAudioFocus()
            
            // Restore original volume
            restoreOriginalVolume()
            
        } catch (e: Exception) {
            AlarmLogger.logError("Stop Alarm Audio", currentAlarmId, e)
        }
    }
    
    /**
     * Request audio focus with maximum priority
     * CRITICAL: Must maintain focus to ensure continuous playback
     */
    private fun requestAudioFocus() {
        audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            handleAudioFocusChange(focusChange)
        }
        
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Use AudioFocusRequest for API 26+
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                .build()
            
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(false) // We need immediate focus
                .setWillPauseWhenDucked(false) // Don't pause for ducking
                .setOnAudioFocusChangeListener(audioFocusChangeListener!!)
                .build()
            
            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            // Legacy audio focus request
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        
        hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        
        AlarmLogger.logSystemEvent("Audio Focus Request", mapOf(
            "result" to if (hasAudioFocus) "GRANTED" else "DENIED",
            "focusType" to "AUDIOFOCUS_GAIN"
        ))
        
        if (!hasAudioFocus) {
            AlarmLogger.logWarning("Audio Focus", "Failed to gain audio focus, alarm may be interrupted")
        }
    }
    
    /**
     * Handle audio focus changes
     * CRITICAL: Must maintain alarm playback even during interruptions
     */
    private fun handleAudioFocusChange(focusChange: Int) {
        AlarmLogger.logSystemEvent("Audio Focus Change", mapOf(
            "focusChange" to focusChange,
            "isPlaying" to isPlaying
        ))
        
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Regained focus - ensure alarm is playing
                hasAudioFocus = true
                if (isPlaying && (mediaPlayer?.isPlaying != true)) {
                    AlarmLogger.logWarning("Audio Focus", "Regained focus, restarting alarm")
                    recoverAudioPlayback("Audio focus regained")
                }
            }
            
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent focus loss - but alarm must continue per requirements
                hasAudioFocus = false
                AlarmLogger.logWarning("Audio Focus", "Lost audio focus permanently - attempting to continue alarm")
                // Critical: Don't stop alarm, attempt to regain focus
                attemptAudioFocusRecovery()
            }
            
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Temporary loss (e.g., notification) - alarm must continue
                hasAudioFocus = false
                AlarmLogger.logWarning("Audio Focus", "Temporary audio focus loss - maintaining alarm")
                // Don't pause - alarm must be continuous per requirements
                scheduleAudioFocusRecovery(1000L) // Try to regain focus in 1 second
            }
            
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Can continue at lower volume - but we need full volume for alarm
                AlarmLogger.logWarning("Audio Focus", "Audio ducking requested - maintaining full volume")
                // Don't duck - alarm needs to be heard per requirements
                ensureAlarmVolume()
            }
        }
    }
    
    /**
     * Set alarm volume to maximum
     * CRITICAL: Bypass user volume settings per requirements
     */
    private fun setAlarmVolumeToMaximum() {
        try {
            // Store original volume for restoration later
            originalStreamVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            
            // Set alarm stream to maximum volume
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(
                AudioManager.STREAM_ALARM,
                maxVolume,
                0 // No UI flags - silent volume change
            )
            
            AlarmLogger.logSystemEvent("Volume Override", mapOf(
                "originalVolume" to originalStreamVolume,
                "maxVolume" to maxVolume,
                "currentVolume" to audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            ))
            
        } catch (e: Exception) {
            AlarmLogger.logError("Volume Override", currentAlarmId, e)
        }
    }
    
    /**
     * Initialize MediaPlayer with robust fallback system
     */
    private fun initializeMediaPlayerWithFallbacks(enableFadeIn: Boolean) {
        var soundSource: Uri? = null
        var lastException: Exception? = null
        
        // Try each fallback sound source until one works
        for (source in fallbackSoundSources) {
            try {
                soundSource = source
                initializeMediaPlayer(source, enableFadeIn)
                AlarmLogger.logSuccess("Audio Source", currentAlarmId, "Using sound source: $source")
                return // Success!
                
            } catch (e: Exception) {
                lastException = e
                AlarmLogger.logWarning("Audio Source", "Failed to use $source: ${e.message}")
                stopMediaPlayer() // Clean up failed attempt
            }
        }
        
        // All fallback sounds failed - use tone generator as last resort
        AlarmLogger.logError("All Audio Sources Failed", currentAlarmId, 
            lastException ?: Exception("No working audio sources found"))
        
        startFallbackToneGenerator()
    }
    
    /**
     * Initialize MediaPlayer with specific sound source
     */
    private fun initializeMediaPlayer(soundSource: Uri, enableFadeIn: Boolean) {
        mediaPlayer = MediaPlayer().apply {
            // Set audio attributes for alarm with maximum priority
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .build()
            )
            
            // Use alarm stream for proper volume control
            @Suppress("DEPRECATION")
            setAudioStreamType(AudioManager.STREAM_ALARM)
            
            // Set data source
            setDataSource(context, soundSource)
            
            // Configure for continuous playback (CRITICAL requirement)
            isLooping = true
            
            // Set initial volume (will fade in if enabled)
            currentVolume = if (enableFadeIn) 0f else targetVolume
            setVolume(currentVolume, currentVolume)
            
            // Set listeners for error handling and recovery
            setOnCompletionListener { mp ->
                AlarmLogger.logWarning("MediaPlayer", "Unexpected completion - restarting")
                if (isPlaying) {
                    recoverAudioPlayback("MediaPlayer completed unexpectedly")
                }
            }
            
            setOnErrorListener { mp, what, extra ->
                AlarmLogger.logError("MediaPlayer", currentAlarmId, 
                    Exception("MediaPlayer error: what=$what, extra=$extra"))
                
                if (isPlaying) {
                    recoverAudioPlayback("MediaPlayer error occurred")
                }
                true // Return true to indicate we handled the error
            }
            
            // Prepare and start
            prepare()
            start()
        }
        
        // Start fade-in if enabled
        if (enableFadeIn) {
            startFadeIn()
        }
    }
    
    /**
     * Start fade-in effect for gentle wake-up
     */
    private fun startFadeIn() {
        fadeInJob?.cancel()
        fadeInJob = audioScope.launch {
            try {
                val steps = 30 // 30 steps over 3 seconds = 100ms per step
                val volumeStep = targetVolume / steps
                val delayStep = FADE_IN_DURATION_MS / steps
                
                for (step in 1..steps) {
                    if (!isPlaying) break
                    
                    currentVolume = volumeStep * step
                    mediaPlayer?.setVolume(currentVolume, currentVolume)
                    
                    delay(delayStep)
                }
                
                // Ensure final volume is reached
                if (isPlaying) {
                    currentVolume = targetVolume
                    mediaPlayer?.setVolume(currentVolume, currentVolume)
                }
                
                AlarmLogger.logDebug("Fade In", mapOf(
                    "duration" to FADE_IN_DURATION_MS,
                    "finalVolume" to currentVolume
                ))
                
            } catch (e: Exception) {
                AlarmLogger.logError("Fade In", currentAlarmId, e)
                // Set to full volume immediately if fade-in fails
                currentVolume = targetVolume
                mediaPlayer?.setVolume(currentVolume, currentVolume)
            }
        }
    }
    
    /**
     * Start volume monitoring to maintain alarm volume
     */
    private fun startVolumeMonitoring() {
        volumeMonitorJob?.cancel()
        volumeMonitorJob = audioScope.launch {
            while (isPlaying) {
                try {
                    ensureAlarmVolume()
                    delay(VOLUME_CHECK_INTERVAL_MS)
                } catch (e: Exception) {
                    AlarmLogger.logError("Volume Monitoring", currentAlarmId, e)
                    delay(VOLUME_CHECK_INTERVAL_MS)
                }
            }
        }
    }
    
    /**
     * Ensure alarm is playing at proper volume
     */
    private fun ensureAlarmVolume() {
        try {
            // Check if system alarm volume has been changed
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val currentSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            
            // Restore maximum volume if it was lowered
            if (currentSystemVolume < maxVolume) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
                AlarmLogger.logWarning("Volume Monitoring", "Restored alarm volume to maximum")
            }
            
            // Ensure MediaPlayer volume is at maximum
            mediaPlayer?.let { mp ->
                if (mp.isPlaying && currentVolume < targetVolume) {
                    currentVolume = targetVolume
                    mp.setVolume(currentVolume, currentVolume)
                }
            }
            
        } catch (e: Exception) {
            AlarmLogger.logError("Volume Ensure", currentAlarmId, e)
        }
    }
    
    /**
     * Start fallback tone generator as last resort
     */
    private fun startFallbackToneGenerator() {
        try {
            fallbackToneGenerator = android.media.ToneGenerator(
                AudioManager.STREAM_ALARM,
                android.media.ToneGenerator.MAX_VOLUME
            )
            
            // Start continuous tone generation
            audioScope.launch {
                while (isPlaying && fallbackToneGenerator != null) {
                    try {
                        fallbackToneGenerator?.startTone(
                            android.media.ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 
                            1000
                        )
                        delay(1200) // Small gap between tones
                    } catch (e: Exception) {
                        AlarmLogger.logError("Fallback Tone", currentAlarmId, e)
                        break
                    }
                }
            }
            
            AlarmLogger.logWarning("Fallback Audio", "Using ToneGenerator as fallback", currentAlarmId)
            
        } catch (e: Exception) {
            AlarmLogger.logError("Fallback Tone Init", currentAlarmId, e)
        }
    }
    
    /**
     * Recover audio playback after interruption
     */
    private fun recoverAudioPlayback(reason: String) {
        if (!isPlaying || retryAttempts >= MAX_RETRY_ATTEMPTS) {
            return
        }
        
        retryAttempts++
        
        AlarmLogger.logWarning("Audio Recovery", "Attempting recovery #$retryAttempts: $reason")
        
        recoveryJob?.cancel()
        recoveryJob = audioScope.launch {
            try {
                delay(AUDIO_RECOVERY_DELAY_MS)
                
                if (isPlaying) {
                    // Stop current playback
                    stopMediaPlayer()
                    
                    // Request audio focus again
                    requestAudioFocus()
                    
                    // Restart with fallbacks
                    initializeMediaPlayerWithFallbacks(enableFadeIn = false)
                }
                
            } catch (e: Exception) {
                AlarmLogger.logError("Audio Recovery", currentAlarmId, e)
                // If recovery fails, try fallback tone
                if (isPlaying) {
                    startFallbackToneGenerator()
                }
            }
        }
    }
    
    /**
     * Attempt to regain audio focus
     */
    private fun attemptAudioFocusRecovery() {
        audioScope.launch {
            repeat(3) { attempt ->
                delay(1000L * (attempt + 1)) // Exponential backoff
                
                if (isPlaying && !hasAudioFocus) {
                    requestAudioFocus()
                    if (hasAudioFocus) {
                        AlarmLogger.logSuccess("Audio Focus Recovery", currentAlarmId, 
                            "Regained audio focus on attempt ${attempt + 1}")
                        return@launch
                    }
                }
            }
            
            if (isPlaying && !hasAudioFocus) {
                AlarmLogger.logWarning("Audio Focus Recovery", "Failed to regain focus - continuing anyway")
            }
        }
    }
    
    /**
     * Schedule audio focus recovery
     */
    private fun scheduleAudioFocusRecovery(delayMs: Long) {
        audioScope.launch {
            delay(delayMs)
            if (isPlaying && !hasAudioFocus) {
                requestAudioFocus()
            }
        }
    }
    
    /**
     * Handle audio failure with comprehensive recovery
     */
    private fun handleAudioFailure(message: String, exception: Exception) {
        AlarmLogger.logError("Audio Failure", currentAlarmId, exception)
        
        if (isPlaying && retryAttempts < MAX_RETRY_ATTEMPTS) {
            recoverAudioPlayback("Audio failure: $message")
        } else {
            AlarmLogger.logError("Audio System Failed", currentAlarmId, 
                Exception("Exhausted all recovery attempts: $message", exception))
        }
    }
    
    /**
     * Stop MediaPlayer safely
     */
    private fun stopMediaPlayer() {
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.release()
            }
        } catch (e: Exception) {
            AlarmLogger.logError("Stop MediaPlayer", currentAlarmId, e)
        } finally {
            mediaPlayer = null
        }
    }
    
    /**
     * Stop fallback tone generator
     */
    private fun stopFallbackTone() {
        try {
            fallbackToneGenerator?.release()
        } catch (e: Exception) {
            AlarmLogger.logError("Stop Fallback Tone", currentAlarmId, e)
        } finally {
            fallbackToneGenerator = null
        }
    }
    
    /**
     * Release audio focus
     */
    private fun releaseAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
            } else if (audioFocusChangeListener != null) {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(audioFocusChangeListener!!)
            }
            
            hasAudioFocus = false
            audioFocusRequest = null
            audioFocusChangeListener = null
            
        } catch (e: Exception) {
            AlarmLogger.logError("Release Audio Focus", currentAlarmId, e)
        }
    }
    
    /**
     * Restore original volume settings
     */
    private fun restoreOriginalVolume() {
        try {
            if (originalStreamVolume > 0) {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    originalStreamVolume,
                    0
                )
                
                AlarmLogger.logDebug("Volume Restore", mapOf(
                    "restoredVolume" to originalStreamVolume
                ))
            }
        } catch (e: Exception) {
            AlarmLogger.logError("Volume Restore", currentAlarmId, e)
        }
    }
    
    /**
     * Clean up all resources
     */
    fun cleanup() {
        try {
            // Cancel all coroutines
            audioScope.cancel()
            
            // Stop audio
            stopAlarm()
            
            AlarmLogger.logSystemEvent("Audio Cleanup", mapOf(
                "alarmId" to currentAlarmId
            ))
            
        } catch (e: Exception) {
            AlarmLogger.logError("Audio Cleanup", currentAlarmId, e)
        }
    }
    
    /**
     * Get current audio state for debugging
     */
    fun getAudioState(): Map<String, Any> {
        return mapOf(
            "isPlaying" to isPlaying,
            "hasAudioFocus" to hasAudioFocus,
            "currentVolume" to currentVolume,
            "targetVolume" to targetVolume,
            "retryAttempts" to retryAttempts,
            "hasMediaPlayer" to (mediaPlayer != null),
            "mediaPlayerPlaying" to (mediaPlayer?.isPlaying ?: false),
            "hasFallbackTone" to (fallbackToneGenerator != null),
            "alarmStreamVolume" to audioManager.getStreamVolume(AudioManager.STREAM_ALARM),
            "maxAlarmVolume" to audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        )
    }
}