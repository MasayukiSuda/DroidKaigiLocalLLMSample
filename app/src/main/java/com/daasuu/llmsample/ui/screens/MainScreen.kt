package com.daasuu.llmsample.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.daasuu.llmsample.data.benchmark.BenchmarkMode
import com.daasuu.llmsample.ui.navigation.AppNavigation
import com.daasuu.llmsample.ui.navigation.BottomNavItem
import com.daasuu.llmsample.ui.screens.benchmark.PerformanceDashboardScreen
import com.daasuu.llmsample.ui.screens.model_download.ModelDownloadScreen
import com.daasuu.llmsample.ui.screens.settings.SettingsScreen

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

    // ベンチマークモードの状態を監視
    val isBenchmarkMode by BenchmarkMode.isEnabled.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DroidKaigi LLM Sample") },
                actions = {
                    // ベンチマークモードトグル
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "ベンチマークモード",
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Switch(
                            checked = isBenchmarkMode,
                            onCheckedChange = { BenchmarkMode.setEnabled(it) }
                        )
                    }

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
        AlertDialog(
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

    // Benchmark modal
    if (showBenchmark) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showBenchmark = false }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .heightIn(max = 600.dp)
                    .padding(16.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // ダイアログヘッダー
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ベンチマーク",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { showBenchmark = false }) {
                            Text("閉じる")
                        }
                    }

                    // パフォーマンス記録内容
                    PerformanceDashboardScreen(
                        onBack = { showBenchmark = false }
                    )
                }
            }
        }
    }

    // Model download modal
    if (showModelDownload) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showModelDownload = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false // フルスクリーンダイアログ
            )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // ダイアログヘッダー
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Llama.cpp モデル管理",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { showModelDownload = false }) {
                            Text("閉じる")
                        }
                    }

                    // モデルダウンロード内容
                    ModelDownloadScreen()
                }
            }
        }
    }
}