package com.eastonseidel.joyncon

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.eastonseidel.joyncon.service.ConnectionState
import com.eastonseidel.joyncon.service.MergeForegroundService
import com.eastonseidel.joyncon.shizuku.ShizukuHelper
import com.eastonseidel.joyncon.ui.RemapScreen
import com.eastonseidel.joyncon.ui.SetupScreen
import com.eastonseidel.joyncon.ui.StatusScreen
import com.eastonseidel.joyncon.ui.theme.JoynConTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            JoynConTheme {
                JoynConApp(
                    onStartService = {
                        startForegroundService(Intent(this, MergeForegroundService::class.java))
                    },
                    onStopService = {
                        stopService(Intent(this, MergeForegroundService::class.java))
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Picks up Shizuku having been installed or started while the user was away.
        ShizukuHelper.refreshState()
        ConnectionState.rescan()
    }
}

private sealed class Dest(val route: String, val label: String) {
    object Status : Dest("status", "Status")
    object Remap : Dest("remap", "Remap")
    object Setup : Dest("setup", "Setup")
}

private val destinations = listOf(Dest.Status, Dest.Remap, Dest.Setup)

@Composable
private fun JoynConApp(onStartService: () -> Unit, onStopService: () -> Unit) {
    val navController: NavHostController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                destinations.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                when (dest) {
                                    Dest.Status -> Icons.Filled.Info
                                    Dest.Remap -> Icons.Filled.Build
                                    Dest.Setup -> Icons.Filled.Settings
                                },
                                contentDescription = dest.label,
                            )
                        },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.Status.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Dest.Status.route) {
                StatusScreen(onStartService = onStartService, onStopService = onStopService)
            }
            composable(Dest.Remap.route) { RemapScreen() }
            composable(Dest.Setup.route) { SetupScreen() }
        }
    }
}
