package com.example.butlermanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.butlermanager.MqttManager
import com.example.butlermanager.data.QrData
import com.example.butlermanager.data.QrDataDatabase
import kotlinx.coroutines.launch

@Composable
fun NetworkModeHomeScreen(navController: NavController, mqttManager: MqttManager) {
    val context = LocalContext.current
    val db = remember { QrDataDatabase.getDatabase(context) }
    val coroutineScope = rememberCoroutineScope()
    
    var brokerHostname by remember { mutableStateOf("192.168.0.4") }
    var devices by remember { mutableStateOf<List<QrData>>(emptyList()) }
    var isConnecting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            devices = db.qrDataDao().getAllQrData()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = brokerHostname,
            onValueChange = { brokerHostname = it },
            label = { Text("Broker Hostname") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Text(
            text = "Devices",
            modifier = Modifier.padding(vertical = 16.dp)
        )

        if (isConnecting) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(devices) { device ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isConnecting) {
                            coroutineScope.launch {
                                isConnecting = true
                                try {
                                    mqttManager.setBrokerUrl(brokerHostname)
                                    mqttManager.connect(device)
                                    mqttManager.readCronData()
                                    navController.navigate("timeEntryDevice/${device.name ?: ""}")
                                } catch (e: Exception) {
                                    // Handle error (e.g., show toast)
                                } finally {
                                    isConnecting = false
                                }
                            }
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                    ) {
                        Text(text = device.name ?: "Unknown Device")
                    }
                }
            }
        }
    }
}
