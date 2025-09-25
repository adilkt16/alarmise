package com.alarmise.app.ui.activity

import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.alarmise.app.data.model.Alarm
import com.alarmise.app.data.model.MathPuzzle
import com.alarmise.app.ui.theme.AlarmiseTheme
import com.alarmise.app.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private val viewModel: MainViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AlarmiseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AlarmiseMainScreen(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmiseMainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val alarms by viewModel.alarms.collectAsState(initial = emptyList())
    val activeAlarm by viewModel.activeAlarm.collectAsState()
    val context = LocalContext.current
    
    // Time picker state
    var showTimePickerDialog by remember { mutableStateOf(false) }
    var isSelectingStartTime by remember { mutableStateOf(true) }
    var startTime by remember { mutableStateOf(LocalTime.now().plusMinutes(1)) }
    var endTime by remember { mutableStateOf(LocalTime.now().plusHours(1)) }
    var alarmLabel by remember { mutableStateOf("Wake Up") }
    var selectedDifficulty by remember { mutableStateOf(MathPuzzle.Difficulty.MEDIUM) }
    
    // Validation state
    var timeValidationError by remember { mutableStateOf<String?>(null) }
    var labelError by remember { mutableStateOf<String?>(null) }
    
    // Snackbar state
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Handle UI state messages
    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            viewModel.clearMessage()
        }
    }
    
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Long
            )
            viewModel.clearError()
        }
    }
    
    // Validation logic
    LaunchedEffect(startTime, endTime) {
        timeValidationError = validateAlarmTimes(startTime, endTime)
    }
    
    LaunchedEffect(alarmLabel) {
        labelError = if (alarmLabel.isBlank()) "Alarm label cannot be empty" else null
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Alarmise") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Active Alarm Status Card (use both sources for debugging)
            ActiveAlarmCard(
                activeAlarm = activeAlarm ?: uiState.activeAlarm,
                onCancelAlarm = { alarm -> viewModel.cancelAlarm(alarm.id) }
            )
            
            // Debug: Show all alarms
            if (alarms.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Debug: All Alarms (${alarms.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        alarms.forEach { alarm ->
                            Text(
                                text = "ID: ${alarm.id} | ${alarm.label} | ${formatTime(alarm.startTime)}-${formatTime(alarm.endTime)} | State: ${alarm.state.name}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            
            // Main Alarm Configuration Card
            AlarmConfigurationCard(
                startTime = startTime,
                endTime = endTime,
                alarmLabel = alarmLabel,
                selectedDifficulty = selectedDifficulty,
                timeValidationError = timeValidationError,
                labelError = labelError,
                isLoading = uiState.isLoading,
                onStartTimeClick = { 
                    isSelectingStartTime = true
                    showTimePickerDialog = true 
                },
                onEndTimeClick = { 
                    isSelectingStartTime = false
                    showTimePickerDialog = true 
                },
                onLabelChange = { alarmLabel = it },
                onDifficultyChange = { selectedDifficulty = it },
                onSetAlarm = {
                    if (timeValidationError == null && labelError == null) {
                        viewModel.setAlarm(
                            startTime = startTime,
                            endTime = endTime,
                            label = alarmLabel,
                            puzzleDifficulty = selectedDifficulty
                        )
                    }
                }
            )
            
            // App Information Card
            InformationCard()
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    
    // Time Picker Dialog
    if (showTimePickerDialog) {
        val currentTime = if (isSelectingStartTime) startTime else endTime
        val is24HourFormat = DateFormat.is24HourFormat(context)
        
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val selectedTime = LocalTime.of(hourOfDay, minute)
                if (isSelectingStartTime) {
                    startTime = selectedTime
                    // Auto-adjust end time if it's before or equal to start time
                    if (endTime.isBefore(startTime) || endTime == startTime) {
                        endTime = startTime.plusHours(1)
                    }
                } else {
                    endTime = selectedTime
                }
                showTimePickerDialog = false
            },
            currentTime.hour,
            currentTime.minute,
            is24HourFormat
        ).show()
    }
}

@Composable
fun ActiveAlarmCard(
    activeAlarm: Alarm?,
    onCancelAlarm: (Alarm) -> Unit
) {
    AnimatedVisibility(
        visible = activeAlarm != null,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        activeAlarm?.let { alarm ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Active alarm card" },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = "Active alarm",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ACTIVE ALARM",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        
                        IconButton(
                            onClick = { onCancelAlarm(alarm) },
                            modifier = Modifier.semantics { 
                                contentDescription = "Cancel active alarm" 
                            }
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cancel alarm",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = alarm.label,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Alarm Times
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Start: ${formatTime(alarm.startTime)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "End: ${formatTime(alarm.endTime)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        
                        Text(
                            text = alarm.getDurationString(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Alarm Status and Details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Status: ${alarm.state.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "ID: ${alarm.id}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        
                        Text(
                            text = "Puzzle: ${alarm.puzzleDifficulty.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmConfigurationCard(
    startTime: LocalTime,
    endTime: LocalTime,
    alarmLabel: String,
    selectedDifficulty: MathPuzzle.Difficulty,
    timeValidationError: String?,
    labelError: String?,
    isLoading: Boolean,
    onStartTimeClick: () -> Unit,
    onEndTimeClick: () -> Unit,
    onLabelChange: (String) -> Unit,
    onDifficultyChange: (MathPuzzle.Difficulty) -> Unit,
    onSetAlarm: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Alarm configuration" },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Set New Alarm",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // Alarm Label Input
            OutlinedTextField(
                value = alarmLabel,
                onValueChange = onLabelChange,
                label = { Text("Alarm Label") },
                placeholder = { Text("Enter alarm description") },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Alarm label input field" },
                isError = labelError != null,
                supportingText = {
                    labelError?.let { 
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Alarm label"
                    )
                }
            )
            
            // Time Selection Section
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Alarm Duration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Start Time Button
                        OutlinedButton(
                            onClick = onStartTimeClick,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { 
                                    contentDescription = "Select start time: ${formatTime(startTime)}" 
                                }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text("Start")
                                Text(
                                    text = formatTime(startTime),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        // Duration Indicator
                        Card(
                            modifier = Modifier.align(Alignment.CenterVertically),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = calculateDurationString(startTime, endTime),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        
                        // End Time Button
                        OutlinedButton(
                            onClick = onEndTimeClick,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { 
                                    contentDescription = "Select end time: ${formatTime(endTime)}" 
                                }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text("End")
                                Text(
                                    text = formatTime(endTime),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    
                    // Time Validation Error
                    timeValidationError?.let { error ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            
            // Math Puzzle Difficulty Selection
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Puzzle Difficulty",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Text(
                        text = "Choose how challenging the math puzzle should be to turn off the alarm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MathPuzzle.Difficulty.values().forEach { difficulty ->
                            val isSelected = selectedDifficulty == difficulty
                            FilterChip(
                                onClick = { onDifficultyChange(difficulty) },
                                label = { 
                                    Text(
                                        text = difficulty.name.lowercase().replaceFirstChar { it.uppercase() },
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ) 
                                },
                                selected = isSelected,
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { 
                                        contentDescription = "Select ${difficulty.name.lowercase()} difficulty" 
                                    }
                            )
                        }
                    }
                    
                    // Difficulty explanation
                    val difficultyExplanation = when (selectedDifficulty) {
                        MathPuzzle.Difficulty.EASY -> "Simple addition and subtraction (e.g., 12 + 8 = ?)"
                        MathPuzzle.Difficulty.MEDIUM -> "Basic multiplication and division (e.g., 7 × 6 = ?)"
                        MathPuzzle.Difficulty.HARD -> "Complex multi-step problems (e.g., 15 × 3 + 8 = ?)"
                    }
                    
                    Text(
                        text = difficultyExplanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            
            // Set Alarm Button
            Button(
                onClick = onSetAlarm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics { contentDescription = "Set alarm button" },
                enabled = !isLoading && timeValidationError == null && labelError == null
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Setting Alarm...")
                } else {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SET ALARM",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun InformationCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Information",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "How Alarmise Works",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            
            val infoPoints = listOf(
                "🔊 Alarm plays continuously between start and end times",
                "🧮 Only way to stop: solve a math puzzle correctly",
                "⏰ Alarm automatically stops at the end time",
                "🔋 Runs in background - device stays awake during alarm",
                "📱 Works even when app is closed"
            )
            
            infoPoints.forEach { point ->
                Text(
                    text = point,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

// Helper functions
private fun validateAlarmTimes(startTime: LocalTime, endTime: LocalTime): String? {
    return when {
        startTime == endTime -> "Start and end times cannot be the same"
        startTime.isAfter(endTime) -> {
            val duration = ChronoUnit.MINUTES.between(startTime, endTime.plus(1, ChronoUnit.DAYS))
            if (duration > 720) "Alarm duration too long (maximum 12 hours)"
            else null
        }
        else -> {
            val duration = ChronoUnit.MINUTES.between(startTime, endTime)
            if (duration > 720) "Alarm duration too long (maximum 12 hours)"
            else null
        }
    }
}

private fun calculateDurationString(startTime: LocalTime, endTime: LocalTime): String {
    val duration = if (startTime.isAfter(endTime)) {
        // Crossing midnight: calculate from start to midnight + from midnight to end
        val toMidnight = ChronoUnit.MINUTES.between(startTime, LocalTime.MAX)
        val fromMidnight = ChronoUnit.MINUTES.between(LocalTime.MIN, endTime)
        toMidnight + fromMidnight + 1 // +1 for the minute at midnight
    } else {
        ChronoUnit.MINUTES.between(startTime, endTime)
    }
    
    val hours = duration / 60
    val minutes = duration % 60
    
    return when {
        hours == 0L -> "${minutes}min"
        minutes == 0L -> "${hours}h"
        else -> "${hours}h ${minutes}min"
    }
}

private fun formatTime(time: LocalTime): String {
    return time.format(DateTimeFormatter.ofPattern("HH:mm"))
}

// Extension function for Alarm duration
private fun Alarm.getDurationString(): String {
    return calculateDurationString(this.startTime, this.endTime)
}

@Preview(showBackground = true)
@Composable
fun AlarmiseMainScreenPreview() {
    AlarmiseTheme {
        AlarmiseMainScreen()
    }
}
