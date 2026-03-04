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
import com.example.butlermanager.ui.NearbyDevicesScreen
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
    var espressifManager: EspressifManager? by remember { mutableStateOf(null) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(navBackStackEntry) {
        if (navBackStackEntry?.destination?.route == "qrScanner") {
            espressifManager?.disconnect()
            espressifManager = null
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
            navController = navController, startDestination = "qrScanner",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("qrScanner") {
                QrScannerScreen(navController)
            }
            composable("nearbyDevices") {
                NearbyDevicesScreen(navController)
            }
            composable(
                route = "timeEntryDevice/{name}",
                arguments = listOf(
                    navArgument("name") { defaultValue = "" },
                )
            ) { backStackEntry ->
                LaunchedEffect(Unit) {
                    if (espressifManager == null) {
                        espressifManager = EspressifManager(context)
                    }
                }
                espressifManager?.let { manager ->
                    TimeEntryScreenOfDevice(
                        navController = navController,
                        name = backStackEntry.arguments?.getString("name") ?: "",
                        espressifManager = manager,
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
                espressifManager?.let { manager ->
                    AdvancedConfigScreen(
                        navController = navController,
                        name = backStackEntry.arguments?.getString("name") ?: "",
                        espressifManager = manager
                    )
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
                    if (espressifManager == null) {
                        espressifManager = EspressifManager(context)
                    }
                }
                espressifManager?.let { manager ->
                    ConnectProgressScreen(
                        navController = navController,
                        qrDataJson = backStackEntry.arguments?.getString("qrDataJson") ?: "",
                        espressifManager = manager
                    )
                }
            }
        }
    }

}
