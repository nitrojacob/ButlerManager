package com.example.butlermanager.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.butlermanager.data.QrDataDatabase
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun NearbyDevicesScreen(navController: NavController) {
    val context = LocalContext.current
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    val db = remember { QrDataDatabase.getDatabase(context) }
    val coroutineScope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var scanResults by remember { mutableStateOf<List<ScanResult>>(emptyList()) }
    var isLocationServicesEnabled by remember { mutableStateOf(true) }
    var showEnableLocationDialog by remember { mutableStateOf(false) }
    var showPermissionDeniedDialog by remember { mutableStateOf(false) }
    var showDeviceNotKnownDialog by remember { mutableStateOf(false) }

    val wifiScanReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission") // Permission is checked before scan is triggered.
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if (success) {
                try {
                    @Suppress("DEPRECATION")
                    scanResults = wifiManager.scanResults
                } catch (e: SecurityException) {
                    isLocationServicesEnabled = false
                }
            }
        }
    }

    val locationSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        isLocationServicesEnabled = context.isLocationEnabled
    }

    val appSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(isLocationServicesEnabled) {
        if (!isLocationServicesEnabled) {
            showEnableLocationDialog = true
        }
    }

    if (showEnableLocationDialog) {
        AlertDialog(
            onDismissRequest = { showEnableLocationDialog = false },
            title = { Text("Enable Location Services") },
            text = { Text("To scan for nearby Wi-Fi devices, please enable location services.") },
            confirmButton = {
                Button(
                    onClick = {
                        showEnableLocationDialog = false
                        locationSettingsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                ) {
                    Text("Settings")
                }
            },
            dismissButton = {
                Button(onClick = { showEnableLocationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDeniedDialog = false },
            title = { Text("Location Permission Required") },
            text = { Text("To scan for nearby Wi-Fi devices, please grant location permission in the app settings.") },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDeniedDialog = false
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        val uri = Uri.fromParts("package", context.packageName, null)
                        intent.data = uri
                        appSettingsLauncher.launch(intent)
                    }
                ) {
                    Text("Settings")
                }
            },
            dismissButton = {
                Button(onClick = { showPermissionDeniedDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeviceNotKnownDialog) {
        AlertDialog(
            onDismissRequest = { showDeviceNotKnownDialog = false },
            title = { Text("Device Not Known") },
            text = { Text("Device not known. Scan QR code to add.") },
            confirmButton = {
                Button(onClick = { showDeviceNotKnownDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    DisposableEffect(Unit) {
        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(wifiScanReceiver, intentFilter)

        onDispose {
            context.unregisterReceiver(wifiScanReceiver)
        }
    }

    val startScan = {
        val isLocationOn = context.isLocationEnabled

        if (isLocationOn) {
            isLocationServicesEnabled = true
            @Suppress("DEPRECATION")
            wifiManager.startScan()
        } else {
            isLocationServicesEnabled = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasPermission = granted
            if (!granted) {
                showPermissionDeniedDialog = true
            }
        }
    )

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(hasPermission, isLocationServicesEnabled) {
        if (hasPermission && isLocationServicesEnabled) {
            while (true) {
                startScan()
                delay(5000) // Scan every 5 seconds
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            when {
                !hasPermission -> {
                    Text("Location permission is required to show nearby devices.")
                }
                !isLocationServicesEnabled -> {
                    Text("Location services are disabled. Please enable them to scan for nearby devices.")
                }
                else -> {
                    val butlerScanResults = scanResults.filter { getSsid(it).startsWith("BUTLER_") }
                    if (butlerScanResults.isEmpty()) {
                        Text("Scanning for nearby devices...")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(butlerScanResults) { result ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            coroutineScope.launch {
                                                val ssid = getSsid(result)
                                                val qrData = db
                                                    .qrDataDao()
                                                    .getQrDataByName(ssid)
                                                if (qrData != null) {
                                                    val qrDataJson = Gson().toJson(qrData)
                                                    val encodedQrData = URLEncoder.encode(
                                                        qrDataJson,
                                                        StandardCharsets.UTF_8.toString()
                                                    )
                                                    navController.navigate("connectProgress/$encodedQrData")
                                                } else {
                                                    showDeviceNotKnownDialog = true
                                                }
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(text = getSsid(result))
                                            Text(text = "${result.level} dBm")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.padding(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { navController.navigate("qrScanner") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Scan QR code")
            }

            Button(
                onClick = { navController.navigate("savedConfigs") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Saved Configs")
            }
        }
    }
}

private val Context.isLocationEnabled: Boolean
    get() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            Settings.Secure.getInt(contentResolver, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF) != Settings.Secure.LOCATION_MODE_OFF
        }
    }

private fun getSsid(scanResult: ScanResult): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        return scanResult.wifiSsid?.toString() ?: ""
    }

    @Suppress("DEPRECATION")
    return scanResult.SSID
}
