package com.alarmise.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    userPreferences: UserPreferences = UserPreferences(),
    onPreferenceChange: (UserPreferences) -> Unit = {}
) {
    var localPreferences by remember { mutableStateOf(userPreferences) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with back navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.semantics {
                    contentDescription = "Navigate back to main screen"
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Alarm Sound Settings
            item {
                SettingsSection(
                    title = "Alarm Sound",
                    icon = Icons.Default.VolumeUp
                ) {
                    SoundSettingsCard(
                        currentSettings = localPreferences.soundSettings,
                        onSettingsChange = { newSoundSettings ->
                            localPreferences = localPreferences.copy(soundSettings = newSoundSettings)
                            onPreferenceChange(localPreferences)
                        }
                    )
                }
            }
            
            // Vibration Settings
            item {
                SettingsSection(
                    title = "Vibration",
                    icon = Icons.Default.Vibration
                ) {
                    VibrationSettingsCard(
                        currentSettings = localPreferences.vibrationSettings,
                        onSettingsChange = { newVibrationSettings ->
                            localPreferences = localPreferences.copy(vibrationSettings = newVibrationSettings)
                            onPreferenceChange(localPreferences)
                        }
                    )
                }
            }
            
            // Math Puzzle Settings
            item {
                SettingsSection(
                    title = "Math Puzzle Difficulty",
                    icon = Icons.Default.Psychology
                ) {
                    PuzzleDifficultyCard(
                        currentDifficulty = localPreferences.defaultPuzzleDifficulty,
                        onDifficultyChange = { newDifficulty ->
                            localPreferences = localPreferences.copy(defaultPuzzleDifficulty = newDifficulty)
                            onPreferenceChange(localPreferences)
                        }
                    )
                }
            }
            
            // Snooze Settings
            item {
                SettingsSection(
                    title = "Snooze Options",
                    icon = Icons.Default.Snooze
                ) {
                    SnoozeSettingsCard(
                        currentSettings = localPreferences.snoozeSettings,
                        onSettingsChange = { newSnoozeSettings ->
                            localPreferences = localPreferences.copy(snoozeSettings = newSnoozeSettings)
                            onPreferenceChange(localPreferences)
                        }
                    )
                }
            }
            
            // App Behavior Settings
            item {
                SettingsSection(
                    title = "App Behavior",
                    icon = Icons.Default.Settings
                ) {
                    BehaviorSettingsCard(
                        currentSettings = localPreferences.behaviorSettings,
                        onSettingsChange = { newBehaviorSettings ->
                            localPreferences = localPreferences.copy(behaviorSettings = newBehaviorSettings)
                            onPreferenceChange(localPreferences)
                        }
                    )
                }
            }
            
            // Emergency Settings
            item {
                SettingsSection(
                    title = "Emergency Options",
                    icon = Icons.Default.Warning
                ) {
                    EmergencySettingsCard(
                        currentSettings = localPreferences.emergencySettings,
                        onSettingsChange = { newEmergencySettings ->
                            localPreferences = localPreferences.copy(emergencySettings = newEmergencySettings)
                            onPreferenceChange(localPreferences)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        content()
    }
}

@Composable
private fun SoundSettingsCard(
    currentSettings: SoundSettings,
    onSettingsChange: (SoundSettings) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Volume Slider
            Text(
                text = "Volume Level",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Slider(
                value = currentSettings.volume,
                onValueChange = { newVolume ->
                    onSettingsChange(currentSettings.copy(volume = newVolume))
                },
                valueRange = 0f..1f,
                steps = 9,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Alarm volume level: ${(currentSettings.volume * 100).toInt()}%"
                    }
            )
            
            Text(
                text = "${(currentSettings.volume * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Sound Selection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .selectable(
                        selected = expanded,
                        onClick = { expanded = !expanded },
                        role = Role.Button
                    )
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Alarm Sound: ${currentSettings.soundType.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse sound options" else "Expand sound options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup()
                        .padding(top = 8.dp)
                ) {
                    AlarmSoundType.values().forEach { soundType ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .selectable(
                                    selected = currentSettings.soundType == soundType,
                                    onClick = {
                                        onSettingsChange(currentSettings.copy(soundType = soundType))
                                        expanded = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentSettings.soundType == soundType,
                                onClick = null
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Text(
                                text = soundType.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Gradual Volume Increase
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = currentSettings.gradualVolumeIncrease,
                    onCheckedChange = { enabled ->
                        onSettingsChange(currentSettings.copy(gradualVolumeIncrease = enabled))
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "Gradual volume increase: ${if (currentSettings.gradualVolumeIncrease) "Enabled" else "Disabled"}"
                    }
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = "Gradual Volume Increase",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "Start quiet and gradually increase volume",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun VibrationSettingsCard(
    currentSettings: VibrationSettings,
    onSettingsChange: (VibrationSettings) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Enable Vibration
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = currentSettings.enabled,
                    onCheckedChange = { enabled ->
                        onSettingsChange(currentSettings.copy(enabled = enabled))
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "Vibration: ${if (currentSettings.enabled) "Enabled" else "Disabled"}"
                    }
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = "Enable Vibration",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (currentSettings.enabled) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Vibration Intensity
                Text(
                    text = "Intensity Level",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Slider(
                    value = currentSettings.intensity,
                    onValueChange = { newIntensity ->
                        onSettingsChange(currentSettings.copy(intensity = newIntensity))
                    },
                    valueRange = 0.1f..1f,
                    steps = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Vibration intensity: ${(currentSettings.intensity * 100).toInt()}%"
                        }
                )
                
                Text(
                    text = "${(currentSettings.intensity * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Vibration Pattern
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Vibration Pattern",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    VibrationPattern.values().forEach { pattern ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .selectable(
                                    selected = currentSettings.pattern == pattern,
                                    onClick = {
                                        onSettingsChange(currentSettings.copy(pattern = pattern))
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentSettings.pattern == pattern,
                                onClick = null
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Column {
                                Text(
                                    text = pattern.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                Text(
                                    text = pattern.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PuzzleDifficultyCard(
    currentDifficulty: MathPuzzle.Difficulty,
    onDifficultyChange: (MathPuzzle.Difficulty) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .selectableGroup()
        ) {
            Text(
                text = "Default difficulty for math puzzles required to stop alarms",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            MathPuzzle.Difficulty.values().forEach { difficulty ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .selectable(
                            selected = currentDifficulty == difficulty,
                            onClick = { onDifficultyChange(difficulty) },
                            role = Role.RadioButton
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentDifficulty == difficulty,
                        onClick = null
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Column {
                        Text(
                            text = difficulty.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Text(
                            text = difficulty.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SnoozeSettingsCard(
    currentSettings: SnoozeSettings,
    onSettingsChange: (SnoozeSettings) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Enable Snooze
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = currentSettings.enabled,
                    onCheckedChange = { enabled ->
                        onSettingsChange(currentSettings.copy(enabled = enabled))
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "Snooze: ${if (currentSettings.enabled) "Enabled" else "Disabled"}"
                    }
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = "Enable Snooze",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (currentSettings.enabled) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Snooze Duration
                Text(
                    text = "Snooze Duration: ${currentSettings.durationMinutes} minutes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Slider(
                    value = currentSettings.durationMinutes.toFloat(),
                    onValueChange = { newDuration ->
                        onSettingsChange(currentSettings.copy(durationMinutes = newDuration.toInt()))
                    },
                    valueRange = 1f..30f,
                    steps = 29,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Snooze duration: ${currentSettings.durationMinutes} minutes"
                        }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Max Snooze Count
                Text(
                    text = "Maximum Snoozes: ${if (currentSettings.maxCount == -1) "Unlimited" else currentSettings.maxCount.toString()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Slider(
                    value = if (currentSettings.maxCount == -1) 11f else currentSettings.maxCount.toFloat(),
                    onValueChange = { newCount ->
                        val count = if (newCount.toInt() >= 11) -1 else newCount.toInt()
                        onSettingsChange(currentSettings.copy(maxCount = count))
                    },
                    valueRange = 1f..11f,
                    steps = 10,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Maximum snoozes: ${if (currentSettings.maxCount == -1) "Unlimited" else currentSettings.maxCount.toString()}"
                        }
                )
            }
        }
    }
}

@Composable
private fun BehaviorSettingsCard(
    currentSettings: BehaviorSettings,
    onSettingsChange: (BehaviorSettings) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Keep Screen On
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = currentSettings.keepScreenOn,
                    onCheckedChange = { enabled ->
                        onSettingsChange(currentSettings.copy(keepScreenOn = enabled))
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "Keep screen on during alarm: ${if (currentSettings.keepScreenOn) "Enabled" else "Disabled"}"
                    }
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = "Keep Screen On",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "Prevent screen from turning off during active alarms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Full Screen Alarm
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = currentSettings.fullScreenAlarm,
                    onCheckedChange = { enabled ->
                        onSettingsChange(currentSettings.copy(fullScreenAlarm = enabled))
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "Full screen alarm: ${if (currentSettings.fullScreenAlarm) "Enabled" else "Disabled"}"
                    }
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = "Full Screen Alarm",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "Show alarm in full screen mode over other apps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Auto Dismiss
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = currentSettings.autoDismissEnabled,
                    onCheckedChange = { enabled ->
                        onSettingsChange(currentSettings.copy(autoDismissEnabled = enabled))
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "Auto dismiss after timeout: ${if (currentSettings.autoDismissEnabled) "Enabled" else "Disabled"}"
                    }
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = "Auto Dismiss",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "Automatically dismiss alarm after ${currentSettings.autoDismissMinutes} minutes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            
            if (currentSettings.autoDismissEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Auto Dismiss Timeout: ${currentSettings.autoDismissMinutes} minutes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Slider(
                    value = currentSettings.autoDismissMinutes.toFloat(),
                    onValueChange = { newMinutes ->
                        onSettingsChange(currentSettings.copy(autoDismissMinutes = newMinutes.toInt()))
                    },
                    valueRange = 1f..60f,
                    steps = 59,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Auto dismiss timeout: ${currentSettings.autoDismissMinutes} minutes"
                        }
                )
            }
        }
    }
}

@Composable
private fun EmergencySettingsCard(
    currentSettings: EmergencySettings,
    onSettingsChange: (EmergencySettings) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Emergency Override",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "These settings provide emergency ways to stop alarms when needed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Power Button Override
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = currentSettings.powerButtonOverride,
                    onCheckedChange = { enabled ->
                        onSettingsChange(currentSettings.copy(powerButtonOverride = enabled))
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "Power button override: ${if (currentSettings.powerButtonOverride) "Enabled" else "Disabled"}"
                    }
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = "Power Button Override",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    
                    Text(
                        text = "Press power button 5 times quickly to stop alarm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                    )
                }
            }
            
            if (currentSettings.powerButtonOverride) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "Presses required: ${currentSettings.powerButtonPresses}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Slider(
                    value = currentSettings.powerButtonPresses.toFloat(),
                    onValueChange = { newPresses ->
                        onSettingsChange(currentSettings.copy(powerButtonPresses = newPresses.toInt()))
                    },
                    valueRange = 3f..10f,
                    steps = 7,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Power button presses required: ${currentSettings.powerButtonPresses}"
                        }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Volume Button Override
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = currentSettings.volumeButtonOverride,
                    onCheckedChange = { enabled ->
                        onSettingsChange(currentSettings.copy(volumeButtonOverride = enabled))
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "Volume button override: ${if (currentSettings.volumeButtonOverride) "Enabled" else "Disabled"}"
                    }
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = "Volume Button Override",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    
                    Text(
                        text = "Hold both volume buttons for 3 seconds to stop alarm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// Data classes for settings
data class UserPreferences(
    val soundSettings: SoundSettings = SoundSettings(),
    val vibrationSettings: VibrationSettings = VibrationSettings(),
    val defaultPuzzleDifficulty: MathPuzzle.Difficulty = MathPuzzle.Difficulty.MEDIUM,
    val snoozeSettings: SnoozeSettings = SnoozeSettings(),
    val behaviorSettings: BehaviorSettings = BehaviorSettings(),
    val emergencySettings: EmergencySettings = EmergencySettings()
)

data class SoundSettings(
    val volume: Float = 0.8f,
    val soundType: AlarmSoundType = AlarmSoundType.DEFAULT,
    val gradualVolumeIncrease: Boolean = true
)

data class VibrationSettings(
    val enabled: Boolean = true,
    val intensity: Float = 0.7f,
    val pattern: VibrationPattern = VibrationPattern.STANDARD
)

data class SnoozeSettings(
    val enabled: Boolean = true,
    val durationMinutes: Int = 5,
    val maxCount: Int = 3
)

data class BehaviorSettings(
    val keepScreenOn: Boolean = true,
    val fullScreenAlarm: Boolean = true,
    val autoDismissEnabled: Boolean = false,
    val autoDismissMinutes: Int = 10
)

data class EmergencySettings(
    val powerButtonOverride: Boolean = true,
    val powerButtonPresses: Int = 5,
    val volumeButtonOverride: Boolean = true
)

enum class AlarmSoundType(val displayName: String) {
    DEFAULT("Default Alarm"),
    GENTLE("Gentle Wake"),
    INTENSE("Intense Beeping"),
    NATURE("Nature Sounds"),
    CLASSIC("Classic Bell"),
    CUSTOM("Custom Sound")
}

enum class VibrationPattern(val displayName: String, val description: String) {
    STANDARD("Standard", "Continuous vibration"),
    PULSE("Pulse", "Short pulses with pauses"),
    HEARTBEAT("Heartbeat", "Double pulse pattern"),
    ESCALATING("Escalating", "Gradually increasing intensity"),
    SOS("SOS", "SOS morse code pattern")
}
