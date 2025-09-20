package com.alarmise.app.di

import android.content.Context
import com.alarmise.app.service.AlarmScheduler
import com.alarmise.app.utils.NotificationUtils
import com.alarmise.app.utils.PermissionUtils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    
    @Provides
    @Singleton
    fun provideAlarmScheduler(
        @ApplicationContext context: Context
    ): AlarmScheduler {
        return AlarmScheduler(context)
    }
    
    @Provides
    @Singleton
    fun provideNotificationUtils(
        @ApplicationContext context: Context
    ): NotificationUtils {
        return NotificationUtils(context)
    }
    
    @Provides
    @Singleton
    fun providePermissionUtils(
        @ApplicationContext context: Context
    ): PermissionUtils {
        return PermissionUtils(context)
    }
}
