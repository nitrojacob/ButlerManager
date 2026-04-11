package com.example.butlermanager.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.butlermanager.DeviceManager
import com.example.butlermanager.R
import com.example.butlermanager.data.QrData
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val TAG = "ConnectProgressScreen"

enum class StepStatus {
    PENDING,
    IN_PROGRESS,
    SUCCESS,
    FAILURE
}

data class ProvisioningStep(
    val title: String,
    val status: MutableState<StepStatus>,
    val errorText: MutableState<String?> = mutableStateOf(null)
)

@Composable
fun rememberProvisioningSteps(): List<ProvisioningStep> {
    val statuses = remember {
        listOf(
            mutableStateOf(StepStatus.SUCCESS),
            mutableStateOf(StepStatus.PENDING),
            mutableStateOf(StepStatus.PENDING),
            mutableStateOf(StepStatus.PENDING),
            mutableStateOf(StepStatus.PENDING),
        )
    }

    return listOf(
        ProvisioningStep(stringResource(R.string.connect_progress_step1), statuses[0]),
        ProvisioningStep(stringResource(R.string.connect_progress_step2), statuses[1]),
        ProvisioningStep(stringResource(R.string.connect_progress_step3), statuses[2]),
        ProvisioningStep(stringResource(R.string.connect_progress_step4), statuses[3]),
        ProvisioningStep(stringResource(R.string.connect_progress_step5), statuses[4]),
    )
}

