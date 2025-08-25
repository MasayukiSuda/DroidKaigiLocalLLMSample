package com.daasuu.llmsample.data.benchmark

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import com.daasuu.llmsample.data.model.LLMProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BenchmarkReportExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
    private val readableDateFormat =
        SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.getDefault())

    suspend fun exportToCsv(results: List<BenchmarkResult>): File = withContext(Dispatchers.IO) {
        val timestamp = dateFormat.format(Date())
        val fileName = "benchmark_report_$timestamp.csv"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)

        FileWriter(file).use { writer ->
            // Header
            writer.appendLine("ID,テストケース,プロバイダー,モデル名,実行時刻,総レイテンシ(ms),初回トークンレイテンシ(ms),平均トークンレイテンシ(ms),最大メモリ使用量(MB),平均メモリ使用量(MB),バッテリー消費量(%),品質スコア,生成トークン数,生成文字数,実行成功,エラーメッセージ")

            // Data rows
            results.forEach { result ->
                val row = listOf(
                    result.id,
                    result.testCaseId,
                    result.provider.displayName,
                    result.modelName ?: "N/A",
                    readableDateFormat.format(Date(result.timestamp)),
                    result.latencyMetrics.totalLatency.toInt().toString(),
                    result.latencyMetrics.firstTokenLatency.toInt().toString(),
                    result.latencyMetrics.averageTokenLatency.toString(),
                    (result.memoryMetrics.peakMemoryUsageMB).toInt().toString(),
                    (result.memoryMetrics.averageMemoryUsageMB).toInt().toString(),
                    String.format("%.2f", result.batteryMetrics.batteryDrain),
                    result.qualityMetrics.relevanceScore?.toString() ?: "N/A",
                    result.latencyMetrics.totalTokens.toString(),
                    result.generatedText.length.toString(),
                    if (result.isSuccess) "成功" else "失敗",
                    result.errorMessage ?: ""
                ).joinToString(",") { "\"$it\"" }

                writer.appendLine(row)
            }
        }

        file
    }

    suspend fun exportToJson(results: List<BenchmarkResult>): File = withContext(Dispatchers.IO) {
        val timestamp = dateFormat.format(Date())
        val fileName = "benchmark_report_$timestamp.json"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)

        val jsonReport = JSONObject().apply {
            put("exportTimestamp", System.currentTimeMillis())
            put("exportDate", readableDateFormat.format(Date()))
            put("totalResults", results.size)
            put("summary", createSummaryJson(results))
            put("results", JSONArray().apply {
                results.forEach { result ->
                    put(resultToJson(result))
                }
            })
        }

        FileWriter(file).use { writer ->
            writer.write(jsonReport.toString(2))
        }

        file
    }

    suspend fun exportToHtml(results: List<BenchmarkResult>): File = withContext(Dispatchers.IO) {
        val timestamp = dateFormat.format(Date())
        val fileName = "benchmark_report_$timestamp.html"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)

        val htmlContent = createHtmlReport(results)

        FileWriter(file).use { writer ->
            writer.write(htmlContent)
        }

        file
    }

    fun shareReport(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = when (file.extension.lowercase()) {
                "csv" -> "text/csv"
                "json" -> "application/json"
                "html" -> "text/html"
                else -> "text/plain"
            }
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "ベンチマークレポート - ${file.nameWithoutExtension}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooserIntent = Intent.createChooser(shareIntent, "ベンチマークレポートを共有")
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooserIntent)
    }

    private fun createSummaryJson(results: List<BenchmarkResult>): JSONObject {
        val groupedResults = results.groupBy { it.provider }

        return JSONObject().apply {
            put("totalTests", results.size)
            put("successfulTests", results.count { it.isSuccess })
            put("failedTests", results.count { !it.isSuccess })
            put("providers", JSONObject().apply {
                groupedResults.forEach { (provider, providerResults) ->
                    put(provider.name, JSONObject().apply {
                        put("displayName", provider.displayName)
                        put("testCount", providerResults.size)
                        put(
                            "successRate",
                            (providerResults.count { it.isSuccess }
                                .toFloat() / providerResults.size * 100).toInt()
                        )
                        put(
                            "avgLatency",
                            providerResults.map { it.latencyMetrics.totalLatency.toDouble() }
                                .average().toInt()
                        )
                        put(
                            "avgMemory",
                            providerResults.map { it.memoryMetrics.peakMemoryUsageMB.toDouble() }
                                .average().toInt()
                        )
                        put(
                            "avgBattery",
                            String.format(
                                "%.2f",
                                providerResults.map { it.batteryMetrics.batteryDrain.toDouble() }
                                    .average()
                            )
                        )
                        put(
                            "avgQuality",
                            String.format(
                                "%.2f",
                                providerResults.mapNotNull { it.qualityMetrics.relevanceScore?.toDouble() }
                                    .takeIf { it.isNotEmpty() }?.average() ?: 0.0
                            )
                        )
                    })
                }
            })
        }
    }

    private fun resultToJson(result: BenchmarkResult): JSONObject {
        return JSONObject().apply {
            put("id", result.id)
            put("testCaseId", result.testCaseId)
            put("provider", result.provider.name)
            put("providerDisplayName", result.provider.displayName)
            put("modelName", result.modelName)
            put("timestamp", result.timestamp)
            put("date", readableDateFormat.format(Date(result.timestamp)))
            put("isSuccess", result.isSuccess)
            put("errorMessage", result.errorMessage)
            put("generatedText", result.generatedText)

            put("latencyMetrics", JSONObject().apply {
                put("totalLatency", result.latencyMetrics.totalLatency)
                put("firstTokenLatency", result.latencyMetrics.firstTokenLatency)
                put("averageTokenLatency", result.latencyMetrics.averageTokenLatency)
                put("totalTokens", result.latencyMetrics.totalTokens)
                put("tokensPerSecond", result.latencyMetrics.tokensPerSecond)
            })

            put("memoryMetrics", JSONObject().apply {
                put("modelSizeMB", result.memoryMetrics.modelSizeMB)
                put("peakMemoryUsageMB", result.memoryMetrics.peakMemoryUsageMB)
                put("averageMemoryUsageMB", result.memoryMetrics.averageMemoryUsageMB)
                put("memoryIncreaseMB", result.memoryMetrics.memoryIncreaseMB)
                put("availableMemoryMB", result.memoryMetrics.availableMemoryMB)
                put("totalMemoryMB", result.memoryMetrics.totalMemoryMB)
            })

            put("batteryMetrics", JSONObject().apply {
                put("batteryLevelBefore", result.batteryMetrics.batteryLevelBefore)
                put("batteryLevelAfter", result.batteryMetrics.batteryLevelAfter)
                put("batteryDrain", result.batteryMetrics.batteryDrain)
                put(
                    "estimatedBatteryDrainPerHour",
                    result.batteryMetrics.estimatedBatteryDrainPerHour
                )
                put("powerConsumptionMW", result.batteryMetrics.powerConsumptionMW)
                put("isCharging", result.batteryMetrics.isCharging)
                put("batteryTemperature", result.batteryMetrics.batteryTemperature)
            })

            put("qualityMetrics", JSONObject().apply {
                put("outputLength", result.qualityMetrics.outputLength)
                put("outputTokens", result.qualityMetrics.outputTokens)
                put("relevanceScore", result.qualityMetrics.relevanceScore)
                put("coherenceScore", result.qualityMetrics.coherenceScore)
                put("fluencyScore", result.qualityMetrics.fluencyScore)
                put("completeness", result.qualityMetrics.completeness)
                put("taskAccomplished", result.qualityMetrics.taskAccomplished)
            })
        }
    }

    private fun createHtmlReport(results: List<BenchmarkResult>): String {
        val timestamp = readableDateFormat.format(Date())
        val groupedResults = results.groupBy { it.provider }

        return """
<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>ベンチマークレポート - $timestamp</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }
        .container { max-width: 1200px; margin: 0 auto; background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
        h1 { color: #333; text-align: center; margin-bottom: 30px; }
        h2 { color: #444; border-bottom: 2px solid #007bff; padding-bottom: 10px; }
        .summary { display: flex; justify-content: space-around; margin: 20px 0; }
        .summary-item { text-align: center; padding: 15px; background: #f8f9fa; border-radius: 8px; }
        .summary-item h3 { margin: 0; color: #007bff; }
        .summary-item p { margin: 5px 0 0 0; font-size: 24px; font-weight: bold; }
        table { width: 100%; border-collapse: collapse; margin: 20px 0; }
        th, td { padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }
        th { background-color: #007bff; color: white; }
        tr:hover { background-color: #f5f5f5; }
        .provider-llama { background-color: #e3f2fd !important; }
        .provider-litert { background-color: #e8f5e8 !important; }
        .provider-gemini { background-color: #fff3e0 !important; }
        .success { color: #28a745; font-weight: bold; }
        .failure { color: #dc3545; font-weight: bold; }
        .chart-container { margin: 20px 0; }
        .bar-chart { display: flex; align-items: center; margin: 10px 0; }
        .bar-label { width: 120px; font-weight: bold; }
        .bar { height: 30px; margin: 0 10px; border-radius: 4px; display: flex; align-items: center; padding: 0 10px; color: white; }
        .bar-llama { background-color: #2196F3; }
        .bar-litert { background-color: #4CAF50; }
        .bar-gemini { background-color: #FF9800; }
    </style>
</head>
<body>
    <div class="container">
        <h1>🚀 ベンチマークレポート</h1>
        <p style="text-align: center; color: #666;">生成日時: $timestamp</p>
        
        <div class="summary">
            <div class="summary-item">
                <h3>総テスト数</h3>
                <p>${results.size}</p>
            </div>
            <div class="summary-item">
                <h3>成功率</h3>
                <p>${(results.count { it.isSuccess }.toFloat() / results.size * 100).toInt()}%</p>
            </div>
            <div class="summary-item">
                <h3>プロバイダー数</h3>
                <p>${groupedResults.size}</p>
            </div>
        </div>
        
        <h2>📊 プロバイダー比較</h2>
        <div class="chart-container">
            <h3>平均レイテンシ (ms)</h3>
            ${createLatencyChart(groupedResults)}
            
            <h3>平均メモリ使用量 (MB)</h3>
            ${createMemoryChart(groupedResults)}
            
            <h3>平均バッテリー消費量 (%)</h3>
            ${createBatteryChart(groupedResults)}
        </div>
        
        <h2>📋 詳細結果</h2>
        <table>
            <thead>
                <tr>
                    <th>プロバイダー</th>
                    <th>テストケース</th>
                    <th>実行時刻</th>
                    <th>レイテンシ (ms)</th>
                    <th>メモリ (MB)</th>
                    <th>バッテリー (%)</th>
                    <th>品質スコア</th>
                    <th>ステータス</th>
                </tr>
            </thead>
            <tbody>
                ${
            results.joinToString("") { result ->
                val providerClass = when (result.provider) {
                    LLMProvider.LLAMA_CPP -> "provider-llama"
                    LLMProvider.LITE_RT -> "provider-litert"
                    LLMProvider.GEMINI_NANO -> "provider-gemini"
                }
                val statusClass = if (result.isSuccess) "success" else "failure"
                val statusText = if (result.isSuccess) "✅ 成功" else "❌ 失敗"

                """
                    <tr class="$providerClass">
                        <td><strong>${result.provider.displayName}</strong></td>
                        <td>${result.testCaseId}</td>
                        <td>${readableDateFormat.format(Date(result.timestamp))}</td>
                        <td>${result.latencyMetrics.totalLatency.toInt()}</td>
                        <td>${result.memoryMetrics.peakMemoryUsageMB.toInt()}</td>
                        <td>${String.format("%.2f", result.batteryMetrics.batteryDrain)}</td>
                        <td>${
                    result.qualityMetrics.relevanceScore?.let {
                        String.format(
                            "%.2f",
                            it
                        )
                    } ?: "N/A"
                }</td>
                        <td class="$statusClass">$statusText</td>
                    </tr>
                    """
            }
        }
            </tbody>
        </table>
        
        <div style="margin-top: 40px; padding: 20px; background-color: #f8f9fa; border-radius: 8px;">
            <h3>📝 レポート情報</h3>
            <p><strong>アプリケーション:</strong> DroidKaigi Local LLM Sample</p>
            <p><strong>生成日時:</strong> $timestamp</p>
            <p><strong>総データ数:</strong> ${results.size} 件</p>
            <p><strong>対象プロバイダー:</strong> ${groupedResults.keys.joinToString(", ") { it.displayName }}</p>
        </div>
    </div>
</body>
</html>
        """.trimIndent()
    }

    private fun createLatencyChart(groupedResults: Map<LLMProvider, List<BenchmarkResult>>): String {
        val maxLatency =
            groupedResults.values.flatten().maxOfOrNull { it.latencyMetrics.totalLatency.toFloat() }
                ?: 1000f

        return groupedResults.map { (provider, results) ->
            val avgLatency = results.map { it.latencyMetrics.totalLatency.toDouble() }.average()
            val width = (avgLatency / maxLatency * 300).toInt()
            val barClass = when (provider) {
                LLMProvider.LLAMA_CPP -> "bar-llama"
                LLMProvider.LITE_RT -> "bar-litert"
                LLMProvider.GEMINI_NANO -> "bar-gemini"
            }

            """
            <div class="bar-chart">
                <div class="bar-label">${provider.displayName}</div>
                <div class="bar $barClass" style="width: ${width}px;">${avgLatency.toInt()}ms</div>
            </div>
            """
        }.joinToString("")
    }

    private fun createMemoryChart(groupedResults: Map<LLMProvider, List<BenchmarkResult>>): String {
        val maxMemory = groupedResults.values.flatten()
            .maxOfOrNull { it.memoryMetrics.peakMemoryUsageMB.toFloat() } ?: 1024f

        return groupedResults.map { (provider, results) ->
            val avgMemory = results.map { it.memoryMetrics.peakMemoryUsageMB.toDouble() }.average()
            val width = (avgMemory / maxMemory * 300).toInt()
            val barClass = when (provider) {
                LLMProvider.LLAMA_CPP -> "bar-llama"
                LLMProvider.LITE_RT -> "bar-litert"
                LLMProvider.GEMINI_NANO -> "bar-gemini"
            }

            """
            <div class="bar-chart">
                <div class="bar-label">${provider.displayName}</div>
                <div class="bar $barClass" style="width: ${width}px;">${avgMemory.toInt()}MB</div>
            </div>
            """
        }.joinToString("")
    }

    private fun createBatteryChart(groupedResults: Map<LLMProvider, List<BenchmarkResult>>): String {
        val maxBattery =
            groupedResults.values.flatten().maxOfOrNull { it.batteryMetrics.batteryDrain } ?: 10f

        return groupedResults.map { (provider, results) ->
            val avgBattery = results.map { it.batteryMetrics.batteryDrain.toDouble() }.average()
            val width = (avgBattery / maxBattery * 300).toInt()
            val barClass = when (provider) {
                LLMProvider.LLAMA_CPP -> "bar-llama"
                LLMProvider.LITE_RT -> "bar-litert"
                LLMProvider.GEMINI_NANO -> "bar-gemini"
            }

            """
            <div class="bar-chart">
                <div class="bar-label">${provider.displayName}</div>
                <div class="bar $barClass" style="width: ${width}px;">${
                String.format(
                    "%.2f",
                    avgBattery
                )
            }%</div>
            </div>
            """
        }.joinToString("")
    }
}