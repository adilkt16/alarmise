package com.alarmise.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alarmise.app.data.model.AlarmLog
import com.alarmise.app.data.model.MathPuzzle
import com.alarmise.app.data.repository.AlarmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlarmTriggerViewModel @Inject constructor(
    private val alarmRepository: AlarmRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(AlarmTriggerUiState())
    val uiState: StateFlow<AlarmTriggerUiState> = _uiState.asStateFlow()
    
    init {
        generateNewPuzzle()
    }
    
    fun submitAnswer(answer: String) {
        val currentState = _uiState.value
        val puzzle = currentState.currentPuzzle ?: return
        
        viewModelScope.launch {
            try {
                val numericAnswer = answer.toIntOrNull()
                if (numericAnswer == null) {
                    _uiState.value = currentState.copy(
                        isAnswerIncorrect = true,
                        attempts = currentState.attempts + 1
                    )
                    return@launch
                }
                
                if (puzzle.isCorrectAnswer(numericAnswer)) {
                    // Correct answer - stop the alarm
                    stopAlarm(AlarmLog.StoppedBy.USER_SOLVED_PUZZLE)
                    _uiState.value = currentState.copy(
                        isAlarmStopped = true,
                        puzzlesSolved = currentState.puzzlesSolved + 1
                    )
                } else {
                    // Incorrect answer - generate new puzzle
                    _uiState.value = currentState.copy(
                        isAnswerIncorrect = true,
                        attempts = currentState.attempts + 1
                    )
                    generateNewPuzzle()
                }
            } catch (e: Exception) {
                _uiState.value = currentState.copy(
                    error = "Error processing answer: ${e.message}"
                )
            }
        }
    }
    
    private fun generateNewPuzzle() {
        val newPuzzle = MathPuzzle.generate(MathPuzzle.Difficulty.MEDIUM)
        _uiState.value = _uiState.value.copy(
            currentPuzzle = newPuzzle,
            isAnswerIncorrect = false
        )
    }
    
    private suspend fun stopAlarm(stoppedBy: AlarmLog.StoppedBy) {
        try {
            val activeLog = alarmRepository.getActiveLog()
            activeLog?.let {
                alarmRepository.stopAlarmLog(
                    logId = it.id,
                    stoppedBy = stoppedBy
                )
            }
            
            // Deactivate the alarm
            alarmRepository.deactivateAllAlarms()
            
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                error = "Failed to stop alarm: ${e.message}"
            )
        }
    }
    
    fun autoStopAlarm() {
        viewModelScope.launch {
            stopAlarm(AlarmLog.StoppedBy.AUTO_STOP_END_TIME)
            _uiState.value = _uiState.value.copy(
                isAlarmStopped = true,
                isAutoStopped = true
            )
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class AlarmTriggerUiState(
    val currentPuzzle: MathPuzzle? = null,
    val isAnswerIncorrect: Boolean = false,
    val isAlarmStopped: Boolean = false,
    val isAutoStopped: Boolean = false,
    val puzzlesSolved: Int = 0,
    val attempts: Int = 0,
    val error: String? = null
)
