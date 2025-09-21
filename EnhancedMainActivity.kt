package com.alarmise

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alarmise.ui.components.AlarmCard
// import com.alarmise.ui.components.*
import com.alarmise.ui.settings.SettingsScreen
import com.alarmise.ui.settings.UserPreferences
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedMainActivity(
    alarmViewModel: AlarmViewModel = viewModel(),
    onNavigateToFullScreen: (Alarm) -> Unit = {}
) {
    val uiState by alarmViewModel.uiState.collectAsState()
    val activeAlarm by alarmViewModel.activeAlarm.collectAsState()
    val upcomingAlarms by alarmViewModel.upcomingAlarms.collectAsState()
    val alarmHistory by alarmViewModel.alarmHistory.collectAsState()
    val userPreferences by alarmViewModel.userPreferences.collectAsState()
    
    var currentScreen by remember { mutableStateOf(MainScreen.ALARMS) }
    var showAddAlarmDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<Alarm?>(null) }
    
    val listState = rememberLazyListState()
    
    // Auto-scroll to active alarm when it becomes active
    LaunchedEffect(activeAlarm) {
        if (activeAlarm?.status == AlarmStatus.ACTIVE) {
            listState.animateScrollToItem(0)
        }
    }
    
    Scaffold(
        topBar = {
            EnhancedTopAppBar(
                currentScreen = currentScreen,
                onScreenChange = { currentScreen = it },
                hasActiveAlarm = activeAlarm != null,
                onAddAlarm = { showAddAlarmDialog = true }
            )
        },
        floatingActionButton = {
            if (currentScreen == MainScreen.ALARMS) {
                ExtendedFloatingActionButton(
                    onClick = { showAddAlarmDialog = true },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null
                        )
                    },
                    text = {
                        Text("New Alarm")
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "Create new alarm"
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentScreen) {
                MainScreen.ALARMS -> {
                    AlarmManagementScreen(
                        uiState = uiState,
                        activeAlarm = activeAlarm,
                        upcomingAlarms = upcomingAlarms,
                        alarmHistory = alarmHistory,
                        listState = listState,
                        onAlarmAction = { alarm, action ->
                            when (action) {
                                AlarmAction.EDIT -> showEditDialog = alarm
                                AlarmAction.DELETE -> alarmViewModel.deleteAlarm(alarm)
                                AlarmAction.TOGGLE -> alarmViewModel.toggleAlarm(alarm)
                                AlarmAction.STOP -> alarmViewModel.stopAlarm(alarm)
                                AlarmAction.SNOOZE -> alarmViewModel.snoozeAlarm(alarm)
                                AlarmAction.SHOW_FULL_SCREEN -> onNavigateToFullScreen(alarm)
                            }
                        },
                        onEmergencyStop = { alarmViewModel.emergencyStopAllAlarms() }
                    )
                }
                
                MainScreen.SETTINGS -> {
                    SettingsScreen(
                        onNavigateBack = { currentScreen = MainScreen.ALARMS },
                        userPreferences = userPreferences,
                        onPreferenceChange = { newPreferences ->
                            alarmViewModel.updateUserPreferences(newPreferences)
                        }
                    )
                }
                
                MainScreen.STATISTICS -> {
                    StatisticsScreen(
                        alarmHistory = alarmHistory,
                        onNavigateBack = { currentScreen = MainScreen.ALARMS }
                    )
                }
            }
            
            // Active alarm overlay
            activeAlarm?.let { alarm ->
                if (alarm.status == AlarmStatus.ACTIVE && !userPreferences.behaviorSettings.fullScreenAlarm) {
                    ActiveAlarmOverlay(
                        alarm = alarm,
                        onAction = { action ->
                            when (action) {
                                AlarmAction.STOP -> alarmViewModel.stopAlarm(alarm)
                                AlarmAction.SNOOZE -> alarmViewModel.snoozeAlarm(alarm)
                                AlarmAction.SHOW_FULL_SCREEN -> onNavigateToFullScreen(alarm)
                                else -> {}
                            }
                        }
                    )
                }
            }
        }
    }
    
    // Dialogs
    if (showAddAlarmDialog) {
        AlarmConfigurationDialog(
            onDismiss = { showAddAlarmDialog = false },
            onSave = { alarmConfig ->
                alarmViewModel.createAlarm(alarmConfig)
                showAddAlarmDialog = false
            },
            userPreferences = userPreferences
        )
    }
    
    showEditDialog?.let { alarm ->
        AlarmConfigurationDialog(
            initialAlarm = alarm,
            onDismiss = { showEditDialog = null },
            onSave = { alarmConfig ->
                alarmViewModel.updateAlarm(alarm.copy(
                    startTime = alarmConfig.startTime,
                    durationMinutes = alarmConfig.durationMinutes,
                    puzzleDifficulty = alarmConfig.puzzleDifficulty,
                    label = alarmConfig.label,
                    isEnabled = alarmConfig.isEnabled
                ))
                showEditDialog = null
            },
            userPreferences = userPreferences
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnhancedTopAppBar(
    currentScreen: MainScreen,
    onScreenChange: (MainScreen) -> Unit,
    hasActiveAlarm: Boolean,
    onAddAlarm: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (currentScreen) {
                        MainScreen.ALARMS -> "Alarmise"
                        MainScreen.SETTINGS -> "Settings"
                        MainScreen.STATISTICS -> "Statistics"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                if (hasActiveAlarm && currentScreen == MainScreen.ALARMS) {
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    val infiniteTransition = rememberInfiniteTransition(label = "alarm_indicator")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alpha_animation"
                    )
                    
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = "Active alarm indicator",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(20.dp)
                            .alpha(alpha)
                    )
                }
            }
        },
        actions = {
            when (currentScreen) {
                MainScreen.ALARMS -> {
                    IconButton(
                        onClick = { onScreenChange(MainScreen.STATISTICS) },
                        modifier = Modifier.semantics {
                            contentDescription = "View alarm statistics"
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = null
                        )
                    }
                    
                    IconButton(
                        onClick = { onScreenChange(MainScreen.SETTINGS) },
                        modifier = Modifier.semantics {
                            contentDescription = "Open settings"
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null
                        )
                    }
                }
                
                MainScreen.SETTINGS, MainScreen.STATISTICS -> {
                    IconButton(
                        onClick = { onScreenChange(MainScreen.ALARMS) },
                        modifier = Modifier.semantics {
                            contentDescription = "Back to alarms"
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
private fun AlarmManagementScreen(
    uiState: AlarmUiState,
    activeAlarm: Alarm?,
    upcomingAlarms: List<Alarm>,
    alarmHistory: List<AlarmLog>,
    listState: LazyListState,
    onAlarmAction: (Alarm, AlarmAction) -> Unit,
    onEmergencyStop: () -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Active alarm status (if any)
        activeAlarm?.let { alarm ->
            item(key = "active_alarm") {
                // EnhancedAlarmStatusCard - temporarily disabled due to compilation issues
                Text(
                    text = "Active Alarm: ${alarm.label}",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        
        // Emergency stop information (when alarm is active)
        if (activeAlarm?.status == AlarmStatus.ACTIVE) {
            item(key = "emergency_info") {
                // EmergencyStopInfoCard - temporarily disabled
                Card(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Emergency Stop Available",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
        
        // Status messages
        if (uiState.statusMessage.isNotEmpty()) {
            item(key = "status_messages") {
                // StatusMessagesCard - temporarily disabled
                Card(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = uiState.statusMessage,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
        
        // Next alarm schedule
        if (upcomingAlarms.isNotEmpty()) {
            item(key = "next_alarms") {
                // NextAlarmScheduleCard - temporarily disabled
                Card(modifier = Modifier.padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Upcoming Alarms",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        upcomingAlarms.take(3).forEach { alarm ->
                            Text(text = alarm.label)
                        }
                    }
                }
            }
        }
        
        // Recent alarm history
        if (alarmHistory.isNotEmpty()) {
            item(key = "alarm_history") {
                // AlarmHistoryView - temporarily disabled
                Card(modifier = Modifier.padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Recent History",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(text = "${alarmHistory.size} alarm entries")
                    }
                }
            }
        }
        
        // Empty state when no alarms
        if (upcomingAlarms.isEmpty() && activeAlarm == null) {
            item(key = "empty_state") {
                EmptyAlarmsState(
                    modifier = Modifier.animateItemPlacement()
                )
            }
        }
    }
}

@Composable
private fun ActiveAlarmOverlay(
    alarm: Alarm,
    onAction: (AlarmAction) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "ALARM ACTIVE",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold
            )
            
            if (alarm.label.isNotEmpty()) {
                Text(
                    text = alarm.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onAction(AlarmAction.SNOOZE) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Snooze")
                }
                
                Button(
                    onClick = { onAction(AlarmAction.SHOW_FULL_SCREEN) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Full Screen")
                }
                
                Button(
                    onClick = { onAction(AlarmAction.STOP) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
private fun EmptyAlarmsState(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.AlarmAdd,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "No Alarms Set",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Tap the + button to create your first persistent alarm",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StatisticsScreen(
    alarmHistory: List<AlarmLog>,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.semantics {
                    contentDescription = "Navigate back to alarms"
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = null
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = "Alarm Statistics",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Quick stats
            item {
                StatisticsOverviewCard(alarmHistory)
            }
            
            // Detailed history
            item {
                AlarmHistoryView(
                    alarmHistory = alarmHistory,
                    showTitle = false
                )
            }
        }
    }
}

@Composable
private fun StatisticsOverviewCard(
    alarmHistory: List<AlarmLog>
) {
    val totalAlarms = alarmHistory.size
    val dismissedAlarms = alarmHistory.count { it.outcome == AlarmOutcome.DISMISSED }
    val snoozedAlarms = alarmHistory.count { it.outcome == AlarmOutcome.SNOOZED }
    val expiredAlarms = alarmHistory.count { it.outcome == AlarmOutcome.EXPIRED }
    val cancelledAlarms = alarmHistory.count { it.outcome == AlarmOutcome.CANCELLED }
    
    val averageSolveTime = if (dismissedAlarms > 0) {
        alarmHistory
            .filter { it.outcome == AlarmOutcome.DISMISSED && it.solveTimeSeconds > 0 }
            .map { it.solveTimeSeconds }
            .average()
    } else 0.0
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Overview",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatisticItem(
                    label = "Total",
                    value = totalAlarms.toString(),
                    icon = Icons.Default.Alarm
                )
                
                StatisticItem(
                    label = "Solved",
                    value = dismissedAlarms.toString(),
                    icon = Icons.Default.CheckCircle
                )
                
                StatisticItem(
                    label = "Snoozed",
                    value = snoozedAlarms.toString(),
                    icon = Icons.Default.Snooze
                )
                
                StatisticItem(
                    label = "Expired",
                    value = expiredAlarms.toString(),
                    icon = Icons.Default.TimerOff
                )
            }
            
            if (averageSolveTime > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Average solve time: ${averageSolveTime.toInt()}s",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun StatisticItem(
    label: String,
    value: String,
    icon: ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
    }
}

// Enums and data classes
enum class MainScreen {
    ALARMS,
    SETTINGS,
    STATISTICS
}

enum class AlarmAction {
    EDIT,
    DELETE,
    TOGGLE,
    STOP,
    SNOOZE,
    SHOW_FULL_SCREEN
}
