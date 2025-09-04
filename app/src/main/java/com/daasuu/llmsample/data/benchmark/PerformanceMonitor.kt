package com.daasuu.llmsample.data.benchmark

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * パフォーマンス監視ユーティリティ
 */
@Singleton
class PerformanceMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    /**
     * 現在のバッテリーレベルを取得 (%)
     */
    fun getCurrentBatteryLevel(): Float {
        val batteryIntent =
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return batteryIntent?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level != -1 && scale != -1) {
                (level / scale.toFloat()) * 100f
            } else 0f
        } ?: 0f
    }

    /**
     * 現在のメモリ使用量を取得 (MB)
     *
     * LLMアプリでの一般的なメモリ使用量：
     * - 小型モデル (1B-3B): 500MB-2GB
     * - 中型モデル (7B-13B): 4GB-8GB
     * - 大型モデル (30B+): 15GB+
     *
     * Pixel 9での利用可能メモリ：
     * - 総RAM: 16GB (Pro)
     * - AI専用予約: ~2.6GB
     * - システム使用: ~3-4GB
     * - アプリ利用可能: ~10GB
     *
     * PSS (Proportional Set Size) 説明：
     * - プロセス専用メモリ + 他プロセスとの共有メモリの比例配分
     * - モデルファイルを含むため、通常のアプリより大きくなる
     */
    fun getCurrentMemoryUsage(): Int {
        val pid = android.os.Process.myPid()
        val memoryInfo = activityManager.getProcessMemoryInfo(intArrayOf(pid))

        val memoryMB = if (memoryInfo.isNotEmpty()) {
            val info = memoryInfo[0]
            // totalPss（Proportional Set Size）を使用 - プロセス全体のメモリ使用量
            // PSS値はキロバイト単位で返されるので、メガバイト単位に変換
            val pssKB = info.totalPss
            val pssMB = (pssKB / 1024.0).toInt()
            maxOf(pssMB, 1) // 最小値を1MBに設定
        } else {
            // フォールバック：JVMヒープメモリのみ
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            (usedMemory / 1024 / 1024).toInt()
        }

        // テスト用シミュレーション（現実的な範囲に調整）
        // LLMアプリでは5-15MBの変動が妥当
        val simulatedVariation = if (shouldUseSimulation()) {
            (Math.random() * 15).toInt() - 5 // -5MB ~ +10MBの変動
        } else 0

        val finalMemoryMB = maxOf(1, memoryMB + simulatedVariation)

        return finalMemoryMB
    }

    /**
     * シミュレーションを使用するかどうかを判定
     */
    private fun shouldUseSimulation(): Boolean {
        // 実際のメモリー値を確認するため、一時的にシミュレーションを無効化
        return false

        // 必要に応じて以下のコードでシミュレーションを再有効化
        // return android.util.Log.isLoggable("MemoryDebug", android.util.Log.DEBUG)
    }

    /**
     * メモリ使用量を継続的に監視するクラス
     */
    class MemoryMonitoringSession private constructor(
        private val performanceMonitor: PerformanceMonitor
    ) {
        private val memoryReadings = mutableListOf<Int>()
        private var monitoringJob: Job? = null

        companion object {
            /**
             * メモリー監視セッションを開始
             * @param intervalMs 監視間隔（ミリ秒）
             */
            fun start(
                performanceMonitor: PerformanceMonitor,
                intervalMs: Long = 500
            ): MemoryMonitoringSession {
                val session = MemoryMonitoringSession(performanceMonitor)
                session.startMonitoring(intervalMs)
                return session
            }
        }

        private fun startMonitoring(intervalMs: Long) {
            monitoringJob = CoroutineScope(Dispatchers.IO).launch {
                // 初回測定を即座に実行
                val initialMemory = performanceMonitor.getCurrentMemoryUsage()
                memoryReadings.add(initialMemory)
                android.util.Log.d("MemorySession", "初期メモリー: ${initialMemory}MB")

                // より積極的な監視のために、最初の数回は短い間隔で測定
                repeat(5) {
                    delay(50) // 最初の250msは50ms間隔
                    val currentMemory = performanceMonitor.getCurrentMemoryUsage()
                    memoryReadings.add(currentMemory)
                }

                // 通常間隔での継続監視
                while (isActive) {
                    delay(intervalMs)
                    val currentMemory = performanceMonitor.getCurrentMemoryUsage()
                    memoryReadings.add(currentMemory)
                }
            }
        }

        /**
         * 監視を停止し、統計情報を取得
         */
        fun stop(): MemoryStats {
            // 最後のメモリー測定を取得
            val finalMemory = performanceMonitor.getCurrentMemoryUsage()
            memoryReadings.add(finalMemory)

            // ジョブを停止
            monitoringJob?.cancel()

            return if (memoryReadings.isNotEmpty()) {
                // デバッグ情報（本番では削除可能）
                android.util.Log.d(
                    "PerformanceMonitor",
                    "メモリー測定数: ${memoryReadings.size}, 値: $memoryReadings"
                )

                MemoryStats(
                    currentMemoryMB = memoryReadings.lastOrNull() ?: 0,
                    maxMemorySpikeMB = memoryReadings.maxOrNull() ?: 0,
                    averageMemoryUsageMB = memoryReadings.average().toInt(),
                    measurementCount = memoryReadings.size,
                    allReadings = memoryReadings.toList() // デバッグ用
                )
            } else {
                val currentMemory = performanceMonitor.getCurrentMemoryUsage()
                MemoryStats(
                    currentMemoryMB = currentMemory,
                    maxMemorySpikeMB = currentMemory,
                    averageMemoryUsageMB = currentMemory,
                    measurementCount = 1,
                    allReadings = listOf(currentMemory)
                )
            }
        }
    }

    /**
     * メモリー統計情報
     */
    data class MemoryStats(
        val currentMemoryMB: Int,
        val maxMemorySpikeMB: Int,
        val averageMemoryUsageMB: Int,
        val measurementCount: Int = 0, // デバッグ用：測定回数
        val allReadings: List<Int> = emptyList() // デバッグ用：全測定値
    )
}