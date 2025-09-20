package com.alarmise.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alarmise.app.service.AlarmService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {
    
    companion object {
        const val EXTRA_ALARM_ID = "extra_alarm_id"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
        
        if (alarmId != -1L) {
            // Start the alarm service
            val serviceIntent = Intent(context, AlarmService::class.java).apply {
                action = AlarmService.ACTION_START_ALARM
                putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
            }
            context.startForegroundService(serviceIntent)
        }
    }
}
