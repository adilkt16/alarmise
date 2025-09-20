package com.alarmise.app

import android.app.Application
import com.alarmise.app.utils.AlarmStateManager
import com.alarmise.app.utils.AppLifecycleObserver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AlarmiseApplication : Application() {
    
    @Inject
    lateinit var appLifecycleObserver: AppLifecycleObserver
    
    @Inject
    lateinit var alarmStateManager: AlarmStateManager
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize alarm state management
        // This ensures alarms work correctly across app lifecycle
        alarmStateManager.initialize()
        
        // Note: AppLifecycleObserver automatically registers itself
        // with ProcessLifecycleOwner in its constructor
    }
}
