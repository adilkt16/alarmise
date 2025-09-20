package com.alarmise.app.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.alarmise.app.ui.theme.AlarmiseTheme
import com.alarmise.app.ui.viewmodel.AlarmTriggerViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AlarmTriggerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AlarmiseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Red
                ) {
                    AlarmTriggerScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmTriggerScreen(
    viewModel: AlarmTriggerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var answerText by remember { mutableStateOf("") }
    
    if (uiState.isAlarmStopped) {
        // Show success message and close
        LaunchedEffect(Unit) {
            // Close activity after showing success
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🚨 ALARM ACTIVE 🚨",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Solve this math puzzle to stop the alarm:",
            fontSize = 18.sp,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        uiState.currentPuzzle?.let { puzzle ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = puzzle.question,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    OutlinedTextField(
                        value = answerText,
                        onValueChange = { answerText = it },
                        label = { Text("Your Answer") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    if (uiState.isAnswerIncorrect) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Incorrect! Try again.",
                            color = Color.Red,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            viewModel.submitAnswer(answerText)
                            answerText = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Submit Answer")
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Attempts: ${uiState.attempts}",
            color = Color.White,
            fontSize = 16.sp
        )
    }
}
