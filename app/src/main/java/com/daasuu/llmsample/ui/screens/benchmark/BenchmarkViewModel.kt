package com.daasuu.llmsample.ui.screens.benchmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daasuu.llmsample.data.performance.PerformanceFilter
import com.daasuu.llmsample.data.performance.PerformanceLogger
import com.daasuu.llmsample.data.performance.PerformanceRecord
import com.daasuu.llmsample.data.performance.PerformanceStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class BenchmarkViewModel @Inject constructor(
    private val performanceLogger: PerformanceLogger,
) : ViewModel() {

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _filter = MutableStateFlow(PerformanceFilter())
    val filter: StateFlow<PerformanceFilter> = _filter.asStateFlow()

    // パフォーマンス記録のリアルタイム表示
    val performanceRecords: StateFlow<List<PerformanceRecord>> = combine(
        performanceLogger.records,
        _filter
    ) { records, filter ->
        performanceLogger.getFilteredRecords(filter)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // 統計情報
    val performanceStats: StateFlow<PerformanceStats?> = _filter.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PerformanceFilter()
    ).let { filterFlow ->
        combine(performanceLogger.records, filterFlow) { _, filter ->
            performanceLogger.getStats(filter)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    }

    /**
     * フィルター適用
     */
    fun applyFilter(filter: PerformanceFilter) {
        _filter.value = filter
    }

    /**
     * フィルタークリア
     */
    fun clearFilter() {
        _filter.value = PerformanceFilter()
    }

    /**
     * 記録をクリア
     */
    fun clearRecords() {
        performanceLogger.clearRecords()
    }

}
