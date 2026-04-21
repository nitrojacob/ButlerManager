package com.example.butlermanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.butlermanager.DeviceManager
import com.example.butlermanager.data.TimeEntryConfiguration
import com.example.butlermanager.data.TimeEntryDatabase
import com.example.butlermanager.data.TimeSlot
import kotlinx.coroutines.launch

@Composable
fun TimeEntryScreenOfDevice(navController: NavController, name: String, deviceManager: DeviceManager) {
    val context = LocalContext.current
    val db = TimeEntryDatabase.getDatabase(context)
    val scope = rememberCoroutineScope()
    var advancedSettingsChanged by remember { mutableStateOf(false) }
    var isProvisioning by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var allConfigs by remember { mutableStateOf<List<TimeEntryConfiguration>>(emptyList()) }
    var showMenu by remember { mutableStateOf(false) }

    BackHandler {
        navController.popBackStack()
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Configuration") },
            text = {
                if (allConfigs.isEmpty()) {
                    Text("No other configurations to import.")
                } else {
                    LazyColumn {
                        items(allConfigs) { config ->
                            Text(
                                text = config.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp)
                                    .clickable {
                                        scope.launch {
                                            val configWithTimeSlots =
                                                db.timeEntryDao().getConfigurationWithTimeSlots(config.name)
                                            if (configWithTimeSlots != null) {
                                                val importedTimeSlots = configWithTimeSlots.timeSlots
                                                deviceManager.timeSlots = List(15) { index ->
                                                    importedTimeSlots.find { it.rowIndex == index }?.copy(
                                                        id = 0,
                                                        configurationName = name
                                                    ) ?: TimeSlot(
                                                        configurationName = name,
                                                        rowIndex = index,
                                                        onOff = 0,
                                                        hour = 24,
                                                        minute = 0,
                                                        channel = 0
                                                    )
                                                }
                                            }
                                            showImportDialog = false
                                        }
                                    }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showImportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(navBackStackEntry) {
        if (navBackStackEntry?.savedStateHandle?.remove<Boolean>("advanced_settings_saved") == true) {
            advancedSettingsChanged = true
        }
    }

    DisposableEffect(navController) {
        onDispose {
            val destinationRoute = navController.currentBackStackEntry?.destination?.route
            if (destinationRoute?.startsWith("advanced_config/") != true && destinationRoute != "log_viewer" && destinationRoute != "version_screen") {
                scope.launch {
                    deviceManager.disconnect()
                }
            }
        }
    }

    LaunchedEffect(key1 = name) {
        if (deviceManager.timeSlots.isEmpty() || deviceManager.timeSlots.first().configurationName != name) {
            scope.launch {
                val configWithTimeSlots = db.timeEntryDao().getConfigurationWithTimeSlots(name)
                val loadedTimeSlots = if (configWithTimeSlots != null) {
                    val existingTimeSlots = configWithTimeSlots.timeSlots
                    List(15) { index ->
                        existingTimeSlots.find { it.rowIndex == index } ?: TimeSlot(
                            configurationName = name,
                            rowIndex = index,
                            onOff = 0,
                            hour = 24,
                            minute = 0,
                            channel = 0
                        )
                    }
                } else {
                    List(15) {
                        TimeSlot(
                            configurationName = name,
                            rowIndex = it,
                            onOff = 0,
                            hour = 24,
                            minute = 0,
                            channel = 0
                        )
                    }
                }
                deviceManager.timeSlots = loadedTimeSlots
                deviceManager.initialTimeSlots = loadedTimeSlots
            }
        }
    }

    val isFormDirty = (deviceManager.timeSlots != deviceManager.initialTimeSlots) || advancedSettingsChanged

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = name, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box {
                IconButton(onClick = { showMenu = true }, enabled = !isProvisioning) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Advanced") },
                        onClick = {
                            showMenu = false
                            navController.navigate("advanced_config/$name")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Logs") },
                        onClick = {
                            showMenu = false
                            navController.navigate("log_viewer")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Version") },
                        onClick = {
                            showMenu = false
                            navController.navigate("version_screen")
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        TimeEntryList(
            timeSlots = deviceManager.timeSlots,
            onTimeSlotsChanged = { deviceManager.timeSlots = it },
            modifier = Modifier.weight(1f)
        )

        if (isProvisioning) {
            CircularProgressIndicator()
        }
        Spacer(modifier = Modifier.height(16.dp))

        ConnectProgressButtons(
            isProvisioning = isProvisioning,
            isFormDirty = isFormDirty,
            onUpdate = {
                scope.launch {
                    isProvisioning = true
                    db.timeEntryDao().updateTimeSlotsForConfiguration(name, deviceManager.timeSlots)
                    deviceManager.writeCronData()
                    if (advancedSettingsChanged) {
                        deviceManager.writeAdvancedConfigs()
                    }
                    deviceManager.provision()
                    deviceManager.initialTimeSlots = deviceManager.timeSlots
                    advancedSettingsChanged = false
                    isProvisioning = false
                    navController.popBackStack()
                }
            },
            onImport = {
                scope.launch {
                    allConfigs = db.timeEntryDao().getAllConfigurations().filter { it.name != name }
                    showImportDialog = true
                }
            }
        )
    }
}

@Composable
fun TimeEntryScreenOfConfig(navController: NavController, name: String) {
    val context = LocalContext.current
    val db = TimeEntryDatabase.getDatabase(context)
    val scope = rememberCoroutineScope()
    var timeSlots by remember { mutableStateOf(emptyList<TimeSlot>()) }
    var initialTimeSlots by remember { mutableStateOf(emptyList<TimeSlot>()) }
    var advancedSettingsChanged by remember { mutableStateOf(false) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(navBackStackEntry) {
        if (navBackStackEntry?.savedStateHandle?.remove<Boolean>("advanced_settings_saved") == true) {
            advancedSettingsChanged = true
        }
    }

    LaunchedEffect(key1 = name) {
        scope.launch {
            val configWithTimeSlots = db.timeEntryDao().getConfigurationWithTimeSlots(name)
            val loadedTimeSlots = if (configWithTimeSlots != null) {
                val existingTimeSlots = configWithTimeSlots.timeSlots
                List(15) { index ->
                    existingTimeSlots.find { it.rowIndex == index } ?: TimeSlot(
                        configurationName = name,
                        rowIndex = index,
                        onOff = 0,
                        hour = 24,
                        minute = 0,
                        channel = 0
                    )
                }
            } else {
                List(15) { TimeSlot(configurationName = name, rowIndex = it, onOff = 0, hour = 24, minute = 0, channel = 0) }
            }
            timeSlots = loadedTimeSlots
            initialTimeSlots = loadedTimeSlots
        }
    }

    val isFormDirty = (timeSlots != initialTimeSlots) || advancedSettingsChanged

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = name, fontSize = 24.sp)
        Spacer(modifier = Modifier.height(16.dp))

        TimeEntryList(
            timeSlots = timeSlots,
            onTimeSlotsChanged = { timeSlots = it },
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        SavedConfigsButtons(
            isFormDirty = isFormDirty,
            isProvisioning = false,
            onSave = {
                scope.launch {
                    db.timeEntryDao().updateTimeSlotsForConfiguration(name, timeSlots.map { it.copy(configurationName = name) })
                    initialTimeSlots = timeSlots
                    advancedSettingsChanged = false
                }
            }
        )
    }
}


@Composable
private fun TimeEntryList(
    timeSlots: List<TimeSlot>,
    onTimeSlotsChanged: (List<TimeSlot>) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(timeSlots) { index, timeSlot ->
                if (timeSlot.hour < 24) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = timeSlot.hour.toString(),
                            onValueChange = { newHour ->
                                val updatedTimeSlots = timeSlots.toMutableList()
                                val hour = newHour.filter { it.isDigit() }.take(2).toIntOrNull() ?: 0
                                if (hour in 0..23) {
                                    updatedTimeSlots[index] = timeSlot.copy(hour = hour)
                                    onTimeSlotsChanged(updatedTimeSlots)
                                }
                            },
                            label = { Text("Hr") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = timeSlot.minute.toString(),
                            onValueChange = { newMinute ->
                                val updatedTimeSlots = timeSlots.toMutableList()
                                val minute = newMinute.filter { it.isDigit() }.take(2).toIntOrNull() ?: 0
                                if (minute in 0..59) {
                                    updatedTimeSlots[index] = timeSlot.copy(minute = minute)
                                    onTimeSlotsChanged(updatedTimeSlots)
                                }
                            },
                            label = { Text("Min") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = timeSlot.onOff.toString(),
                            onValueChange = { newOnOff ->
                                val updatedTimeSlots = timeSlots.toMutableList()
                                updatedTimeSlots[index] = timeSlot.copy(onOff = newOnOff.filter { it.isDigit() }.take(2).toIntOrNull() ?: 0)
                                onTimeSlotsChanged(updatedTimeSlots)
                            },
                            label = { Text("On/Off") },
                            modifier = Modifier.width(80.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = timeSlot.channel.toString(),
                            onValueChange = { newChannel ->
                                val updatedTimeSlots = timeSlots.toMutableList()
                                updatedTimeSlots[index] = timeSlot.copy(channel = newChannel.filter { it.isDigit() }.take(1).toIntOrNull() ?: 0)
                                onTimeSlotsChanged(updatedTimeSlots)
                            },
                            label = { Text("Ch") },
                            modifier = Modifier.width(80.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = {
                            val updatedTimeSlots = timeSlots.toMutableList()
                            updatedTimeSlots[index] = timeSlot.copy(hour = 24, minute = 0, onOff = 0, channel = 0)
                            onTimeSlotsChanged(updatedTimeSlots)
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
        FloatingActionButton(
            onClick = {
                val firstEmptyIndex = timeSlots.indexOfFirst { it.hour >= 24 }
                if (firstEmptyIndex != -1) {
                    val updatedTimeSlots = timeSlots.toMutableList()
                    updatedTimeSlots[firstEmptyIndex] = timeSlots[firstEmptyIndex].copy(hour = 0, minute = 0, onOff = 0, channel = 0)
                    onTimeSlotsChanged(updatedTimeSlots)
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add")
        }
    }
}

@Composable
private fun ConnectProgressButtons(
    isProvisioning: Boolean,
    isFormDirty: Boolean,
    onUpdate: () -> Unit,
    onImport: () -> Unit
) {
    Row {
        Button(
            onClick = onImport,
            enabled = !isProvisioning
        ) {
            Text("Import")
        }
        Spacer(modifier = Modifier.width(4.dp))
        Button(
            onClick = onUpdate,
            enabled = isFormDirty && !isProvisioning
        ) {
            Text("Update")
        }
    }
}

@Composable
private fun SavedConfigsButtons(
    isFormDirty: Boolean,
    isProvisioning: Boolean,
    onSave: () -> Unit
) {
    Row {
        Button(
            onClick = onSave,
            enabled = isFormDirty && !isProvisioning
        ) {
            Text("Save")
        }
    }
}
