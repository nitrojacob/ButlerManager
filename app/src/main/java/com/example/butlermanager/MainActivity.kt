package com.example.butlermanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.butlermanager.ui.AdvancedConfigScreen
import com.example.butlermanager.ui.ConnectProgressScreen
import com.example.butlermanager.ui.LogViewerScreen
import com.example.butlermanager.ui.NearbyDevicesScreen
import com.example.butlermanager.ui.NetworkModeHomeScreen
import com.example.butlermanager.ui.QrScannerScreen
import com.example.butlermanager.ui.SavedConfigsScreen
import com.example.butlermanager.ui.TimeEntryScreenOfConfig
import com.example.butlermanager.ui.TimeEntryScreenOfDevice
import com.example.butlermanager.ui.theme.ButlerManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ButlerManagerTheme {
                AppNavigation()
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current.applicationContext
    var currentDeviceManager: DeviceManager? by remember { mutableStateOf(null) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(navBackStackEntry) {
        val route = navBackStackEntry?.destination?.route
        // Clear manager when returning to root navigation screens
        if (route == "qrScanner" || route == "nearbyDevices" || route == "savedConfigs") {
            currentDeviceManager?.disconnect()
            currentDeviceManager = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                title = { Text(stringResource(R.string.page_title)) }
            )
        }
    ) { paddingValues ->
        NavHost(
            navController = navController, startDestination = "nearbyDevices",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("qrScanner") {
                QrScannerScreen(navController)
            }
            composable("nearbyDevices") {
                NearbyDevicesScreen(navController)
            }
            composable("networkModeHome") {
                LaunchedEffect(Unit) {
                    if (currentDeviceManager !is MqttManager) {
                        currentDeviceManager?.disconnect()
                        currentDeviceManager = MqttManager(context)
                    }
                }
                (currentDeviceManager as? MqttManager)?.let { mqttManager ->
                    NetworkModeHomeScreen(navController, mqttManager)
                }
            }
            composable(
                route = "timeEntryDevice/{name}",
                arguments = listOf(
                    navArgument("name") { defaultValue = "" },
                )
            ) { backStackEntry ->
                currentDeviceManager?.let { manager ->
                    TimeEntryScreenOfDevice(
                        navController = navController,
                        name = backStackEntry.arguments?.getString("name") ?: "",
                        deviceManager = manager,
                    )
                }
            }
            composable(
                route = "timeEntryConfig/{name}",
                arguments = listOf(
                    navArgument("name") { defaultValue = "" },
                )
            ) { backStackEntry ->
                TimeEntryScreenOfConfig(
                    navController = navController,
                    name = backStackEntry.arguments?.getString("name") ?: "",
                )
            }
            composable(
                route = "advanced_config/{name}",
                arguments = listOf(navArgument("name") { defaultValue = "" })
            ) { backStackEntry ->
                (currentDeviceManager as? EspressifManager)?.let { manager ->
                    AdvancedConfigScreen(
                        navController = navController,
                        name = backStackEntry.arguments?.getString("name") ?: "",
                        espressifManager = manager
                    )
                }
            }
            composable("log_viewer") {
                currentDeviceManager?.let { manager ->
                    LogViewerScreen(navController = navController, deviceManager = manager)
                }
            }
            composable("savedConfigs") {
                SavedConfigsScreen(navController)
            }
            composable(
                route = "connectProgress/{qrDataJson}",
                arguments = listOf(
                    navArgument("qrDataJson") { defaultValue = "" }
                )
            ) { backStackEntry ->
                LaunchedEffect(Unit) {
                    if (currentDeviceManager !is EspressifManager) {
                        currentDeviceManager?.disconnect()
                        currentDeviceManager = EspressifManager(context)
                    }
                }
                (currentDeviceManager as? EspressifManager)?.let { espressifManager ->
                    ConnectProgressScreen(
                        navController = navController,
                        qrDataJson = backStackEntry.arguments?.getString("qrDataJson") ?: "",
                        deviceManager = espressifManager
                    )
                }
            }
        }
    }

}
