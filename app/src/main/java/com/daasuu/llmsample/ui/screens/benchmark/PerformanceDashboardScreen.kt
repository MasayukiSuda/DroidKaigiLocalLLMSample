package com.daasuu.llmsample.ui.screens.benchmark

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.daasuu.llmsample.data.performance.PerformanceRecord
import com.daasuu.llmsample.data.performance.PerformanceFilter
import com.daasuu.llmsample.data.performance.PerformanceStats
import com.daasuu.llmsample.data.performance.PerformanceReportExporter
import com.daasuu.llmsample.data.model.LLMProvider
import com.daasuu.llmsample.data.model.TaskType
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.EntryPointAccessors
import com.daasuu.llmsample.di.PerformanceEntryPoint
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceDashboardScreen(
    onBack: () -> Unit,
    viewModel: BenchmarkViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val reportExporter = remember {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            PerformanceEntryPoint::class.java
        )
        entryPoint.performanceReportExporter()
    }

    val performanceRecords by viewModel.performanceRecords.collectAsState()
    val performanceStats by viewModel.performanceStats.collectAsState()
    val filter by viewModel.filter.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Modern Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "戻る",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Column {
                        Text(
                            text = "📊 パフォーマンス記録",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${performanceRecords.size}件の実行記録",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                Row {
                    // Filter Button
                    IconButton(
                        onClick = { showFilterDialog = true }
                    ) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = "フィルター",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    
                    // Export Button
                    if (performanceRecords.isNotEmpty()) {
                        IconButton(
                            onClick = { showExportDialog = true },
                            enabled = !isExporting
                        ) {
                            Icon(
                                if (isExporting) Icons.Default.Schedule else Icons.Default.FileDownload,
                                contentDescription = "エクスポート",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    
                    // Clear Button
                    if (performanceRecords.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.clearRecords() }
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "クリア",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }

        // Modern Tab Bar
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("📈 概要") },
                icon = { Icon(Icons.Default.Analytics, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("📋 詳細") },
                icon = { Icon(Icons.Default.List, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("📊 統計") },
                icon = { Icon(Icons.Default.BarChart, contentDescription = null) }
            )
        }

        // Tab Content
        when (selectedTab) {
            0 -> PerformanceOverviewTab(
                records = performanceRecords,
                stats = performanceStats,
                filter = filter,
                onFilterChange = viewModel::applyFilter,
                modifier = Modifier.fillMaxSize()
            )
            1 -> DetailedRecordsTab(
                records = performanceRecords,
                modifier = Modifier.fillMaxSize()
            )
            2 -> StatisticsTab(
                records = performanceRecords,
                stats = performanceStats,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // Export Dialog
    if (showExportDialog) {
        ModernExportDialog(
            onDismiss = { showExportDialog = false },
            onExportCsv = {
                scope.launch {
                    isExporting = true
                    try {
                        val file = reportExporter.exportToCsv(performanceRecords)
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
                        val file = reportExporter.exportToJson(performanceRecords)
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
                        val file = reportExporter.exportToHtml(performanceRecords)
                        reportExporter.shareReport(file)
                    } finally {
                        isExporting = false
                        showExportDialog = false
                    }
                }
            }
        )
    }

    // Filter Dialog
    if (showFilterDialog) {
        FilterDialog(
            currentFilter = filter,
            onDismiss = { showFilterDialog = false },
            onApplyFilter = { newFilter ->
                viewModel.applyFilter(newFilter)
                showFilterDialog = false
            },
            onClearFilter = {
                viewModel.clearFilter()
                showFilterDialog = false
            }
        )
    }
}

@Composable
fun PerformanceOverviewTab(
    records: List<PerformanceRecord>,
    stats: PerformanceStats?,
    filter: PerformanceFilter,
    onFilterChange: (PerformanceFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quick Stats Cards
        item {
            Text(
                text = "📊 概要統計",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            QuickStatsCards(records = records, stats = stats)
        }

        // Recent Records
        if (records.isNotEmpty()) {
            item {
                Text(
                    text = "🕒 最近の実行記録",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            items(records.take(5)) { record ->
                RecentRecordCard(record = record)
            }
        } else {
            item {
                EmptyStateCard()
            }
        }
    }
}

@Composable
fun DetailedRecordsTab(
    records: List<PerformanceRecord>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "📋 詳細記録一覧",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (records.isEmpty()) {
            item {
                EmptyStateCard()
            }
        } else {
            items(records) { record ->
                DetailedRecordCard(record = record)
            }
        }
    }
}

@Composable
fun StatisticsTab(
    records: List<PerformanceRecord>,
    stats: PerformanceStats?,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "📊 詳細統計",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        if (stats != null && records.isNotEmpty()) {
            item {
                StatisticsCards(stats = stats)
            }
        } else {
            item {
                EmptyStateCard()
            }
        }
    }
}

@Composable
fun QuickStatsCards(
    records: List<PerformanceRecord>,
    stats: PerformanceStats?
) {
    val successfulRecords = records.filter { it.isSuccess }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatsCard(
            title = "総実行回数",
            value = records.size.toString(),
            icon = Icons.Default.PlayArrow,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        
        StatsCard(
            title = "成功率",
            value = if (records.isNotEmpty()) "${(successfulRecords.size * 100 / records.size)}%" else "0%",
            icon = Icons.Default.CheckCircle,
            color = Color(0xFF4CAF50),
            modifier = Modifier.weight(1f)
        )
    }
    
    Spacer(modifier = Modifier.height(8.dp))
    
    if (stats != null && successfulRecords.isNotEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatsCard(
                title = "平均レイテンシ",
                value = "${stats.averageLatencyMs}ms",
                icon = Icons.Default.Speed,
                color = Color(0xFF2196F3),
                modifier = Modifier.weight(1f)
            )
            
            StatsCard(
                title = "平均トークン/秒",
                value = String.format("%.1f", stats.averageTokensPerSecond),
                icon = Icons.Default.Analytics,
                color = Color(0xFF9C27B0),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun StatsCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun RecentRecordCard(record: PerformanceRecord) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = record.provider.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TaskTypeChip(taskType = record.taskType)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dateFormat.format(Date(record.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "${record.latencyMs}ms",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = String.format("%.1f tok/s", record.tokensPerSecond),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun DetailedRecordCard(record: PerformanceRecord) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()) }
    var isExporting by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (record.isSuccess) 
                MaterialTheme.colorScheme.surface 
            else 
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = record.provider.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TaskTypeChip(taskType = record.taskType)
                }
                
                Icon(
                    imageVector = if (record.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (record.isSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = dateFormat.format(Date(record.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "レイテンシ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${record.latencyMs}ms",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Column {
                    Text(
                        text = "トークン/秒",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = String.format("%.2f", record.tokensPerSecond),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Column {
                    Text(
                        text = "メモリ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${record.memoryUsageMB}MB",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // バッテリー使用量
                if (record.batteryDrain > 0f) {
                    Column {
                        Text(
                            text = "バッテリー",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            text = String.format("%.2f%%", record.batteryDrain),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                record.batteryDrain < 0.1f -> MaterialTheme.colorScheme.primary
                                record.batteryDrain < 0.5f -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }
                
                Column {
                    Text(
                        text = "トークン数",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${record.totalTokens}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            if (!record.isSuccess && record.errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "エラー: ${record.errorMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(8.dp)
                )
            }
            
            // エクスポートボタン
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isExporting = true
                            try {
                                val entryPoint = EntryPointAccessors.fromApplication(
                                    context,
                                    PerformanceEntryPoint::class.java
                                )
                                val reportExporter = entryPoint.performanceReportExporter()
                                val file = reportExporter.exportSingleRecordToJson(record)
                                reportExporter.shareReport(file)
                            } finally {
                                isExporting = false
                            }
                        }
                    },
                    enabled = !isExporting,
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = if (isExporting) "エクスポート中..." else "JSONエクスポート",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatisticsCards(stats: PerformanceStats) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Overall Stats
        Text(
            text = "全体統計",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        // LazyColumnをColumnに変更（項目数が少ないため）
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatsCard(
                        title = "総実行数",
                        value = stats.totalRecords.toString(),
                        icon = Icons.Default.PlayArrow,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    StatsCard(
                        title = "成功実行数",
                        value = "${(stats.totalRecords * stats.successRate / 100).toInt()}",
                        icon = Icons.Default.CheckCircle,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.weight(1f)
                    )
                }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatsCard(
                    title = "平均レイテンシ",
                        value = "${stats.averageLatencyMs}ms",
                        icon = Icons.Default.Speed,
                        color = Color(0xFF2196F3),
                        modifier = Modifier.weight(1f)
                    )
                    StatsCard(
                        title = "最大レイテンシ",
                        value = "N/A", // maxLatencyはPerformanceStatsにない
                        icon = Icons.Default.TrendingUp,
                        color = Color(0xFFFF9800),
                        modifier = Modifier.weight(1f)
                    )
                }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatsCard(
                    title = "平均トークン/秒",
                        value = String.format("%.1f", stats.averageTokensPerSecond),
                        icon = Icons.Default.Analytics,
                        color = Color(0xFF9C27B0),
                        modifier = Modifier.weight(1f)
                    )
                    StatsCard(
                        title = "平均メモリ",
                        value = "N/A", // averageMemoryUsageはPerformanceStatsにない
                        icon = Icons.Default.Memory,
                        color = Color(0xFF607D8B),
                        modifier = Modifier.weight(1f)
                    )
                }
        }
    }
}

@Composable
fun TaskTypeChip(taskType: TaskType) {
    val (color, text) = when (taskType) {
        TaskType.CHAT -> Color(0xFF667eea) to "💬"
        TaskType.SUMMARIZATION -> Color(0xFFf093fb) to "📝"
        TaskType.PROOFREADING -> Color(0xFF4facfe) to "✏️"
    }
    
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.2f),
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
    ) {
        Text(
            text = "$text ${taskType.displayName}",
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun EmptyStateCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Analytics,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "実行記録がありません",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "チャット、要約、校正機能を使用すると\n自動的に記録されます",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun ModernExportDialog(
    onDismiss: () -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    onExportHtml: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("エクスポート形式を選択")
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExportOptionButton(
                    title = "CSV形式",
                    description = "Excel等で開ける表形式",
                    icon = Icons.Default.TableChart,
                    onClick = onExportCsv
                )
                ExportOptionButton(
                    title = "JSON形式",
                    description = "プログラムで読み込める形式",
                    icon = Icons.Default.Code,
                    onClick = onExportJson
                )
                ExportOptionButton(
                    title = "HTML形式",
                    description = "ブラウザで見れる美しいレポート",
                    icon = Icons.Default.Web,
                    onClick = onExportHtml
                )
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

@Composable
fun ExportOptionButton(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun FilterDialog(
    currentFilter: PerformanceFilter,
    onDismiss: () -> Unit,
    onApplyFilter: (PerformanceFilter) -> Unit,
    onClearFilter: () -> Unit
) {
    var selectedProvider by remember { mutableStateOf(currentFilter.provider) }
    var selectedTaskType by remember { mutableStateOf(currentFilter.taskType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("フィルター設定")
            }
        },
        text = {
            Column {
                Text(
                    text = "プロバイダー",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LLMProvider.entries.forEach { provider ->
                        FilterChip(
                            selected = selectedProvider == provider,
                            onClick = {
                                selectedProvider = if (selectedProvider == provider) null else provider
                            },
                            label = { Text(provider.displayName) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "タスクタイプ",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TaskType.entries.forEach { taskType ->
                        FilterChip(
                            selected = selectedTaskType == taskType,
                            onClick = {
                                selectedTaskType = if (selectedTaskType == taskType) null else taskType
                            },
                            label = { Text(taskType.displayName) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onClearFilter) {
                    Text("クリア")
                }
                Button(
                    onClick = {
                        onApplyFilter(
                            PerformanceFilter(
                                provider = selectedProvider,
                                taskType = selectedTaskType
                            )
                        )
                    }
                ) {
                    Text("適用")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}
