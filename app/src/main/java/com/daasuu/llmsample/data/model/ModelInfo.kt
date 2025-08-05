package com.daasuu.llmsample.data.model

data class ModelInfo(
    val id: String,
    val name: String,
    val provider: LLMProvider,
    val downloadUrl: String,
    val fileSize: Long,
    val description: String,
    val isDownloaded: Boolean = false,
    val localPath: String? = null
)

enum class DownloadStatus {
    NOT_STARTED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    PAUSED
}

data class DownloadProgress(
    val modelId: String,
    val status: DownloadStatus,
    val progress: Float = 0f, // 0.0 to 1.0
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val error: String? = null
)