@Composable
fun ConnectProgressScreen(
    navController: NavController, qrDataJson: String, deviceManager: DeviceManager
) {
    BackHandler {
        deviceManager.disconnect()
        navController.popBackStack()
    }

    val isDone = qrDataJson == "done"
    val previousRoute = remember { navController.previousBackStackEntry?.destination?.route }
    val isFromProvisioningSource = previousRoute == "qrScanner" || previousRoute == "nearbyDevices"

    if (isDone || !isFromProvisioningSource) {
        LaunchedEffect(Unit) {
            delay(1000)
            navController.popBackStack()
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.success),
                modifier = Modifier.size(120.dp),
                tint = Color(0xFF00C853)
            )
        }
        return
    }

    val qrData = remember(qrDataJson) {
        try {
            Gson().fromJson(qrDataJson, QrData::class.java)
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse QR data", e)
            null
        }
    }

    if (qrData == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.error_invalid_qr_code_data))
        }
        return
    }

    Log.d(TAG, "Attempting to connect to device: ${qrData.name}")
    val context = LocalContext.current
    var overallStatus by remember { mutableStateOf(context.getString(R.string.connecting)) }

    val steps = rememberProvisioningSteps()
    var showWifiDialog by remember { mutableStateOf(false) }

    fun updateStep(title: String, newStatus: StepStatus, error: String? = null) {
        steps.find { it.title == title }?.apply {
            status.value = newStatus
            errorText.value = error
        }
    }

    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    var isWifiEnabled by remember { mutableStateOf(wifiManager.isWifiEnabled) }

    val wifiStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (WifiManager.WIFI_STATE_CHANGED_ACTION == intent.action) {
                val wifiState =
                    intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                isWifiEnabled = wifiState == WifiManager.WIFI_STATE_ENABLED
                Log.d(TAG, "Wifi state changed. Is enabled: $isWifiEnabled")
            }
        }
    }

    DisposableEffect(Unit) {
        val intentFilter = IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION)
        context.registerReceiver(wifiStateReceiver, intentFilter)
        onDispose {
            context.unregisterReceiver(wifiStateReceiver)
        }
    }


    val permissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

    var hasPermissions by remember {
        mutableStateOf(
            permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissionsMap ->
            hasPermissions = permissionsMap.values.all { it }
            if (!hasPermissions) {
                Log.w(TAG, "Permissions not granted")
                overallStatus = context.getString(R.string.permission_denied_messsage)
            } else {
                Log.d(TAG, "Permissions granted")
            }
        }
    )

    LaunchedEffect(key1 = true) {
        if (!hasPermissions) {
            launcher.launch(permissions)
        }
    }

    LaunchedEffect(hasPermissions, isWifiEnabled) {
        if (hasPermissions) {
            updateStep(context.getString(R.string.connect_progress_step2), if (isWifiEnabled) StepStatus.SUCCESS else StepStatus.IN_PROGRESS)

            if (!isWifiEnabled) {
                overallStatus = context.getString(R.string.wifi_disabled_message)
                showWifiDialog = true
                return@LaunchedEffect
            }
            showWifiDialog = false //Dismiss dialog if wifi gets enabled

            try {
                updateStep(context.getString(R.string.connect_progress_step3), StepStatus.IN_PROGRESS)
                withContext(Dispatchers.IO) {
                    deviceManager.connect(qrData)
                }
                updateStep(context.getString(R.string.connect_progress_step3), StepStatus.SUCCESS)

                updateStep(context.getString(R.string.connect_progress_step4), StepStatus.IN_PROGRESS)
                withContext(Dispatchers.IO) {
                    deviceManager.writeTimeData()
                }
                updateStep(context.getString(R.string.connect_progress_step4), StepStatus.SUCCESS)

                updateStep(context.getString(R.string.connect_progress_step5), StepStatus.IN_PROGRESS)
                withContext(Dispatchers.IO) {
                    deviceManager.readCronData()
                }
                updateStep(context.getString(R.string.connect_progress_step5), StepStatus.SUCCESS)

                overallStatus = context.getString(R.string.connected_successfully)
                navController.navigate("timeEntryDevice/${qrData.name ?: ""}") {
                    navController.popBackStack()
                }
            } catch (e: Throwable) {
                val errorMessage = when (e) {
                    is IllegalArgumentException -> context.getString(R.string.invalid_qr_code_data)
                    is NotImplementedError -> context.getString(R.string.ble_transport_not_supported)
                    else -> e.message ?: context.getString(R.string.an_unknown_error_occurred)
                }
                Log.e(TAG, "Failed to connect to device: $errorMessage", e)

                // Determine which step failed
                if (steps.find { it.title == context.getString(R.string.connect_progress_step3) }?.status?.value != StepStatus.SUCCESS) {
                    updateStep(context.getString(R.string.connect_progress_step3), StepStatus.FAILURE, errorMessage)
                } else if (steps.find { it.title == context.getString(R.string.connect_progress_step4) }?.status?.value != StepStatus.SUCCESS) {
                    updateStep(context.getString(R.string.connect_progress_step4), StepStatus.FAILURE, errorMessage)
                } else {
                    updateStep(context.getString(R.string.connect_progress_step5), StepStatus.FAILURE, errorMessage)
                }

                overallStatus = context.getString(R.string.failed_to_connect_to_device)
            }
        }
    }

    if (showWifiDialog) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismissing the dialog by clicking outside */ },
            title = { Text(stringResource(R.string.wifi_required_title)) },
            text = { Text(stringResource(R.string.wifi_required_message)) },
            confirmButton = {
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }) {
                    Text(stringResource(R.string.open_wifi_settings))
                }
            },
            dismissButton = {
                Button(onClick = {
                    deviceManager.disconnect()
                    navController.popBackStack()
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = overallStatus, modifier = Modifier.padding(bottom = 16.dp))

        Column {
            steps.forEach { step ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    val status = step.status.value
                    val icon = when (status) {
                        StepStatus.PENDING -> Icons.Filled.AccessTime
                        StepStatus.IN_PROGRESS -> null
                        StepStatus.SUCCESS -> Icons.Filled.CheckCircle
                        StepStatus.FAILURE -> Icons.Filled.Warning
                    }

                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = when (status) {
                                StepStatus.SUCCESS -> Color(0xFF00C853)
                                StepStatus.FAILURE -> Color.Red
                                else -> Color.Gray
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = step.title)
                        step.errorText.value?.let {
                            Text(text = it, color = Color.Red, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        }

        if (overallStatus == context.getString(R.string.failed_to_connect_to_device)) {
            Button(
                onClick = {
                    navController.popBackStack()
                },
                modifier = Modifier.padding(top = 32.dp)
            ) {
                Text("Back")
            }
        }
    }
}
