package com.alarmise.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.alarmise.app.service.AlarmService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {
    
    companion object {
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        private const val TAG = "AlarmReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
        
        Log.d(TAG, "🔔 AlarmReceiver.onReceive() called!")
        Log.d(TAG, "🔔 Intent action: ${intent.action}")
        Log.d(TAG, "🔔 Alarm ID: $alarmId")
        Log.d(TAG, "🔔 Intent extras: ${intent.extras}")
        
        if (alarmId != -1L) {
            Log.d(TAG, "🔔 Starting AlarmService for alarm ID: $alarmId")
            
            // Start the alarm service
            val serviceIntent = Intent(context, AlarmService::class.java).apply {
                action = AlarmService.ACTION_START_ALARM
                putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
            }
            
            try {
                context.startForegroundService(serviceIntent)
                Log.d(TAG, "🔔 AlarmService started successfully!")
            } catch (e: Exception) {
                Log.e(TAG, "🔔 Error starting AlarmService: ${e.message}", e)
            }
        } else {
            Log.e(TAG, "🔔 Invalid alarm ID received: $alarmId")
        }
    }
}
