package com.daasuu.llmsample.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.daasuu.llmsample.data.model.DownloadStatus
import com.daasuu.llmsample.data.model.LLMProvider
import com.daasuu.llmsample.data.model.ModelInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val models by viewModel.models.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "モデル管理",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Provider selection
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "現在の LLM プロバイダー",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                LLMProvider.values().forEach { provider ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedProvider == provider,
                            onClick = { viewModel.selectProvider(provider) }
                        )
                        Text(
                            text = provider.displayName,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
        
        // Models list
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(models) { model ->
                ModelCard(
                    model = model,
                    downloadProgress = downloadProgress[model.id],
                    onDownload = { viewModel.downloadModel(model.id) },
                    onDelete = { viewModel.deleteModel(model.id) }
                )
            }
        }
    }
}

@Composable
fun ModelCard(
    model: ModelInfo,
    downloadProgress: com.daasuu.llmsample.data.model.DownloadProgress?,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = model.provider.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = "サイズ: ${formatFileSize(model.fileSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                
                when {
                    downloadProgress?.status == DownloadStatus.DOWNLOADING -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                progress = { downloadProgress.progress },
                                modifier = Modifier.size(40.dp),
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = "${(downloadProgress.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    model.isDownloaded -> {
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "削除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    else -> {
                        IconButton(onClick = onDownload) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = "ダウンロード"
                            )
                        }
                    }
                }
            }
            
            if (downloadProgress?.status == DownloadStatus.FAILED) {
                Text(
                    text = "エラー: ${downloadProgress.error}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toFloat()
    var unitIndex = 0
    
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }
    
    return String.format("%.1f %s", size, units[unitIndex])
}