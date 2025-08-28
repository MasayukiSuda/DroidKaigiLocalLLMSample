package com.daasuu.llmsample.data.benchmark

import kotlinx.coroutines.flow.MutableStateFlow
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

    private val _isUserActive = MutableStateFlow(false)

    private var benchmarkStartTime: Long = 0L
    private val userActionHistory = mutableListOf<UserAction>()
    private var resetTimer: Timer? = null

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

