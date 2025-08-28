package com.daasuu.llmsample.di

import com.daasuu.llmsample.data.performance.PerformanceReportExporter
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PerformanceEntryPoint {
    fun performanceReportExporter(): PerformanceReportExporter
}
