package com.daasuu.llmsample.di

import com.daasuu.llmsample.data.benchmark.BenchmarkReportExporter
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BenchmarkEntryPoint {
    fun benchmarkReportExporter(): BenchmarkReportExporter
}