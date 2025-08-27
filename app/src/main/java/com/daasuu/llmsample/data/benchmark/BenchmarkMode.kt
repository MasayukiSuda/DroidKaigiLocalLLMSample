package com.daasuu.llmsample.data.benchmark

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ベンチマークモードの状態管理
 * Session Proposalで約束した「同一プロンプト・同一端末でのベンチマーク」を実現するため、
 * 統一プロンプトと最適化プロンプトの切り替えを管理
 */
object BenchmarkMode {

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    /**
     * ベンチマークモードの有効/無効を切り替え
     * @param enabled true: 統一プロンプトを使用, false: 各プロバイダー最適化プロンプトを使用
     */
    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
    }

    /**
     * 現在のベンチマークモード状態を取得
     * @return true: 統一プロンプトモード, false: 最適化プロンプトモード
     */
    fun isCurrentlyEnabled(): Boolean = _isEnabled.value
}
