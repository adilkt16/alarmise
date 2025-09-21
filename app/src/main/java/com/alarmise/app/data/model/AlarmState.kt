package com.alarmise.app.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Comprehensive alarm state management for precise timing requirements
 * Tracks the complete lifecycle of an alarm from creation to completion
 */
@Parcelize
enum class AlarmState : Parcelable {
    /**
     * Alarm has been created but not yet scheduled with the system
     */
    CREATED,
    
    /**
     * Alarm is scheduled with AlarmManager and waiting to trigger
     * This is the primary state for alarms that are set to go off in the future
     */
    SCHEDULED,
    
    /**
     * Alarm is currently playing and requiring user interaction
     * This state indicates the alarm has triggered and needs math puzzle solution
     */
    ACTIVE,
    
    /**
     * Alarm was successfully dismissed by solving the math puzzle
     * This is the successful completion state
     */
    DISMISSED,
    
    /**
     * Alarm reached its end time without being dismissed
     * Auto-stop safety mechanism activated
     */
    EXPIRED,
    
    /**
     * Alarm was manually cancelled before it could trigger
     * User cancelled the alarm before start time
     */
    CANCELLED,
    
    /**
     * Alarm encountered an error during scheduling or playback
     * System error state for troubleshooting
     */
    ERROR;
    
    /**
     * Check if the alarm is currently in a "finished" state
     */
    fun isFinished(): Boolean = when (this) {
        DISMISSED, EXPIRED, CANCELLED, ERROR -> true
        else -> false
    }
    
    /**
     * Check if the alarm is currently scheduled and waiting
     */
    fun isWaiting(): Boolean = when (this) {
        SCHEDULED -> true
        else -> false
    }
    
    /**
     * Check if the alarm is currently active and playing
     */
    fun isPlaying(): Boolean = when (this) {
        ACTIVE -> true
        else -> false
    }
    
    /**
     * Get valid state transitions from current state
     * Enforces proper state machine behavior
     */
    fun getValidTransitions(): Set<AlarmState> = when (this) {
        CREATED -> setOf(SCHEDULED, CANCELLED, ERROR)
        SCHEDULED -> setOf(ACTIVE, CANCELLED, EXPIRED, ERROR)
        ACTIVE -> setOf(DISMISSED, EXPIRED, ERROR)
        DISMISSED -> emptySet() // Terminal state
        EXPIRED -> emptySet() // Terminal state
        CANCELLED -> emptySet() // Terminal state
        ERROR -> setOf(SCHEDULED, CANCELLED) // Can retry scheduling
    }
    
    /**
     * Check if transition to new state is valid
     */
    fun canTransitionTo(newState: AlarmState): Boolean {
        return newState in getValidTransitions()
    }
    
    /**
     * Get human-readable description of the state
     */
    fun getDescription(): String = when (this) {
        CREATED -> "Created"
        SCHEDULED -> "Scheduled"
        ACTIVE -> "Playing"
        DISMISSED -> "Solved & Dismissed"
        EXPIRED -> "Auto-Expired"
        CANCELLED -> "Cancelled"
        ERROR -> "Error"
    }
    
    /**
     * Get state transition log message
     */
    fun getTransitionLogMessage(fromState: AlarmState): String {
        return "Alarm state changed: ${fromState.getDescription()} -> ${getDescription()}"
    }
    
    companion object {
        /**
         * Get the initial state for a new alarm
         */
        fun getInitialState(): AlarmState = CREATED
        
        /**
         * Get all terminal states (no further transitions possible)
         */
        fun getTerminalStates(): Set<AlarmState> = setOf(DISMISSED, EXPIRED, CANCELLED)
        
        /**
         * Get all active operation states (non-terminal)
         */
        fun getActiveStates(): Set<AlarmState> = setOf(CREATED, SCHEDULED, ACTIVE)
    }
}

/**
 * Data class to track state transitions with timestamps
 * Used for debugging and audit trail
 */
@Parcelize
data class AlarmStateTransition(
    val fromState: AlarmState,
    val toState: AlarmState,
    val timestamp: Long = System.currentTimeMillis(),
    val reason: String? = null
) : Parcelable {
    
    fun getLogMessage(): String {
        val reasonText = reason?.let { " (Reason: $it)" } ?: ""
        return "${toState.getTransitionLogMessage(fromState)}$reasonText at ${timestamp}"
    }
}