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
                "json" -> "application/json"
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
                put("averageMaxMemorySpike", successfulRecords.map { it.maxMemorySpikeMB }.average())
                put("averageAverageMemoryUsage", successfulRecords.map { it.averageMemoryUsageMB }.average())
                put("averageBatteryDrain", successfulRecords.map { it.batteryDrain }.average())
                
                // プロバイダー別統計
                val providerStats = JSONObject()
                successfulRecords.groupBy { it.provider }.forEach { (provider, records) ->
                    providerStats.put(provider.displayName, JSONObject().apply {
                        put("count", records.size)
                        put("averageLatency", records.map { it.latencyMs }.average())
                        put("averageTokensPerSecond", records.map { it.tokensPerSecond }.average())
                        put("averageMaxMemorySpike", records.map { it.maxMemorySpikeMB }.average())
                        put("averageAverageMemoryUsage", records.map { it.averageMemoryUsageMB }.average())
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
                        put("averageMaxMemorySpike", records.map { it.maxMemorySpikeMB }.average())
                        put("averageAverageMemoryUsage", records.map { it.averageMemoryUsageMB }.average())
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
            put("maxMemorySpikeMB", record.maxMemorySpikeMB)
            put("averageMemoryUsageMB", record.averageMemoryUsageMB)
            put("batteryDrain", record.batteryDrain)
            put("deviceInfo", record.deviceInfo)
            put("isSuccess", record.isSuccess)
            put("errorMessage", record.errorMessage)
        }
    }

}
