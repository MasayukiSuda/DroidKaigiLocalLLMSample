package com.daasuu.llmsample.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daasuu.llmsample.data.benchmark.BenchmarkResult
import com.daasuu.llmsample.data.model.LLMProvider

@Composable
fun BenchmarkResultsCharts(
    results: List<BenchmarkResult>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Text(
                text = "ベンチマーク結果",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (results.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LatencyComparisonChart(
                        results = results,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MemoryUsageChart(
                        results = results,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BatteryConsumptionChart(
                        results = results,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ProviderComparisonTable(
                        results = results,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ベンチマーク結果がありません",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LatencyComparisonChart(
    results: List<BenchmarkResult>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "レイテンシ比較 (ms)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        val groupedResults = results.groupBy { it.provider }
        val maxLatency = results.maxOfOrNull { it.latencyMetrics.totalLatency.toFloat() } ?: 1000f

        groupedResults.forEach { (provider, providerResults) ->
            val avgLatency =
                providerResults.map { it.latencyMetrics.totalLatency.toDouble() }.average()
                    .toFloat()
            val avgFirstToken =
                providerResults.map { it.latencyMetrics.firstTokenLatency.toDouble() }.average()
                    .toFloat()
            val interferenceCount =
                providerResults.count { it.latencyMetrics.userInterferenceDetected }

            ProviderLatencyBar(
                provider = provider,
                totalLatency = avgLatency,
                firstTokenLatency = avgFirstToken,
                maxLatency = maxLatency,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
fun ProviderLatencyBar(
    provider: LLMProvider,
    totalLatency: Float,
    firstTokenLatency: Float,
    maxLatency: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = provider.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${totalLatency.toInt()}ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .padding(vertical = 2.dp)
        ) {
            // Background bar
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            // First token latency (darker)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(firstTokenLatency / maxLatency)
                    .clip(RoundedCornerShape(4.dp))
                    .background(getProviderColor(provider).copy(alpha = 0.8f))
            )

            // Total latency (lighter)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(totalLatency / maxLatency)
                    .clip(RoundedCornerShape(4.dp))
                    .background(getProviderColor(provider).copy(alpha = 0.6f))
            )
        }
    }
}

@Composable
fun MemoryUsageChart(
    results: List<BenchmarkResult>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "メモリ使用量比較 (MB)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        val groupedResults = results.groupBy { it.provider }
        val maxMemory =
            results.maxOfOrNull { it.memoryMetrics.peakMemoryUsageMB.toFloat() } ?: 1024f

        groupedResults.forEach { (provider, providerResults) ->
            val avgMemory =
                providerResults.map { it.memoryMetrics.peakMemoryUsageMB.toDouble() }.average()
                    .toFloat()

            ProviderMemoryBar(
                provider = provider,
                memoryUsage = avgMemory,
                maxMemory = maxMemory,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
fun ProviderMemoryBar(
    provider: LLMProvider,
    memoryUsage: Float,
    maxMemory: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = provider.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${memoryUsage.toInt()}MB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .padding(vertical = 2.dp)
        ) {
            // Background bar
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            // Memory usage bar
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(memoryUsage / maxMemory)
                    .clip(RoundedCornerShape(4.dp))
                    .background(getProviderColor(provider))
            )
        }
    }
}

@Composable
fun BatteryConsumptionChart(
    results: List<BenchmarkResult>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "バッテリー消費量比較 (%)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        val groupedResults = results.groupBy { it.provider }
        val maxConsumption = results.maxOfOrNull { it.batteryMetrics.batteryDrain } ?: 10f

        groupedResults.forEach { (provider, providerResults) ->
            val avgConsumption =
                providerResults.map { it.batteryMetrics.batteryDrain.toDouble() }.average()
                    .toFloat()

            ProviderBatteryBar(
                provider = provider,
                batteryConsumption = avgConsumption,
                maxConsumption = maxConsumption,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
fun ProviderBatteryBar(
    provider: LLMProvider,
    batteryConsumption: Float,
    maxConsumption: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = provider.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${String.format("%.2f", batteryConsumption)}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .padding(vertical = 2.dp)
        ) {
            // Background bar
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            // Battery consumption bar
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(batteryConsumption / maxConsumption)
                    .clip(RoundedCornerShape(4.dp))
                    .background(getProviderColor(provider))
            )
        }
    }
}

@Composable
fun ProviderComparisonTable(
    results: List<BenchmarkResult>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "プロバイダー比較表",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        val groupedResults = results.groupBy { it.provider }

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "プロバイダー",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "レイテンシ",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "メモリ",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "バッテリー",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }

        groupedResults.forEach { (provider, providerResults) ->
            val avgLatency =
                providerResults.map { it.latencyMetrics.totalLatency.toDouble() }.average()
            val avgMemory =
                providerResults.map { it.memoryMetrics.peakMemoryUsageMB.toDouble() }.average()
            val avgBattery =
                providerResults.map { it.batteryMetrics.batteryDrain.toDouble() }.average()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = provider.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${avgLatency.toInt()}ms",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${avgMemory.toInt()}MB",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${String.format("%.2f", avgBattery)}%",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }

            Divider(
                color = MaterialTheme.colorScheme.surfaceVariant,
                thickness = 0.5.dp
            )
        }
    }
}

private fun getProviderColor(provider: LLMProvider): Color {
    return when (provider) {
        LLMProvider.LLAMA_CPP -> Color(0xFF2196F3) // Blue
        LLMProvider.LITE_RT -> Color(0xFF4CAF50) // Green
        LLMProvider.GEMINI_NANO -> Color(0xFFFF9800) // Orange
    }
}