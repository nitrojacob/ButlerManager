package com.example.butlermanager.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.butlermanager.MqttManager
import com.example.butlermanager.data.QrData
import com.example.butlermanager.data.QrDataDatabase
import com.example.butlermanager.data.TimeEntryDatabase
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NetworkModeHomeScreen(navController: NavController, mqttManager: MqttManager) {
    val context = LocalContext.current
    val qrDb = remember { QrDataDatabase.getDatabase(context) }
    val timeDb = remember { TimeEntryDatabase.getDatabase(context) }
    val coroutineScope = rememberCoroutineScope()
    
    var brokerHostname by remember { mutableStateOf("192.168.0.4") }
    var devices by remember { mutableStateOf<List<QrData>>(emptyList()) }
    var isConnecting by remember { mutableStateOf(false) }
    var deviceToDelete by remember { mutableStateOf<QrData?>(null) }

    fun loadDevices() {
        coroutineScope.launch {
            devices = qrDb.qrDataDao().getAllQrData()
        }
    }

    LaunchedEffect(Unit) {
        loadDevices()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = brokerHostname,
                    onValueChange = { brokerHostname = it },
                    label = { Text("Broker Hostname") },
                    modifier = Modifier.weight(1f)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(
                            color = if (mqttManager.isConnected) Color.Green else Color.Red,
                            shape = CircleShape
                        )
                )
            }
            
            Text(
                text = "Devices",
                modifier = Modifier.padding(vertical = 16.dp),
                style = MaterialTheme.typography.titleMedium
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(devices) { device ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                enabled = !isConnecting,
                                onClick = {
                                    coroutineScope.launch {
                                        isConnecting = true
                                        try {
                                            mqttManager.setBrokerUrl(brokerHostname)
                                            mqttManager.connect(device)
                                            mqttManager.readCronData()
                                            navController.navigate("timeEntryDevice/${device.name ?: ""}")
                                        } catch (e: Exception) {
                                            // Handle error
                                        } finally {
                                            isConnecting = false
                                        }
                                    }
                                },
                                onLongClick = {
                                    deviceToDelete = device
                                }
                            )
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

        if (isConnecting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(enabled = false) { },
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }

    deviceToDelete?.let { device ->
        AlertDialog(
            onDismissRequest = { deviceToDelete = null },
            title = { Text("Delete Device") },
            text = { Text("Are you sure you want to delete '${device.name}'? You will need the device QR code and physical access to connect to the device again") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val name = device.name ?: ""
                            qrDb.qrDataDao().deleteQrDataByName(name)
                            timeDb.timeEntryDao().deleteConfigurationAndSlots(name)
                            deviceToDelete = null
                            loadDevices()
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                Button(onClick = { deviceToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
