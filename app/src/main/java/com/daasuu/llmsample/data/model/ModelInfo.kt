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
