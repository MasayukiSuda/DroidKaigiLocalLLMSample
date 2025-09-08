package com.daasuu.llmsample.ui.screens.model_selection

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionScreen(
    viewModel: ModelSelectionViewModel = hiltViewModel()
) {
    val availableModels by viewModel.availableModels.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // ライフサイクルを監視してonResumeでrefreshModelsを呼び出す
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshModels()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "ダウンロード済みのモデルから選択してください",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val downloadedModels = availableModels.filter { it.isDownloaded }
            val notDownloadedModels = availableModels.filter { !it.isDownloaded }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 「なし」オプションを追加
                item {
                    ModelSelectionItem(
                        title = "なし（自動選択）",
                        description = "利用可能なモデルから自動的に選択します",
                        isSelected = selectedModel == null,
                        isDownloaded = true,
                        fileSize = null,
                        onClick = { viewModel.selectModel(null) }
                    )
                }

                if (downloadedModels.isNotEmpty()) {
                    item {
                        Text(
                            text = "利用可能なモデル",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(downloadedModels) { model ->
                        ModelSelectionItem(
                            title = model.name,
                            description = model.description,
                            isSelected = selectedModel == model.id,
                            isDownloaded = model.isDownloaded,
                            fileSize = model.fileSize,
                            onClick = { viewModel.selectModel(model.id) }
                        )
                    }
                }

                if (notDownloadedModels.isNotEmpty()) {
                    item {
                        Text(
                            text = "ダウンロードが必要なモデル",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(notDownloadedModels) { model ->
                        ModelSelectionItem(
                            title = model.name,
                            description = model.description,
                            isSelected = false,
                            isDownloaded = model.isDownloaded,
                            fileSize = model.fileSize,
                            onClick = { /* ダウンロードが必要 */ }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelSelectionItem(
    title: String,
    description: String,
    isSelected: Boolean,
    isDownloaded: Boolean,
    fileSize: Long?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isDownloaded) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                isDownloaded -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // アイコン
            Icon(
                imageVector = when {
                    isSelected -> Icons.Default.Check
                    isDownloaded -> Icons.Default.Check
                    else -> Icons.Default.Download
                },
                contentDescription = null,
                tint = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                    isDownloaded -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                modifier = Modifier.padding(end = 12.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                        isDownloaded -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        isDownloaded -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (fileSize != null) {
                    Text(
                        text = formatFileSize(fileSize),
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            isDownloaded -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        },
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                if (!isDownloaded) {
                    Text(
                        text = "ダウンロードが必要です",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@SuppressLint("DefaultLocale")
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
