package com.daasuu.llmsample.ui.screens.benchmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daasuu.llmsample.data.benchmark.BenchmarkRepository
import com.daasuu.llmsample.data.benchmark.BenchmarkResult
import com.daasuu.llmsample.data.benchmark.BenchmarkSession
import com.daasuu.llmsample.data.benchmark.BenchmarkStats
import com.daasuu.llmsample.data.benchmark.BenchmarkStatus
import com.daasuu.llmsample.data.benchmark.BenchmarkTestCases
import com.daasuu.llmsample.data.model.LLMProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BenchmarkViewModel @Inject constructor(
    private val benchmarkRepository: BenchmarkRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(BenchmarkUiState())
    val uiState: StateFlow<BenchmarkUiState> = _uiState.asStateFlow()
    
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
     * 基本ベンチマークを開始
     */
    fun startBasicBenchmark() {
        if (_uiState.value.isLoading) return
        
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                
                val session = BenchmarkTestCases.createBasicBenchmarkSession()
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
     * 包括的ベンチマークを開始
     */
    fun startComprehensiveBenchmark() {
        if (_uiState.value.isLoading) return
        
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                
                val session = BenchmarkTestCases.createComprehensiveBenchmarkSession()
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
     * パフォーマンステストを開始
     */
    fun startPerformanceBenchmark() {
        if (_uiState.value.isLoading) return
        
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                
                val session = BenchmarkTestCases.createPerformanceBenchmarkSession()
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
     * カスタムベンチマークを開始
     */
    fun startCustomBenchmark(
        selectedProviders: List<LLMProvider>,
        selectedTestCases: List<String>
    ) {
        if (_uiState.value.isLoading) return
        
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                
                val allTestCases = BenchmarkTestCases.getAllTestCases()
                val filteredTestCases = allTestCases.filter { it.id in selectedTestCases }
                
                val session = BenchmarkSession(
                    name = "カスタムベンチマーク",
                    description = "ユーザー選択によるカスタムベンチマーク",
                    testCases = filteredTestCases,
                    providers = selectedProviders
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
     * カスタムベンチマークを開始 (簡略版)
     */
    fun startCustomBenchmark(selectedProviders: List<LLMProvider>) {
        val allTestCases = BenchmarkTestCases.getAllTestCases()
        startCustomBenchmark(selectedProviders, allTestCases.map { it.id })
    }
    
    /**
     * ベンチマークを停止
     */
    fun stopBenchmark() {
        android.util.Log.d("BenchmarkViewModel", "stopBenchmark() called")
        viewModelScope.launch {
            try {
                android.util.Log.d("BenchmarkViewModel", "Calling benchmarkRepository.stopBenchmarkSession()")
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
    
    /**
     * 結果をクリア
     */
    fun clearResults() {
        benchmarkRepository.clearResults()
        _allResults.value = emptyList()
        _uiState.value = _uiState.value.copy(
            currentSession = null,
            results = emptyList(),
            stats = null,
            errorMessage = null
        )
    }
    
    /**
     * プロバイダー別結果の取得
     */
    fun getResultsByProvider(provider: LLMProvider): List<BenchmarkResult> {
        return _uiState.value.results.filter { it.provider == provider }
    }
    
    /**
     * エラーメッセージをクリア
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    /**
     * 詳細表示設定の切り替え
     */
    fun toggleShowDetails() {
        _uiState.value = _uiState.value.copy(
            showDetails = !_uiState.value.showDetails
        )
    }
    
    /**
     * チャート表示タイプの設定
     */
    fun setChartType(chartType: ChartType) {
        _uiState.value = _uiState.value.copy(selectedChartType = chartType)
    }
    
    /**
     * 利用可能なテストケースを取得
     */
    fun getAvailableTestCases() = BenchmarkTestCases.getAllTestCases()
    
    /**
     * 利用可能なプロバイダーを取得
     */
    fun getAvailableProviders() = LLMProvider.values().toList()
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