package com.alarmise.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AlarmiseApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
    }
}
