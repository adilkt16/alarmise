package com.alarmise.app.di

import android.content.Context
import androidx.room.Room
import com.alarmise.app.data.database.AlarmDatabase
import com.alarmise.app.data.database.AlarmDao
import com.alarmise.app.data.database.AlarmLogDao
// import com.alarmise.app.data.repository.AlarmRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideAlarmDatabase(@ApplicationContext context: Context): AlarmDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AlarmDatabase::class.java,
            "alarm_database"
        )
        .fallbackToDestructiveMigration()
        .build()
    }
    
    @Provides
    fun provideAlarmDao(database: AlarmDatabase): AlarmDao {
        return database.alarmDao()
    }
    
    @Provides
    fun provideAlarmLogDao(database: AlarmDatabase): AlarmLogDao {
        return database.alarmLogDao()
    }
    
//     @Provides
//     @Singleton
//     fun provideAlarmRepository(
//         alarmDao: AlarmDao,
//         alarmLogDao: AlarmLogDao
//     ): AlarmRepository {
//         return AlarmRepository(alarmDao, alarmLogDao)
//     }
}
