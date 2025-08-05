package com.daasuu.llmsample.data.benchmark

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * パフォーマンス監視ユーティリティ
 */
@Singleton
class PerformanceMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    
    private var monitoringStartTime: Long = 0L
    private var baselineMemory: Long = 0L
    private var baselineBatteryLevel: Float = 0f
    private val memorySnapshots = mutableListOf<Long>()
    
    /**
     * 監視を開始
     */
    fun startMonitoring() {
        monitoringStartTime = System.currentTimeMillis()
        baselineMemory = getCurrentMemoryUsage()
        baselineBatteryLevel = getCurrentBatteryLevel()
        memorySnapshots.clear()
    }
    
    /**
     * メモリ使用量の継続監視
     */
    fun monitorMemoryUsage(): Flow<Long> = flow {
        while (true) {
            val memoryUsage = getCurrentMemoryUsage()
            memorySnapshots.add(memoryUsage)
            emit(memoryUsage)
            delay(100) // 100ms間隔でサンプリング
        }
    }
    
    /**
     * 現在のメモリ使用量を取得 (MB)
     */
    fun getCurrentMemoryUsage(): Long {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return (memoryInfo.totalMem - memoryInfo.availMem) / (1024 * 1024)
    }
    
    /**
     * ピークメモリ使用量を取得
     */
    fun getPeakMemoryUsage(): Long {
        return memorySnapshots.maxOrNull() ?: getCurrentMemoryUsage()
    }
    
    /**
     * 平均メモリ使用量を取得
     */
    fun getAverageMemoryUsage(): Long {
        return if (memorySnapshots.isNotEmpty()) {
            memorySnapshots.average().toLong()
        } else {
            getCurrentMemoryUsage()
        }
    }
    
    /**
     * メモリ増加量を取得
     */
    fun getMemoryIncrease(): Long {
        return getCurrentMemoryUsage() - baselineMemory
    }
    
    /**
     * 現在のバッテリーレベルを取得 (%)
     */
    fun getCurrentBatteryLevel(): Float {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return batteryIntent?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level != -1 && scale != -1) {
                (level / scale.toFloat()) * 100f
            } else 0f
        } ?: 0f
    }
    
    /**
     * バッテリー情報を取得
     */
    fun getBatteryInfo(): BatteryMetrics {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val currentLevel = getCurrentBatteryLevel()
        val isCharging = batteryIntent?.let { intent ->
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            plugged == BatteryManager.BATTERY_PLUGGED_AC || 
            plugged == BatteryManager.BATTERY_PLUGGED_USB || 
            plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS
        } ?: false
        
        val temperature = batteryIntent?.let { intent ->
            intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1).let { temp ->
                if (temp != -1) temp / 10f else null
            }
        }
        
        val batteryDrain = baselineBatteryLevel - currentLevel
        val elapsedHours = (System.currentTimeMillis() - monitoringStartTime) / (1000f * 60f * 60f)
        val estimatedDrainPerHour = if (elapsedHours > 0) batteryDrain / elapsedHours else 0f
        
        return BatteryMetrics(
            batteryLevelBefore = baselineBatteryLevel,
            batteryLevelAfter = currentLevel,
            batteryDrain = batteryDrain,
            estimatedBatteryDrainPerHour = estimatedDrainPerHour,
            isCharging = isCharging,
            batteryTemperature = temperature
        )
    }
    
    /**
     * デバイス情報を取得
     */
    fun getDeviceInfo(): DeviceInfo {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val availableStorage = getAvailableStorage()
        val displayMetrics = DisplayMetrics()
        
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            cpuArchitecture = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            totalRamMB = memoryInfo.totalMem / (1024 * 1024),
            availableStorageGB = availableStorage,
            screenDensity = displayMetrics.density,
            thermalState = getThermalState()
        )
    }
    
    /**
     * 利用可能ストレージを取得 (GB)
     */
    private fun getAvailableStorage(): Long {
        val path = context.filesDir
        val stat = StatFs(path.path)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        return availableBytes / (1024 * 1024 * 1024)
    }
    
    /**
     * サーマル状態を取得
     */
    private fun getThermalState(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getThermalStateQ()
        } else {
            null
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getThermalStateQ(): String {
        return when (powerManager.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "NONE"
            PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
            PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
            PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
            PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
            else -> "UNKNOWN"
        }
    }
    
    /**
     * CPUコア数を取得
     */
    fun getCpuCoreCount(): Int {
        return Runtime.getRuntime().availableProcessors()
    }
    
    /**
     * CPU使用率を取得（簡易版）
     */
    fun getCpuUsage(): Float {
        return try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val load = reader.readLine()
            reader.close()
            
            val toks = load.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val idle1 = toks[4].toLong()
            val cpu1 = toks[2].toLong() + toks[3].toLong() + toks[5].toLong() + 
                      toks[6].toLong() + toks[7].toLong() + toks[8].toLong()
            
            Thread.sleep(100)
            
            val reader2 = RandomAccessFile("/proc/stat", "r")
            val load2 = reader2.readLine()
            reader2.close()
            
            val toks2 = load2.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val idle2 = toks2[4].toLong()
            val cpu2 = toks2[2].toLong() + toks2[3].toLong() + toks2[5].toLong() + 
                       toks2[6].toLong() + toks2[7].toLong() + toks2[8].toLong()
            
            ((cpu2 - cpu1).toFloat()) / ((cpu2 + idle2) - (cpu1 + idle1)) * 100f
        } catch (e: Exception) {
            0f
        }
    }
    
    /**
     * プロセスの詳細メモリ情報を取得
     */
    fun getDetailedMemoryInfo(): Map<String, Long> {
        val pid = android.os.Process.myPid()
        val memoryInfo = activityManager.getProcessMemoryInfo(intArrayOf(pid))
        
        return if (memoryInfo.isNotEmpty()) {
            val info = memoryInfo[0]
            mapOf(
                "totalPss" to info.totalPss.toLong(),
                "totalPrivateDirty" to info.totalPrivateDirty.toLong(),
                "totalSharedDirty" to info.totalSharedDirty.toLong(),
                "nativeHeap" to info.nativePss.toLong(),
                "dalvikHeap" to info.dalvikPss.toLong()
            )
        } else {
            emptyMap()
        }
    }
    
    /**
     * ガベージコレクション統計を取得
     */
    fun getGcStats(): Map<String, Any> {
        val runtime = Runtime.getRuntime()
        return mapOf(
            "totalMemory" to runtime.totalMemory(),
            "freeMemory" to runtime.freeMemory(),
            "maxMemory" to runtime.maxMemory(),
            "usedMemory" to (runtime.totalMemory() - runtime.freeMemory())
        )
    }
}