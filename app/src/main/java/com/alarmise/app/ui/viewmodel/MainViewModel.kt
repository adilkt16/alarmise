package com.alarmise.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alarmise.app.data.model.Alarm
import com.alarmise.app.data.repository.AlarmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val alarmRepository: AlarmRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    val alarms = alarmRepository.getAllAlarms()
    
    init {
        loadActiveAlarm()
    }
    
    fun setAlarm(startTime: LocalTime, endTime: LocalTime, label: String = "Alarm") {
        viewModelScope.launch {
            try {
                // Deactivate all existing alarms first
                alarmRepository.deactivateAllAlarms()
                
                // Create new alarm
                val alarm = Alarm(
                    startTime = startTime,
                    endTime = endTime,
                    label = label,
                    isActive = true,
                    isEnabled = true
                )
                
                val alarmId = alarmRepository.insertAlarm(alarm)
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Alarm set successfully"
                )
                
                // Schedule the alarm using AlarmManager
                // This will be implemented in the AlarmManagerService
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to set alarm: ${e.message}"
                )
            }
        }
    }
    
    fun cancelAlarm(alarmId: Long) {
        viewModelScope.launch {
            try {
                alarmRepository.deleteAlarmById(alarmId)
                _uiState.value = _uiState.value.copy(
                    message = "Alarm cancelled"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to cancel alarm: ${e.message}"
                )
            }
        }
    }
    
    private fun loadActiveAlarm() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val activeAlarm = alarmRepository.getActiveAlarm()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    activeAlarm = activeAlarm
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load active alarm: ${e.message}"
                )
            }
        }
    }
    
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class MainUiState(
    val isLoading: Boolean = false,
    val activeAlarm: Alarm? = null,
    val message: String? = null,
    val error: String? = null
)
