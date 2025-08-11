package com.daasuu.llmsample.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.daasuu.llmsample.ui.navigation.AppNavigation
import com.daasuu.llmsample.ui.navigation.BottomNavItem
import com.daasuu.llmsample.ui.screens.settings.SettingsScreen
import com.daasuu.llmsample.ui.screens.benchmark.BenchmarkDashboardScreen
import com.daasuu.llmsample.ui.screens.model_download.ModelDownloadScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val items = listOf(
        BottomNavItem.Chat,
        BottomNavItem.Summarize,
        BottomNavItem.Proofread
    )
    
    // We'll inject the BenchmarkReportExporter in the BenchmarkDashboardScreen instead
    
    var showSettings by remember { mutableStateOf(false) }
    var showBenchmark by remember { mutableStateOf(false) }
    var showModelDownload by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DroidKaigi LLM Sample") },
                actions = {
                    IconButton(onClick = { showModelDownload = true }) {
                        Icon(Icons.Default.Download, contentDescription = "モデル管理")
                    }
                    IconButton(onClick = { showBenchmark = true }) {
                        Icon(Icons.Default.Speed, contentDescription = "ベンチマーク")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "設定")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AppNavigation(navController = navController)
        }
    }
    
    // Settings modal
    if (showSettings) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSettings = false },
            title = {
                Text("設定")
            },
            text = {
                SettingsScreen()
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text("閉じる")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
    
    // Benchmark dashboard modal
    if (showBenchmark) {
        Box(modifier = Modifier.fillMaxSize()) {
            BenchmarkDashboardScreen(
                onBack = { showBenchmark = false }
            )
        }
    }
    
    // Model download modal
    if (showModelDownload) {
        Box(modifier = Modifier.fillMaxSize()) {
            ModelDownloadScreen(
                onBackClick = { showModelDownload = false }
            )
        }
    }
}