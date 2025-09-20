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
    val isActive: Boolean = false,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val label: String = "Alarm",
    val puzzleDifficulty: MathPuzzle.Difficulty = MathPuzzle.Difficulty.MEDIUM,
    val lastTriggered: Long? = null,
    val isOneTime: Boolean = true // For future recurring alarm support
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
     * This is the core logic for persistent alarm behavior
     */
    fun isCurrentlyActive(): Boolean {
        if (!isEnabled || !isActive) return false
        
        val now = LocalTime.now()
        
        return if (endTime.isAfter(startTime)) {
            // Same day scenario: 09:00 -> 10:00
            now.isAfter(startTime) && now.isBefore(endTime)
        } else {
            // Cross midnight scenario: 23:00 -> 07:00
            now.isAfter(startTime) || now.isBefore(endTime)
        }
    }
    
    /**
     * Check if alarm should be triggered now (within 1 minute of start time)
     */
    fun shouldTriggerNow(): Boolean {
        if (!isEnabled) return false
        
        val now = LocalTime.now()
        val triggerWindow = 1 // minutes
        
        // Check if current time is within trigger window of start time
        val startMinutes = startTime.hour * 60 + startTime.minute
        val nowMinutes = now.hour * 60 + now.minute
        
        return when {
            // Same day scenario
            endTime.isAfter(startTime) -> {
                nowMinutes >= startMinutes && nowMinutes < startMinutes + triggerWindow
            }
            // Cross midnight scenario
            else -> {
                nowMinutes >= startMinutes || nowMinutes < startMinutes + triggerWindow
            }
        }
    }
    
    /**
     * Check if alarm has expired (past end time)
     */
    fun hasExpired(): Boolean {
        val now = LocalTime.now()
        
        return if (endTime.isAfter(startTime)) {
            // Same day: expired if current time is after end time
            now.isAfter(endTime)
        } else {
            // Cross midnight: expired if between end and start time
            now.isAfter(endTime) && now.isBefore(startTime)
        }
    }
    
    /**
     * Get human-readable duration string
     */
    fun getDurationString(): String {
        val minutes = getDurationInMinutes()
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        
        return when {
            hours == 0L -> "${remainingMinutes}m"
            remainingMinutes == 0L -> "${hours}h"
            else -> "${hours}h ${remainingMinutes}m"
        }
    }
    
    /**
     * Validate alarm configuration
     * Critical for ensuring alarm reliability
     */
    fun isValid(): Boolean {
        // Minimum duration check (at least 1 minute)
        if (getDurationInMinutes() < 1) return false
        
        // Maximum duration check (24 hours max)
        if (getDurationInMinutes() > 24 * 60) return false
        
        // Label validation
        if (label.isBlank()) return false
        
        return true
    }
    
    /**
     * Get the next math puzzle for this alarm
     */
    fun generateMathPuzzle(): MathPuzzle {
        return MathPuzzle.generate(puzzleDifficulty)
    }
    
    companion object {
        /**
         * Create a new alarm with validation
         * Follows the critical behavioral requirements from context
         */
        fun create(
            startTime: LocalTime,
            endTime: LocalTime,
            label: String = "Alarm",
            puzzleDifficulty: MathPuzzle.Difficulty = MathPuzzle.Difficulty.MEDIUM
        ): Alarm? {
            val alarm = Alarm(
                startTime = startTime,
                endTime = endTime,
                label = label.ifBlank { "Alarm" },
                puzzleDifficulty = puzzleDifficulty,
                isActive = false, // Will be activated when scheduled
                isEnabled = true
            )
            
            return if (alarm.isValid()) alarm else null
        }
        
        /**
         * Create an immediate test alarm (for testing purposes)
         */
        fun createTestAlarm(durationMinutes: Int = 5): Alarm {
            val now = LocalTime.now()
            return Alarm(
                startTime = now.plusMinutes(1),
                endTime = now.plusMinutes((1 + durationMinutes).toLong()),
                label = "Test Alarm",
                puzzleDifficulty = MathPuzzle.Difficulty.EASY,
                isActive = false,
                isEnabled = true
            )
        }
    }
}
