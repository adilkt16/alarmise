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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@AndroidEntryPoint
class AlarmService : Service() {
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    companion object {
        const val ACTION_START_ALARM = "com.alarmise.app.START_ALARM"
        const val ACTION_STOP_ALARM = "com.alarmise.app.STOP_ALARM"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val NOTIFICATION_ID = 1001
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ALARM -> {
                val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
                startAlarm(alarmId)
            }
            ACTION_STOP_ALARM -> {
                stopAlarm()
            }
        }
        
        return START_STICKY // Restart if killed by system
    }
    
    private fun startAlarm(alarmId: Long) {
        createNotification()
        startAlarmSound()
    }
    
    private fun stopAlarm() {
        stopAlarmSound()
        stopForeground(true)
        stopSelf()
    }
    
    private fun createNotification() {
        val notification = NotificationCompat.Builder(this, "alarm_channel")
            .setContentTitle(getString(R.string.alarm_playing))
            .setContentText(getString(R.string.solve_math_to_stop))
            .setSmallIcon(R.drawable.ic_alarm_24)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
            
        startForeground(NOTIFICATION_ID, notification)
    }
    
    private fun startAlarmSound() {
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                
                // Set to use alarm stream for maximum volume
                setAudioStreamType(AudioManager.STREAM_ALARM)
                
                // Use system alarm sound
                setDataSource(this@AlarmService, android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI)
                
                isLooping = true
                setVolume(1.0f, 1.0f)
                
                prepare()
                start()
            }
        } catch (e: Exception) {
            // Fallback to system default alarm sound
            e.printStackTrace()
        }
    }
    
    private fun stopAlarmSound() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
    }
    
    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "Alarmise::AlarmWakeLock"
        )
        wakeLock?.acquire(60 * 60 * 1000L) // 1 hour max
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopAlarmSound()
        wakeLock?.release()
        serviceScope.cancel()
    }
}
