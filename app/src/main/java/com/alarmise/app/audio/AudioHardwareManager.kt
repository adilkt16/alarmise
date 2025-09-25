package com.alarmise.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.alarmise.app.utils.AlarmLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AudioHardwareManager handles hardware-related audio events
 * Critical for maintaining alarm playback during hardware changes
 * 
 * Handles:
 * - Headphone/Bluetooth device connect/disconnect
 * - Audio route changes
 * - Hardware audio failures
 * - Recovery after hardware events
 */
@Singleton
class AudioHardwareManager @Inject constructor(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "AudioHardwareManager"
        private const val HARDWARE_RECOVERY_DELAY_MS = 1000L
    }
    
    // Hardware event callbacks
    interface AudioHardwareCallback {
        fun onHeadphonesDisconnected()
        fun onHeadphonesConnected()
        fun onBluetoothDisconnected()
        fun onBluetoothConnected()
        fun onAudioRoutingChanged(newRoute: String)
        fun onHardwareAudioFailure(reason: String)
    }
    
    private var callback: AudioHardwareCallback? = null
    private var isRegistered = false
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    // Broadcast receiver for hardware events
    private val hardwareReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.ACTION_HEADSET_PLUG -> {
                    handleHeadsetPlug(intent)
                }
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    handleAudioBecomingNoisy()
                }
                Intent.ACTION_HEADSET_PLUG -> {
                    handleHeadsetPlug(intent)
                }
                "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED" -> {
                    handleBluetoothHeadsetChange(intent)
                }
                "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED" -> {
                    handleBluetoothA2dpChange(intent)
                }
            }
        }
    }
    
    /**
     * Register for hardware audio events
     */
    fun registerHardwareEvents(callback: AudioHardwareCallback) {
        if (isRegistered) {
            unregisterHardwareEvents()
        }
        
        this.callback = callback
        
        try {
            val filter = IntentFilter().apply {
                addAction(AudioManager.ACTION_HEADSET_PLUG)
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                addAction(Intent.ACTION_HEADSET_PLUG)
                addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED")
                addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
            }
            
            context.registerReceiver(hardwareReceiver, filter)
            isRegistered = true
            
            AlarmLogger.logSystemEvent("Hardware Events", mapOf(
                "registered" to true,
                "actions" to filter.countActions()
            ))
            
        } catch (e: Exception) {
            AlarmLogger.logError("Hardware Registration", null, e)
        }
    }
    
    /**
     * Unregister hardware events
     */
    fun unregisterHardwareEvents() {
        if (isRegistered) {
            try {
                context.unregisterReceiver(hardwareReceiver)
                isRegistered = false
                callback = null
                
                AlarmLogger.logSystemEvent("Hardware Events", mapOf("registered" to false))
                
            } catch (e: Exception) {
                AlarmLogger.logError("Hardware Unregistration", null, e)
            }
        }
    }
    
    /**
     * Handle headset plug/unplug events
     */
    private fun handleHeadsetPlug(intent: Intent) {
        val state = intent.getIntExtra("state", -1)
        val name = intent.getStringExtra("name") ?: "Unknown"
        val microphone = intent.getIntExtra("microphone", -1)
        
        AlarmLogger.logSystemEvent("Headset Event", mapOf(
            "state" to state,
            "name" to name,
            "hasMicrophone" to (microphone == 1)
        ))
        
        when (state) {
            0 -> {
                // Headphones disconnected - CRITICAL: Alarm must continue on speaker
                AlarmLogger.logWarning("Headset", "Headphones disconnected - ensuring alarm continues on speaker")
                
                // Delay callback to allow audio system to stabilize
                handler.postDelayed({
                    callback?.onHeadphonesDisconnected()
                }, HARDWARE_RECOVERY_DELAY_MS)
            }
            1 -> {
                // Headphones connected - Alarm should continue on headphones
                AlarmLogger.logSystemEvent("Headset", mapOf("message" to "Headphones connected"))
                
                handler.postDelayed({
                    callback?.onHeadphonesConnected()
                }, HARDWARE_RECOVERY_DELAY_MS)
            }
        }
    }
    
    /**
     * Handle audio becoming noisy (usually headphone disconnect)
     */
    private fun handleAudioBecomingNoisy() {
        AlarmLogger.logWarning("Audio Hardware", "Audio becoming noisy - likely headphone disconnect")
        
        // For regular apps, this would pause music, but for alarms we must continue
        // The alarm MUST continue playing on speaker per requirements
        handler.postDelayed({
            callback?.onHeadphonesDisconnected()
        }, HARDWARE_RECOVERY_DELAY_MS)
    }
    
    /**
     * Handle Bluetooth headset connection changes
     */
    private fun handleBluetoothHeadsetChange(intent: Intent) {
        val state = intent.getIntExtra("android.bluetooth.profile.extra.STATE", -1)
        
        AlarmLogger.logSystemEvent("Bluetooth Headset", mapOf("state" to state))
        
        when (state) {
            0 -> { // Disconnected
                AlarmLogger.logWarning("Bluetooth", "Bluetooth headset disconnected - ensuring alarm continues")
                handler.postDelayed({
                    callback?.onBluetoothDisconnected()
                }, HARDWARE_RECOVERY_DELAY_MS)
            }
            2 -> { // Connected
                AlarmLogger.logSystemEvent("Bluetooth", mapOf("message" to "Bluetooth headset connected"))
                handler.postDelayed({
                    callback?.onBluetoothConnected()
                }, HARDWARE_RECOVERY_DELAY_MS)
            }
        }
    }
    
    /**
     * Handle Bluetooth A2DP (audio) connection changes
     */
    private fun handleBluetoothA2dpChange(intent: Intent) {
        val state = intent.getIntExtra("android.bluetooth.profile.extra.STATE", -1)
        
        AlarmLogger.logSystemEvent("Bluetooth A2DP", mapOf("state" to state))
        
        when (state) {
            0 -> { // Disconnected
                AlarmLogger.logWarning("Bluetooth", "Bluetooth audio disconnected - ensuring alarm continues")
                handler.postDelayed({
                    callback?.onBluetoothDisconnected()
                }, HARDWARE_RECOVERY_DELAY_MS)
            }
            2 -> { // Connected
                AlarmLogger.logSystemEvent("Bluetooth", mapOf("message" to "Bluetooth audio connected"))
                handler.postDelayed({
                    callback?.onBluetoothConnected()
                }, HARDWARE_RECOVERY_DELAY_MS)
            }
        }
    }
    
    /**
     * Get current audio routing information
     */
    fun getCurrentAudioRoute(): String {
        return try {
            when {
                audioManager.isBluetoothScoOn -> "Bluetooth SCO"
                audioManager.isBluetoothA2dpOn -> "Bluetooth A2DP"
                audioManager.isWiredHeadsetOn -> "Wired Headset"
                audioManager.isSpeakerphoneOn -> "Speakerphone"
                else -> "Default (Speaker)"
            }
        } catch (e: Exception) {
            AlarmLogger.logError("Audio Route Check", null, e)
            "Unknown"
        }
    }
    
    /**
     * Force audio to speaker (critical for alarm continuation)
     */
    fun forceAudioToSpeaker() {
        try {
            // Ensure audio plays through speaker when headphones disconnect
            audioManager.isSpeakerphoneOn = true
            
            AlarmLogger.logSystemEvent("Force Speaker", mapOf(
                "speakerphoneOn" to audioManager.isSpeakerphoneOn
            ))
            
        } catch (e: Exception) {
            AlarmLogger.logError("Force Speaker", null, e)
        }
    }
    
    /**
     * Restore normal audio routing
     */
    fun restoreNormalAudioRouting() {
        try {
            audioManager.isSpeakerphoneOn = false
            
            AlarmLogger.logSystemEvent("Restore Audio Routing", mapOf(
                "speakerphoneOn" to audioManager.isSpeakerphoneOn
            ))
            
        } catch (e: Exception) {
            AlarmLogger.logError("Restore Audio Routing", null, e)
        }
    }
    
    /**
     * Check if audio hardware is in a problematic state
     */
    fun checkAudioHardwareHealth(): Map<String, Any> {
        return try {
            mapOf(
                "currentRoute" to getCurrentAudioRoute(),
                "bluetoothScoOn" to audioManager.isBluetoothScoOn,
                "bluetoothA2dpOn" to audioManager.isBluetoothA2dpOn,
                "wiredHeadsetOn" to audioManager.isWiredHeadsetOn,
                "speakerphoneOn" to audioManager.isSpeakerphoneOn,
                "musicActive" to audioManager.isMusicActive,
                "mode" to audioManager.mode,
                "ringerMode" to audioManager.ringerMode,
                "streamVolume" to audioManager.getStreamVolume(AudioManager.STREAM_ALARM),
                "maxStreamVolume" to audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            )
        } catch (e: Exception) {
            AlarmLogger.logError("Hardware Health Check", null, e)
            mapOf("error" to (e.message ?: "Unknown error"))
        }
    }
}