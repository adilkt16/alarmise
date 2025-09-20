package com.alarmise.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarm_logs")
data class AlarmLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val alarmId: Long,
    val startedAt: Long,
    val stoppedAt: Long? = null,
    val stoppedBy: StoppedBy? = null,
    val puzzlesSolved: Int = 0,
    val puzzleAttempts: Int = 0
) {
    enum class StoppedBy {
        USER_SOLVED_PUZZLE,
        AUTO_STOP_END_TIME,
        USER_CANCELLED // Should rarely happen
    }
    
    val durationInMillis: Long
        get() = (stoppedAt ?: System.currentTimeMillis()) - startedAt
        
    val isActive: Boolean
        get() = stoppedAt == null
}
