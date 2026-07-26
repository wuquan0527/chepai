package com.vzlpr.controller

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vzlpr.controller.ui.screens.ConfigScreen
import com.vzlpr.controller.ui.screens.DiscoveryScreen
import com.vzlpr.controller.ui.screens.MonitorScreen
import com.vzlpr.controller.ui.screens.PreviewScreen
import com.vzlpr.controller.ui.screens.WhitelistScreen
import com.vzlpr.controller.ui.theme.VzTheme

class MainActivity : ComponentActivity() {

    private val notiPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notiPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            VzTheme { AppRoot() }
        }
    }
}

private sealed class Dest(val route: String, val label: String, val icon: ImageVector) {
    data object Discovery : Dest("discovery", "搜索", Icons.Filled.Search)
    data object Whitelist : Dest("whitelist", "白名单", Icons.Filled.List)
    data object Monitor : Dest("monitor", "监控", Icons.Filled.DirectionsCar)
    data object Preview : Dest("preview", "预览", Icons.Filled.Videocam)
    data object Config : Dest("config", "配置", Icons.Filled.Settings)
}

private val destinations = listOf(
    Dest.Discovery, Dest.Whitelist, Dest.Monitor, Dest.Preview, Dest.Config
)

@Composable
private fun AppRoot() {
    val nav = rememberNavController()
    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStack by nav.currentBackStackEntryAsState()
                val current = backStack?.destination
                destinations.forEach { dest ->
                    NavigationBarItem(
                        selected = current?.hierarchy?.any { it.route == dest.route } == true,
                        onClick = {
                            nav.navigate(dest.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Dest.Monitor.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Dest.Discovery.route) { DiscoveryScreen() }
            composable(Dest.Whitelist.route) { WhitelistScreen() }
            composable(Dest.Monitor.route) { MonitorScreen() }
            composable(Dest.Preview.route) { PreviewScreen() }
            composable(Dest.Config.route) { ConfigScreen() }
        }
    }
}
