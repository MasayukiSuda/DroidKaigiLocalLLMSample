package com.daasuu.llmsample.ui.screens.benchmark

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.daasuu.llmsample.data.benchmark.BenchmarkResult
import com.daasuu.llmsample.data.benchmark.BenchmarkStats
import com.daasuu.llmsample.data.benchmark.BenchmarkStatus
import com.daasuu.llmsample.data.model.LLMProvider
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkScreen(
    viewModel: BenchmarkViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()
    val progress by viewModel.progress.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "性能ベンチマーク",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ベンチマークコントロール
        BenchmarkControls(
            isRunning = progress.isRunning,
            onStartBasic = viewModel::startBasicBenchmark,
            onStartComprehensive = viewModel::startComprehensiveBenchmark,
            onStartPerformance = viewModel::startPerformanceBenchmark,
            onStop = viewModel::stopBenchmark,
            onClearResults = viewModel::clearResults
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 進行状況表示
        if (progress.isRunning) {
            BenchmarkProgressCard(progress = progress)
            Spacer(modifier = Modifier.height(16.dp))
        } else if (progress.currentTestCase == "停止済み") {
            // 停止状態の表示
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ベンチマークが停止されました",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // エラーメッセージ
        uiState.errorMessage?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // 統計サマリー
        uiState.stats?.let { stats ->
            BenchmarkStatsCard(stats = stats)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 結果リスト
        if (uiState.results.isNotEmpty()) {
            Text(
                text = "ベンチマーク結果",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn {
                items(uiState.results) { result ->
                    BenchmarkResultCard(
                        result = result,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BenchmarkControls(
    isRunning: Boolean,
    onStartBasic: () -> Unit,
    onStartComprehensive: () -> Unit,
    onStartPerformance: () -> Unit,
    onStop: () -> Unit,
    onClearResults: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "ベンチマーク実行",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (isRunning) {
                Button(
                    onClick = {
                        android.util.Log.d("BenchmarkScreen", "Stop button clicked")
                        onStop()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ベンチマークを停止")
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onStartBasic,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("基本ベンチマーク")
                    }

                    Button(
                        onClick = onStartComprehensive,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Assessment, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("包括的ベンチマーク")
                    }

                    Button(
                        onClick = onStartPerformance,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("パフォーマンステスト")
                    }

                    OutlinedButton(
                        onClick = onClearResults,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("結果をクリア")
                    }
                }
            }
        }
    }
}

@Composable
private fun BenchmarkProgressCard(
    progress: com.daasuu.llmsample.data.benchmark.BenchmarkProgress
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "実行中...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "プロバイダー: ${progress.currentProvider}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Text(
                text = "テストケース: ${progress.currentTestCase}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            LinearProgressIndicator(
                progress = { progress.progressPercentage / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "${progress.completedTests} / ${progress.totalTests} テスト完了 (${progress.progressPercentage.roundToInt()}%)",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun BenchmarkStatsCard(
    stats: BenchmarkStats
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "ベンチマーク統計",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "総テスト数",
                    value = "${stats.totalTests}",
                    icon = Icons.Default.Assessment
                )
                StatItem(
                    label = "成功",
                    value = "${stats.completedTests}",
                    icon = Icons.Default.PlayArrow,
                    color = MaterialTheme.colorScheme.primary
                )
                StatItem(
                    label = "失敗",
                    value = "${stats.failedTests}",
                    icon = Icons.Default.Stop,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "平均レイテンシ",
                    value = "${stats.averageLatency.roundToInt()}ms",
                    icon = Icons.Default.Speed
                )
                StatItem(
                    label = "平均メモリ",
                    value = "${stats.averageMemoryUsage}MB",
                    icon = Icons.Default.Memory
                )
            }

            stats.bestPerformingProvider?.let { provider ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "最高性能: ${provider.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun BenchmarkResultCard(
    result: BenchmarkResult,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = result.provider.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = result.modelName ?: "Unknown Model",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (result.isSuccess) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "成功",
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "失敗",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (result.isSuccess) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MetricItem(
                        label = "レイテンシ",
                        value = "${result.latencyMetrics.totalLatency}ms"
                    )
                    MetricItem(
                        label = "トークン/秒",
                        value = String.format("%.1f", result.latencyMetrics.tokensPerSecond)
                    )
                    MetricItem(
                        label = "メモリ",
                        value = "${result.memoryMetrics.peakMemoryUsageMB}MB"
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MetricItem(
                        label = "バッテリー",
                        value = String.format("%.2f%%", result.batteryMetrics.batteryDrain)
                    )
                    MetricItem(
                        label = "出力長",
                        value = "${result.qualityMetrics.outputLength}文字"
                    )
                    MetricItem(
                        label = "トークン数",
                        value = "${result.qualityMetrics.outputTokens}"
                    )
                }
                
                // 干渉情報も表示
                if (result.latencyMetrics.userInterferenceDetected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetricItem(
                            label = "ユーザー干渉",
                            value = "検知済み"
                        )
                        MetricItem(
                            label = "偏差",
                            value = "${String.format("%.1f", result.latencyMetrics.baselineDeviation)}%"
                        )
                        MetricItem(
                            label = "同時操作",
                            value = "${result.latencyMetrics.concurrentUserActions}回"
                        )
                    }
                }
            } else {
                result.errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "エラー: $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}