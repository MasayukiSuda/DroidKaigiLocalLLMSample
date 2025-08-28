package com.daasuu.llmsample.data.performance

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
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
class PerformanceReportExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
    private val readableDateFormat = SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.getDefault())

    suspend fun exportToCsv(records: List<PerformanceRecord>): File = withContext(Dispatchers.IO) {
        val timestamp = dateFormat.format(Date())
        val fileName = "performance_report_$timestamp.csv"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)

        FileWriter(file).use { writer ->
            // Header
            writer.appendLine("ID,実行時刻,プロバイダー,モデル名,タスクタイプ,入力文字数,出力文字数,総レイテンシ(ms),初回トークンレイテンシ(ms),トークン/秒,総トークン数,プロンプトトークン数,メモリ使用量(MB),バッテリー消費(%),デバイス情報,実行成功,エラーメッセージ")

            // Data rows
            records.forEach { record ->
                val row = listOf(
                    record.id,
                    readableDateFormat.format(Date(record.timestamp)),
                    record.provider.displayName,
                    record.modelName ?: "N/A",
                    record.taskType.displayName,
                    record.inputText.length.toString(),
                    record.outputText.length.toString(),
                    record.latencyMs.toString(),
                    record.firstTokenLatencyMs.toString(),
                    String.format("%.2f", record.tokensPerSecond),
                    record.totalTokens.toString(),
                    record.promptTokens.toString(),
                    record.memoryUsageMB.toString(),
                    String.format("%.3f", record.batteryDrain),
                    record.deviceInfo,
                    if (record.isSuccess) "成功" else "失敗",
                    record.errorMessage ?: ""
                ).joinToString(",") { "\"$it\"" }

                writer.appendLine(row)
            }
        }

        file
    }

    suspend fun exportToJson(records: List<PerformanceRecord>): File = withContext(Dispatchers.IO) {
        val timestamp = dateFormat.format(Date())
        val fileName = "performance_report_$timestamp.json"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)

        val jsonReport = JSONObject().apply {
            put("exportTimestamp", System.currentTimeMillis())
            put("exportDate", readableDateFormat.format(Date()))
            put("totalRecords", records.size)
            put("summary", createSummaryJson(records))
            put("records", JSONArray().apply {
                records.forEach { record ->
                    put(recordToJson(record))
                }
            })
        }

        FileWriter(file).use { writer ->
            writer.write(jsonReport.toString(2))
        }

        file
    }

    suspend fun exportToHtml(records: List<PerformanceRecord>): File = withContext(Dispatchers.IO) {
        val timestamp = dateFormat.format(Date())
        val fileName = "performance_report_$timestamp.html"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)

        val htmlContent = createHtmlReport(records)

        FileWriter(file).use { writer ->
            writer.write(htmlContent)
        }

        file
    }

    /**
     * 単一レコードのJSONエクスポート
     */
    suspend fun exportSingleRecordToJson(record: PerformanceRecord): File = withContext(Dispatchers.IO) {
        val timestamp = dateFormat.format(Date(record.timestamp))
        val sanitizedProvider = record.provider.displayName.replace(" ", "_").replace("(", "").replace(")", "")
        val fileName = "performance_record_${sanitizedProvider}_$timestamp.json"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)

        val jsonRecord = JSONObject().apply {
            put("exportTimestamp", System.currentTimeMillis())
            put("exportDate", readableDateFormat.format(Date()))
            put("record", recordToJson(record))
        }

        FileWriter(file).use { writer ->
            writer.write(jsonRecord.toString(2))
        }

        file
    }

    fun shareReport(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = when (file.extension) {
                "csv" -> "text/csv"
                "json" -> "application/json"
                "html" -> "text/html"
                else -> "text/plain"
            }
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "パフォーマンス測定レポート")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(Intent.createChooser(intent, "レポートを共有").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun createSummaryJson(records: List<PerformanceRecord>): JSONObject {
        val successfulRecords = records.filter { it.isSuccess }
        
        return JSONObject().apply {
            put("totalRecords", records.size)
            put("successfulRecords", successfulRecords.size)
            put("failedRecords", records.size - successfulRecords.size)
            put("successRate", if (records.isNotEmpty()) (successfulRecords.size.toDouble() / records.size * 100) else 0.0)
            
            if (successfulRecords.isNotEmpty()) {
                put("averageLatency", successfulRecords.map { it.latencyMs }.average())
                put("averageTokensPerSecond", successfulRecords.map { it.tokensPerSecond }.average())
                put("averageMemoryUsage", successfulRecords.map { it.memoryUsageMB }.average())
                put("averageBatteryDrain", successfulRecords.map { it.batteryDrain }.average())
                
                // プロバイダー別統計
                val providerStats = JSONObject()
                successfulRecords.groupBy { it.provider }.forEach { (provider, records) ->
                    providerStats.put(provider.displayName, JSONObject().apply {
                        put("count", records.size)
                        put("averageLatency", records.map { it.latencyMs }.average())
                        put("averageTokensPerSecond", records.map { it.tokensPerSecond }.average())
                    })
                }
                put("providerStats", providerStats)
                
                // タスクタイプ別統計
                val taskStats = JSONObject()
                successfulRecords.groupBy { it.taskType }.forEach { (taskType, records) ->
                    taskStats.put(taskType.displayName, JSONObject().apply {
                        put("count", records.size)
                        put("averageLatency", records.map { it.latencyMs }.average())
                        put("averageTokensPerSecond", records.map { it.tokensPerSecond }.average())
                    })
                }
                put("taskStats", taskStats)
            }
        }
    }

    private fun recordToJson(record: PerformanceRecord): JSONObject {
        return JSONObject().apply {
            put("id", record.id)
            put("timestamp", record.timestamp)
            put("provider", record.provider.displayName)
            put("modelName", record.modelName)
            put("taskType", record.taskType.displayName)
            put("inputText", record.inputText)
            put("outputText", record.outputText)
            put("latencyMs", record.latencyMs)
            put("firstTokenLatencyMs", record.firstTokenLatencyMs)
            put("tokensPerSecond", record.tokensPerSecond)
            put("totalTokens", record.totalTokens)
            put("promptTokens", record.promptTokens)
            put("memoryUsageMB", record.memoryUsageMB)
            put("batteryDrain", record.batteryDrain)
            put("deviceInfo", record.deviceInfo)
            put("isSuccess", record.isSuccess)
            put("errorMessage", record.errorMessage)
        }
    }

    private fun createHtmlReport(records: List<PerformanceRecord>): String {
        val timestamp = readableDateFormat.format(Date())
        val successfulRecords = records.filter { it.isSuccess }
        
        return """
<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>パフォーマンス測定レポート</title>
    <style>
        body { font-family: 'Segoe UI', Arial, sans-serif; margin: 20px; background: #f5f5f5; }
        .container { max-width: 1200px; margin: 0 auto; background: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
        h1 { color: #2c3e50; text-align: center; margin-bottom: 30px; }
        h2 { color: #34495e; border-bottom: 2px solid #3498db; padding-bottom: 10px; }
        .summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; margin: 30px 0; }
        .stat-card { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 20px; border-radius: 8px; text-align: center; }
        .stat-value { font-size: 2em; font-weight: bold; margin-bottom: 5px; }
        .stat-label { font-size: 0.9em; opacity: 0.9; }
        table { width: 100%; border-collapse: collapse; margin: 20px 0; font-size: 14px; }
        th, td { padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }
        th { background: #f8f9fa; font-weight: bold; }
        tr:hover { background: #f8f9fa; }
        .provider-llama { border-left: 4px solid #e74c3c; }
        .provider-litert { border-left: 4px solid #f39c12; }
        .provider-gemini { border-left: 4px solid #27ae60; }
        .success { color: #27ae60; font-weight: bold; }
        .failure { color: #e74c3c; font-weight: bold; }
        .task-chat { background: linear-gradient(90deg, #667eea, #764ba2); color: white; padding: 4px 8px; border-radius: 4px; font-size: 0.8em; }
        .task-summarization { background: linear-gradient(90deg, #f093fb, #f5576c); color: white; padding: 4px 8px; border-radius: 4px; font-size: 0.8em; }
        .task-proofreading { background: linear-gradient(90deg, #4facfe, #00f2fe); color: white; padding: 4px 8px; border-radius: 4px; font-size: 0.8em; }
        .footer { text-align: center; margin-top: 40px; color: #7f8c8d; font-size: 0.9em; }
    </style>
</head>
<body>
    <div class="container">
        <h1>🚀 パフォーマンス測定レポート</h1>
        <p style="text-align: center; color: #7f8c8d;">生成日時: $timestamp</p>

        <div class="summary">
            <div class="stat-card">
                <div class="stat-value">${records.size}</div>
                <div class="stat-label">総実行回数</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${successfulRecords.size}</div>
                <div class="stat-label">成功回数</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${if (records.isNotEmpty()) String.format("%.1f", (successfulRecords.size.toDouble() / records.size * 100)) else "0"}%</div>
                <div class="stat-label">成功率</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${if (successfulRecords.isNotEmpty()) String.format("%.0f", successfulRecords.map { it.latencyMs }.average()) else "0"}ms</div>
                <div class="stat-label">平均レイテンシ</div>
            </div>
        </div>

        <h2>📋 実行履歴</h2>
        <table>
            <thead>
                <tr>
                    <th>実行時刻</th>
                    <th>プロバイダー</th>
                    <th>タスクタイプ</th>
                    <th>レイテンシ (ms)</th>
                    <th>トークン/秒</th>
                    <th>メモリ (MB)</th>
                    <th>バッテリー (%)</th>
                    <th>ステータス</th>
                </tr>
            </thead>
            <tbody>
                ${records.joinToString("") { record ->
                    val providerClass = when (record.provider.name) {
                        "LLAMA_CPP" -> "provider-llama"
                        "LITE_RT" -> "provider-litert"
                        "GEMINI_NANO" -> "provider-gemini"
                        else -> ""
                    }
                    val taskClass = when (record.taskType.name) {
                        "CHAT" -> "task-chat"
                        "SUMMARIZATION" -> "task-summarization"
                        "PROOFREADING" -> "task-proofreading"
                        else -> ""
                    }
                    val statusClass = if (record.isSuccess) "success" else "failure"
                    val statusText = if (record.isSuccess) "✅ 成功" else "❌ 失敗"

                    """
                    <tr class="$providerClass">
                        <td>${readableDateFormat.format(Date(record.timestamp))}</td>
                        <td><strong>${record.provider.displayName}</strong></td>
                        <td><span class="$taskClass">${record.taskType.displayName}</span></td>
                        <td>${record.latencyMs}</td>
                        <td>${String.format("%.2f", record.tokensPerSecond)}</td>
                        <td>${record.memoryUsageMB}</td>
                        <td>${String.format("%.3f", record.batteryDrain)}</td>
                        <td class="$statusClass">$statusText</td>
                    </tr>
                    """
                }}
            </tbody>
        </table>

        <div class="footer">
            <p>🤖 DroidKaigi Local LLM Sample - パフォーマンス測定システム</p>
        </div>
    </div>
</body>
</html>
        """.trimIndent()
    }
}
