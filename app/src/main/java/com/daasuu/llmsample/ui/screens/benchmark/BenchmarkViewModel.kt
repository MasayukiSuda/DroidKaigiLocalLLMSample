package com.daasuu.llmsample.ui.screens.benchmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daasuu.llmsample.data.benchmark.BenchmarkRepository
import com.daasuu.llmsample.data.benchmark.BenchmarkResult
import com.daasuu.llmsample.data.benchmark.BenchmarkSession
import com.daasuu.llmsample.data.benchmark.BenchmarkStats
import com.daasuu.llmsample.data.benchmark.BenchmarkTestCases
import com.daasuu.llmsample.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BenchmarkViewModel @Inject constructor(
    private val benchmarkRepository: BenchmarkRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BenchmarkUiState())

    val currentSession = benchmarkRepository.currentSession
    val progress = benchmarkRepository.progress
    val results = benchmarkRepository.results

    // 全結果の管理（履歴機能は削除）
    private val _allResults = MutableStateFlow<List<BenchmarkResult>>(emptyList())
    val allResults: StateFlow<List<BenchmarkResult>> = _allResults.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    init {
        // セッション、進行状況、結果の変更を監視してUIStateを更新
        viewModelScope.launch {
            combine(
                currentSession,
                progress,
                results
            ) { session, progressData, resultsList ->
                // Update isRunning state
                _isRunning.value = progressData.isRunning

                // Update allResults with current results
                if (resultsList.isNotEmpty()) {
                    val currentAllResults = _allResults.value.toMutableList()
                    resultsList.forEach { newResult ->
                        val existingIndex = currentAllResults.indexOfFirst { it.id == newResult.id }
                        if (existingIndex >= 0) {
                            currentAllResults[existingIndex] = newResult
                        } else {
                            currentAllResults.add(newResult)
                        }
                    }
                    _allResults.value = currentAllResults
                }

                // セッション履歴機能は削除済み

                _uiState.value.copy(
                    currentSession = session,
                    progress = progressData,
                    results = resultsList,
                    stats = if (resultsList.isNotEmpty()) {
                        benchmarkRepository.calculateBenchmarkStats(resultsList)
                    } else null,
                    isLoading = progressData.isRunning
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    /**
     * 現在のプロバイダーのみで基本ベンチマークを開始
     */
    fun startCurrentProviderBenchmark() {
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

                val currentProvider = settingsRepository.currentProvider.first()
                val session =
                    BenchmarkTestCases.createCurrentProviderBenchmarkSession(currentProvider)
                benchmarkRepository.startBenchmarkSession(session)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "ベンチマーク開始に失敗しました: ${e.message}"
                )
            }
        }
    }

    /**
     * 現在のプロバイダーのみで包括的ベンチマークを開始
     */
    fun startCurrentProviderComprehensiveBenchmark() {
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

                val currentProvider = settingsRepository.currentProvider.first()
                val session = BenchmarkTestCases.createCurrentProviderComprehensiveBenchmarkSession(
                    currentProvider
                )
                benchmarkRepository.startBenchmarkSession(session)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "ベンチマーク開始に失敗しました: ${e.message}"
                )
            }
        }
    }


    /**
     * ベンチマークを停止
     */
    fun stopBenchmark() {
        android.util.Log.d("BenchmarkViewModel", "stopBenchmark() called")
        viewModelScope.launch {
            try {
                android.util.Log.d(
                    "BenchmarkViewModel",
                    "Calling benchmarkRepository.stopBenchmarkSession()"
                )
                benchmarkRepository.stopBenchmarkSession()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = null
                )
                android.util.Log.d("BenchmarkViewModel", "Benchmark stopped successfully")
            } catch (e: Exception) {
                android.util.Log.e("BenchmarkViewModel", "Failed to stop benchmark", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "ベンチマーク停止に失敗しました: ${e.message}"
                )
            }
        }
    }
}

/**
 * ベンチマーク画面のUIState
 */
data class BenchmarkUiState(
    val currentSession: BenchmarkSession? = null,
    val progress: com.daasuu.llmsample.data.benchmark.BenchmarkProgress = com.daasuu.llmsample.data.benchmark.BenchmarkProgress(),
    val results: List<BenchmarkResult> = emptyList(),
    val stats: BenchmarkStats? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showDetails: Boolean = false,
    val selectedChartType: ChartType = ChartType.LATENCY
)

/**
 * チャート表示タイプ
 */
enum class ChartType(val displayName: String) {
    LATENCY("レイテンシ"),
    MEMORY("メモリ使用量"),
    BATTERY("バッテリー消費"),
    TOKENS_PER_SECOND("トークン/秒"),
    COMPARISON("比較")
}