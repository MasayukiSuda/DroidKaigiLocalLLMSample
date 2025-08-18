package com.daasuu.llmsample.data.benchmark

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ユーザー操作によるベンチマークへの干渉を監視
 */
@Singleton
class InterferenceMonitor @Inject constructor() {

    private val _userActionCount = MutableStateFlow(0)
    val userActionCount: StateFlow<Int> = _userActionCount.asStateFlow()

    private val _isUserActive = MutableStateFlow(false)
    val isUserActive: StateFlow<Boolean> = _isUserActive.asStateFlow()

    private var benchmarkStartTime: Long = 0L
    private val userActionHistory = mutableListOf<UserAction>()
    private var resetTimer: Timer? = null

    /**
     * ベンチマーク開始を記録
     */
    fun startBenchmarkMonitoring() {
        benchmarkStartTime = System.currentTimeMillis()
        userActionHistory.clear()
        _userActionCount.value = 0
        _isUserActive.value = false
        resetTimer?.cancel()
        resetTimer = null
    }

    /**
     * ユーザー操作を記録
     */
    fun recordUserAction(actionType: UserActionType, duration: Long = 0) {
        val currentTime = System.currentTimeMillis()
        userActionHistory.add(
            UserAction(
                type = actionType,
                timestamp = currentTime,
                duration = duration,
                relativeToBenchmarkStart = currentTime - benchmarkStartTime
            )
        )

        _userActionCount.value++
        _isUserActive.value = true

        // 既存のタイマーをキャンセルして新しいタイマーを設定
        resetTimer?.cancel()
        resetTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    _isUserActive.value = false
                }
            }, 5000) // 5秒後にリセット
        }
    }

    /**
     * ベンチマーク期間中のユーザー操作統計を取得
     */
    fun getInterferenceStats(): InterferenceStats {
        val totalActions = userActionHistory.size
        val actionsByType = userActionHistory.groupBy { it.type }
        val averageActionDuration = if (userActionHistory.isNotEmpty()) {
            userActionHistory.map { it.duration }.average()
        } else 0.0

        return InterferenceStats(
            totalUserActions = totalActions,
            actionsByType = actionsByType.mapValues { it.value.size },
            averageActionDuration = averageActionDuration,
            hasSignificantInterference = totalActions > 3, // 3回以上の操作で影響ありとみなす
            interferenceLevel = when {
                totalActions == 0 -> InterferenceLevel.NONE
                totalActions <= 2 -> InterferenceLevel.LOW
                totalActions <= 5 -> InterferenceLevel.MEDIUM
                else -> InterferenceLevel.HIGH
            }
        )
    }

    /**
     * 監視データをクリア
     */
    fun clearMonitoring() {
        userActionHistory.clear()
        _userActionCount.value = 0
        _isUserActive.value = false
        benchmarkStartTime = 0L
        resetTimer?.cancel()
        resetTimer = null
    }
}

/**
 * ユーザー操作の種類
 */
enum class UserActionType {
    CHAT_MESSAGE,
    PROOFREADING,
    SUMMARIZATION,
    PROVIDER_SWITCH,
    SETTINGS_CHANGE,
    NAVIGATION
}

/**
 * ユーザー操作記録
 */
data class UserAction(
    val type: UserActionType,
    val timestamp: Long,
    val duration: Long,
    val relativeToBenchmarkStart: Long
)

/**
 * 干渉統計
 */
data class InterferenceStats(
    val totalUserActions: Int,
    val actionsByType: Map<UserActionType, Int>,
    val averageActionDuration: Double,
    val hasSignificantInterference: Boolean,
    val interferenceLevel: InterferenceLevel
)

/**
 * 干渉レベル
 */
enum class InterferenceLevel {
    NONE,    // 干渉なし
    LOW,     // 軽微な干渉
    MEDIUM,  // 中程度の干渉
    HIGH     // 高い干渉
}
