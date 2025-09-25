package com.alarmise.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alarmise.app.data.model.Alarm
import com.alarmise.app.data.model.MathPuzzle
import com.alarmise.app.data.repository.AlarmRepository
import com.alarmise.app.service.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    // Observe all alarms
    val alarms = alarmRepository.getAllAlarms()
    
    // Observe active alarm specifically (critical for UI state)
    private val _activeAlarm = MutableStateFlow<Alarm?>(null)
    val activeAlarm: StateFlow<Alarm?> = _activeAlarm.asStateFlow()
    
    init {
        loadInitialData()
        observeActiveAlarm()
    }
    
    /**
     * Load initial data and check for active alarms
     */
    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                // Load active alarm
                val activeAlarm = alarmRepository.getActiveAlarm()
                _activeAlarm.value = activeAlarm
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    activeAlarm = activeAlarm
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load alarms: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Observe active alarm changes
     */
    private fun observeActiveAlarm() {
        viewModelScope.launch {
            alarmRepository.getActiveAlarms().combine(_uiState) { activeAlarms, currentState ->
                val activeAlarm = activeAlarms.firstOrNull()
                _activeAlarm.value = activeAlarm
                currentState.copy(activeAlarm = activeAlarm)
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }
    
    /**
     * Create and schedule a new alarm
     * Implements core requirement: persistent alarm with math puzzle dismissal
     */
    fun setAlarm(
        startTime: LocalTime, 
        endTime: LocalTime, 
        label: String = "Alarm",
        puzzleDifficulty: MathPuzzle.Difficulty = MathPuzzle.Difficulty.MEDIUM
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                // Validate alarm times
                if (!isValidAlarmTime(startTime, endTime)) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Invalid alarm time configuration"
                    )
                    return@launch
                }
                
                // Create alarm in repository (this deactivates existing alarms)
                val result = alarmRepository.createAlarm(
                    startTime = startTime,
                    endTime = endTime,
                    label = label.ifBlank { "Alarm" },
                    puzzleDifficulty = puzzleDifficulty
                )
                
                result.fold(
                    onSuccess = { alarmId ->
                        // Activate the alarm
                        alarmRepository.activateAlarm(alarmId)
                        
                        // Get the created alarm and schedule with system AlarmManager
                        viewModelScope.launch {
                            try {
                                val createdAlarm = alarmRepository.getById(alarmId)
                                if (createdAlarm != null) {
                                    val scheduleResult = alarmScheduler.scheduleAlarm(createdAlarm)
                                    if (scheduleResult.isFailure) {
                                        _uiState.value = _uiState.value.copy(
                                            isLoading = false,
                                            error = "Alarm created but failed to schedule: ${scheduleResult.exceptionOrNull()?.message}"
                                        )
                                        return@launch
                                    }
                                }
                            } catch (e: Exception) {
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    error = "Alarm created but failed to schedule: ${e.message}"
                                )
                                return@launch
                            }
                        }
                        
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            message = "Alarm set for ${formatTime(startTime)} - ${formatTime(endTime)}"
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Failed to set alarm: ${error.message}"
                        )
                    }
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to set alarm: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Cancel an existing alarm
     */
    fun cancelAlarm(alarmId: Long) {
        viewModelScope.launch {
            try {
                val result = alarmRepository.deleteAlarmById(alarmId)
                
                result.fold(
                    onSuccess = {
                        // Cancel system alarm
                        // cancelSystemAlarm(alarmId)
                        
                        _uiState.value = _uiState.value.copy(
                            message = "Alarm cancelled",
                            activeAlarm = null
                        )
                        _activeAlarm.value = null
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            error = "Failed to cancel alarm: ${error.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to cancel alarm: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Toggle alarm enabled state
     */
    fun toggleAlarmEnabled(alarmId: Long, enabled: Boolean) {
        viewModelScope.launch {
            try {
                val result = alarmRepository.setAlarmEnabled(alarmId, enabled)
                
                result.fold(
                    onSuccess = {
                        val action = if (enabled) "enabled" else "disabled"
                        _uiState.value = _uiState.value.copy(
                            message = "Alarm $action"
                        )
                        
                        // Update system alarm scheduling
                        if (enabled) {
                            // rescheduleAlarm(alarmId)
                        } else {
                            // cancelSystemAlarm(alarmId)
                        }
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            error = "Failed to update alarm: ${error.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to update alarm: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Check if alarm should be playing now
     * Critical for maintaining persistent alarm state
     */
    fun checkAlarmStatus() {
        viewModelScope.launch {
            try {
                val activeAlarm = alarmRepository.getActiveAlarm()
                
                if (activeAlarm != null && activeAlarm.isCurrentlyActive()) {
                    // Alarm should be playing
                    if (!isAlarmCurrentlyPlaying()) {
                        // Start alarm service if not already playing
                        startAlarmPlayback(activeAlarm)
                    }
                } else if (activeAlarm != null && activeAlarm.hasExpired()) {
                    // Auto-stop expired alarm
                    autoStopExpiredAlarm(activeAlarm)
                }
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to check alarm status: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Handle alarm trigger (when start time is reached)
     */
    fun onAlarmTriggered(alarmId: Long) {
        viewModelScope.launch {
            try {
                // Mark alarm as triggered
                alarmRepository.markAlarmTriggered(alarmId)
                
                // Start alarm log
                alarmRepository.startAlarmLog(alarmId)
                
                // Start alarm service for continuous playback
                val alarm = alarmRepository.getAlarmById(alarmId)
                if (alarm != null) {
                    startAlarmPlayback(alarm)
                }
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to handle alarm trigger: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Create a quick test alarm (for development/testing)
     */
    fun createTestAlarm(durationMinutes: Int = 2) {
        val now = LocalTime.now()
        setAlarm(
            startTime = now.plusMinutes(1),
            endTime = now.plusMinutes((1 + durationMinutes).toLong()),
            label = "Test Alarm",
            puzzleDifficulty = MathPuzzle.Difficulty.EASY
        )
    }
    
    // ==================== UTILITY METHODS ====================
    
    /**
     * Validate alarm time configuration
     */
    private fun isValidAlarmTime(startTime: LocalTime, endTime: LocalTime): Boolean {
        // Calculate duration
        val startMinutes = startTime.hour * 60 + startTime.minute
        val endMinutes = endTime.hour * 60 + endTime.minute
        
        val duration = if (endMinutes > startMinutes) {
            endMinutes - startMinutes
        } else {
            // Cross midnight
            24 * 60 - startMinutes + endMinutes
        }
        
        // Minimum 1 minute, maximum 12 hours
        return duration in 1..(12 * 60)
    }
    
    /**
     * Format time for display
     */
    private fun formatTime(time: LocalTime): String {
        return String.format("%02d:%02d", time.hour, time.minute)
    }
    
    /**
     * Check if alarm is currently playing
     */
    private fun isAlarmCurrentlyPlaying(): Boolean {
        // This would check if AlarmService is running
        // Implementation depends on service architecture
        return false
    }
    
    /**
     * Start alarm playback service
     */
    private fun startAlarmPlayback(alarm: Alarm) {
        // Start AlarmService with foreground service
        // Implementation would use Intent to start service
    }
    
    /**
     * Auto-stop expired alarm
     */
    private fun autoStopExpiredAlarm(alarm: Alarm) {
        viewModelScope.launch {
            try {
                // Stop any active log
                val activeLog = alarmRepository.getActiveLog()
                if (activeLog != null) {
                    alarmRepository.stopAlarmLog(
                        logId = activeLog.id,
                        stoppedBy = com.alarmise.app.data.model.AlarmLog.StoppedBy.AUTO_STOP_END_TIME
                    )
                }
                
                // Deactivate the alarm
                alarmRepository.deactivateAllAlarms()
                
                _uiState.value = _uiState.value.copy(
                    message = "Alarm auto-stopped at end time",
                    activeAlarm = null
                )
                _activeAlarm.value = null
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to auto-stop alarm: ${e.message}"
                )
            }
        }
    }
    
    // ==================== UI STATE MANAGEMENT ====================
    
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun setLoading(loading: Boolean) {
        _uiState.value = _uiState.value.copy(isLoading = loading)
    }
}

/**
 * UI State for MainActivity
 * Represents all the state needed for the main screen
 */
data class MainUiState(
    val isLoading: Boolean = false,
    val activeAlarm: Alarm? = null,
    val message: String? = null,
    val error: String? = null,
    val permissionsGranted: Boolean = false,
    val batteryOptimizationDisabled: Boolean = false
)
