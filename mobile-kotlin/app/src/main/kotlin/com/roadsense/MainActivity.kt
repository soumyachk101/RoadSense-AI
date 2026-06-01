package com.roadsense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roadsense.sensor.SensorEngine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var sensorEngine: SensorEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Mocking trip setup for demo
        sensorEngine = SensorEngine(
            context = this,
            tripId = "test-trip-id",
            vehicleType = "four_wheeler",
            placement = "dashboard",
            onPocDetected = { println("PoC: $it") },
            onFlush = { println("Flushing batch: ${it.size}") }
        )

        setContent {
            RoadSenseTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(
                        onStartTrip = { sensorEngine.start() },
                        onEndTrip = { sensorEngine.stop() }
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(onStartTrip: () -> Unit, onEndTrip: () -> Unit) {
    var isRunning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "RoadSense AI Kotlin", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = {
                if (isRunning) onEndTrip() else onStartTrip()
                isRunning = !isRunning
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isRunning) "End Trip" else "Start Trip")
        }
        
        if (isRunning) {
            Text(text = "Sensors Active...", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun RoadSenseTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
