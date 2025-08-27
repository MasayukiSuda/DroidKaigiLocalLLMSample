package com.daasuu.llmsample.ui.screens.benchmark

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.daasuu.llmsample.data.benchmark.BenchmarkResult
import com.daasuu.llmsample.data.benchmark.BenchmarkSession
import com.daasuu.llmsample.data.model.LLMProvider
import com.daasuu.llmsample.di.BenchmarkEntryPoint
import com.daasuu.llmsample.ui.components.BenchmarkResultsCharts
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkDashboardScreen(
    onBack: () -> Unit,
    viewModel: BenchmarkViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reportExporter = remember {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            BenchmarkEntryPoint::class.java
        )
        entryPoint.benchmarkReportExporter()
    }

    val currentSession by viewModel.currentSession.collectAsState()
    val allResults by viewModel.allResults.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    var showExportDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // ヘッダー部分をシンプルなタイトルに変更（戻るボタンとダウンロードボタンを削除）
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // エクスポートボタンのみ残す（シンプルなテキストボタンに変更）
                if (allResults.isNotEmpty()) {
                    TextButton(
                        onClick = { showExportDialog = true },
                        enabled = !isExporting
                    ) {
                        Icon(
                            imageVector = if (isExporting) Icons.Default.Schedule else Icons.Default.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("エクスポート")
                    }
                }
            }
        }

        // Tab Navigation (履歴タブを削除してシンプル化)
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("ベンチマーク実行") },
                icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("結果分析") },
                icon = { Icon(Icons.Default.Analytics, contentDescription = null) }
            )
        }

        // Tab Content (2タブ制にシンプル化)
        when (selectedTab) {
            0 -> CurrentSessionTab(
                session = currentSession,
                isRunning = isRunning,
                onStartCurrentProvider = viewModel::startCurrentProviderBenchmark,
                onStartCurrentProviderComprehensive = viewModel::startCurrentProviderComprehensiveBenchmark,
                onStop = viewModel::stopBenchmark,
                modifier = Modifier.fillMaxWidth()
            )

            1 -> ResultsAnalysisTab(
                results = allResults,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Export Dialog
    if (showExportDialog) {
        ExportOptionsDialog(
            onDismiss = { showExportDialog = false },
            onExportCsv = {
                scope.launch {
                    isExporting = true
                    try {
                        val file = reportExporter.exportToCsv(allResults)
                        reportExporter.shareReport(file)
                    } finally {
                        isExporting = false
                        showExportDialog = false
                    }
                }
            },
            onExportJson = {
                scope.launch {
                    isExporting = true
                    try {
                        val file = reportExporter.exportToJson(allResults)
                        reportExporter.shareReport(file)
                    } finally {
                        isExporting = false
                        showExportDialog = false
                    }
                }
            },
            onExportHtml = {
                scope.launch {
                    isExporting = true
                    try {
                        val file = reportExporter.exportToHtml(allResults)
                        reportExporter.shareReport(file)
                    } finally {
                        isExporting = false
                        showExportDialog = false
                    }
                }
            }
        )
    }
}

@Composable
fun CurrentSessionTab(
    session: BenchmarkSession?,
    isRunning: Boolean,
    onStartCurrentProvider: () -> Unit,
    onStartCurrentProviderComprehensive: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "ベンチマーク実行",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (isRunning) {
                        // Running state
                        Column {
                            Text(
                                text = "実行中...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            LinearProgressIndicator(
                                progress = session?.progress ?: 0f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            )
                            Text(
                                text = "${((session?.progress ?: 0f) * 100).toInt()}% 完了",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = onStop,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("停止")
                            }
                        }
                    } else {
                        // Start options
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BenchmarkActionButton(
                                text = "基本ベンチマーク",
                                description = "選択中プロバイダーでの基本性能測定",
                                icon = Icons.Default.PlayArrow,
                                onClick = onStartCurrentProvider
                            )

                            BenchmarkActionButton(
                                text = "包括的ベンチマーク",
                                description = "選択中プロバイダーでの詳細テスト",
                                icon = Icons.Default.Assessment,
                                onClick = onStartCurrentProviderComprehensive
                            )
                        }
                    }
                }
            }
        }

        // Current session results
        session?.let { currentSession ->
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "現在のセッション結果",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        SessionSummary(session = currentSession)
                    }
                }
            }

            items(currentSession.results) { result ->
                DashboardBenchmarkResultCard(result = result)
            }
        }
    }
}

@Composable
fun ResultsAnalysisTab(
    results: List<BenchmarkResult>,
    modifier: Modifier = Modifier
) {
    BenchmarkResultsCharts(
        results = results,
        modifier = modifier
    )
}

// SessionHistoryTab を削除（実用性が低いため）

@Composable
fun BenchmarkActionButton(
    text: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun SessionSummary(
    session: BenchmarkSession,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        SummaryItem(
            label = "完了",
            value = "${session.results.size}/${session.testCases.size}"
        )
        SummaryItem(
            label = "成功率",
            value = "${
                (session.results.count { it.isSuccess }
                    .toFloat() / session.results.size * 100).toInt()
            }%"
        )
        SummaryItem(
            label = "進捗",
            value = "${(session.progress * 100).toInt()}%"
        )
    }
}

@Composable
fun SummaryItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DashboardBenchmarkResultCard(
    result: BenchmarkResult,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.provider.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (result.isSuccess) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "成功",
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = "失敗",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "レイテンシ: ${result.latencyMetrics.totalLatency.toInt()}ms",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "メモリ: ${result.memoryMetrics.peakMemoryUsageMB.toInt()}MB",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

// SessionHistoryCard も削除（SessionHistoryTab と一緒に不要）

@Composable
fun ExportOptionsDialog(
    onDismiss: () -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    onExportHtml: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("エクスポート形式を選択") },
        text = {
            Column {
                Text("ベンチマーク結果をエクスポートする形式を選択してください。")
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = onExportCsv,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("CSV")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onExportJson,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("JSON")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onExportHtml,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("HTML")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}