package com.alarmise.app.data.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import java.time.LocalTime

@Parcelize
@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val isActive: Boolean = true,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val label: String = "Alarm"
) : Parcelable {
    
    /**
     * Calculate the duration between start and end time in minutes
     */
    fun getDurationInMinutes(): Long {
        val startMinutes = startTime.hour * 60 + startTime.minute
        val endMinutes = endTime.hour * 60 + endTime.minute
        
        return if (endMinutes > startMinutes) {
            (endMinutes - startMinutes).toLong()
        } else {
            // Handle case where end time is next day
            (24 * 60 - startMinutes + endMinutes).toLong()
        }
    }
    
    /**
     * Check if the alarm is currently supposed to be playing
     */
    fun isCurrentlyActive(): Boolean {
        val now = LocalTime.now()
        
        return if (endTime.isAfter(startTime)) {
            // Same day scenario
            now.isAfter(startTime) && now.isBefore(endTime)
        } else {
            // Cross midnight scenario
            now.isAfter(startTime) || now.isBefore(endTime)
        }
    }
}
